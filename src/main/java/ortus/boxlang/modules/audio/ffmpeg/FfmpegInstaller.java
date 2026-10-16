/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package ortus.boxlang.modules.audio.ffmpeg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.jr.ob.JSON;

import ortus.boxlang.modules.audio.tts.Downloader;
import ortus.boxlang.modules.audio.tts.EngineInstaller.NamedProgress;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Downloads FFmpeg and FFprobe for this computer from a pinned list ({@code ffmpeg.json} in the module): each file has a URL, a size and a
 * SHA-256, and is only used after it matches. The programs are separate executables that the module starts as processes, never linked or
 * shipped, and they are kept in the user's bx-audio home.
 */
public final class FfmpegInstaller {

	/**
	 * One archive to download
	 *
	 * @param url     Where it is
	 * @param size    Bytes
	 * @param sha256  Its digest
	 * @param extract Program name to path inside the archive
	 */
	public record Archive( String url, long size, String sha256, Map<String, String> extract ) {
	}

	private final Path			dir;
	private final String		version;
	private final String		platform;
	private final List<Archive>	archives;

	/**
	 * @param ffmpegDir The folder for FFmpeg inside the bx-audio home
	 */
	public FfmpegInstaller( Path ffmpegDir ) {
		this( ffmpegDir, platformKey() );
	}

	FfmpegInstaller( Path ffmpegDir, String platform ) {
		this( ffmpegDir, platform, manifest() );
	}

	@SuppressWarnings( "unchecked" )
	FfmpegInstaller( Path ffmpegDir, String platform, Map<String, Object> manifest ) {
		this.platform = platform;
		String				defaultVersion	= ( String ) manifest.get( "version" );
		Map<String, Object>	platforms		= ( Map<String, Object> ) manifest.get( "platforms" );
		List<Archive>		list			= new ArrayList<>();
		Map<String, Object>	entry			= ( Map<String, Object> ) platforms.get( platform );
		if ( entry != null ) {
			defaultVersion = ( String ) entry.getOrDefault( "version", defaultVersion );
			for ( Object file : ( List<Object> ) entry.get( "archives" ) ) {
				Map<String, Object> f = ( Map<String, Object> ) file;
				list.add( new Archive( ( String ) f.get( "url" ), ( ( Number ) f.get( "size" ) ).longValue(), ( String ) f.get( "sha256" ),
				    ( Map<String, String> ) f.get( "extract" ) ) );
			}
		}
		this.version	= defaultVersion;
		this.archives	= List.copyOf( list );
		this.dir		= ffmpegDir.resolve( this.version );
	}

	@SuppressWarnings( "unchecked" )
	private static Map<String, Object> manifest() {
		try ( InputStream in = FfmpegInstaller.class.getClassLoader().getResourceAsStream( "ffmpeg.json" ) ) {
			if ( in == null ) {
				throw new BoxRuntimeException( "The FFmpeg list (ffmpeg.json) is missing from the module" );
			}
			return JSON.std.mapFrom( in );
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Could not read the FFmpeg list: " + e.getMessage(), e );
		}
	}

	/**
	 * @return linux-amd64, linux-arm64, macos-amd64, macos-arm64 or windows-amd64, or the raw os and arch for anything else
	 */
	public static String platformKey() {
		String	os		= System.getProperty( "os.name", "" ).toLowerCase( Locale.ROOT );
		String	arch	= System.getProperty( "os.arch", "" ).toLowerCase( Locale.ROOT );
		String	name	= os.contains( "win" ) ? "windows" : os.contains( "mac" ) || os.contains( "darwin" ) ? "macos" : os.contains( "linux" ) ? "linux" : os;
		String	cpu		= arch.equals( "amd64" ) || arch.equals( "x86_64" ) ? "amd64" : arch.equals( "aarch64" ) || arch.equals( "arm64" ) ? "arm64" : arch;
		return name + "-" + cpu;
	}

	public String getPlatform() {
		return this.platform;
	}

	public String getVersion() {
		return this.version;
	}

	public Path getDir() {
		return this.dir;
	}

	/**
	 * @return Whether a download exists for this computer
	 */
	public boolean isSupported() {
		return !this.archives.isEmpty();
	}

	/**
	 * @return The bytes to download
	 */
	public long downloadSize() {
		return this.archives.stream().mapToLong( Archive::size ).sum();
	}

	public Path ffmpegPath() {
		return this.dir.resolve( exe( "ffmpeg" ) );
	}

	public Path ffprobePath() {
		return this.dir.resolve( exe( "ffprobe" ) );
	}

	private String exe( String name ) {
		return this.platform.startsWith( "windows" ) ? name + ".exe" : name;
	}

	public boolean isInstalled() {
		return isSupported() && Files.isRegularFile( ffmpegPath() ) && Files.isRegularFile( ffprobePath() );
	}

	/**
	 * Download and unpack. Nothing is left in place if a download fails its checks.
	 *
	 * @param progress Told how far each download has got, may be null
	 */
	public void install( NamedProgress progress ) {
		if ( !isSupported() ) {
			throw new BoxRuntimeException( "There is no FFmpeg download for this computer (" + this.platform
			    + "). Install FFmpeg yourself so it is on the PATH, or set the ffmpeg module setting or BX_AUDIO_FFMPEG to its path." );
		}
		if ( isInstalled() ) {
			// Already here: a second install would download everything again and replace programs that may be running
			return;
		}
		try {
			Path downloads = this.dir.resolve( "downloads" );
			for ( Archive archive : this.archives ) {
				String	fileName	= archive.url().substring( archive.url().lastIndexOf( '/' ) + 1 );
				// Several archives share a file name (every build calls its program ffmpeg.zip), so keep them apart by digest
				Path	zip			= downloads.resolve( archive.sha256().substring( 0, 12 ) + "-" + fileName );
				Downloader.download( archive.url(), zip, archive.size(), "SHA-256", archive.sha256(), progress == null ? null
				    : ( done, total ) -> progress
				        .update( extractedName( archive ), done, total ) );
				unzip( zip, archive.extract() );
				Files.deleteIfExists( zip );
			}
			Files.deleteIfExists( downloads );
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Could not install FFmpeg: " + e.getMessage(), e );
		}
	}

	private static String extractedName( Archive archive ) {
		return String.join( " and ", archive.extract().keySet() );
	}

	private void unzip( Path zip, Map<String, String> wanted ) throws IOException {
		try ( ZipInputStream in = new ZipInputStream( Files.newInputStream( zip ) ) ) {
			ZipEntry entry;
			while ( ( entry = in.getNextEntry() ) != null ) {
				for ( Map.Entry<String, String> want : wanted.entrySet() ) {
					if ( entry.getName().equals( want.getValue() ) ) {
						Path	target	= this.dir.resolve( want.getKey() );
						Path	part	= this.dir.resolve( want.getKey() + ".part" );
						Files.createDirectories( this.dir );
						Files.copy( in, part, StandardCopyOption.REPLACE_EXISTING );
						part.toFile().setExecutable( true, false );
						Files.move( part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE );
					}
				}
			}
		}
		for ( String name : wanted.keySet() ) {
			if ( !Files.isRegularFile( this.dir.resolve( name ) ) ) {
				throw new BoxRuntimeException( "The FFmpeg download did not contain " + name );
			}
		}
	}

	/**
	 * Delete the installed FFmpeg
	 *
	 * @return True if there was something to delete
	 */
	public boolean remove() {
		if ( !Files.isDirectory( this.dir ) ) {
			return false;
		}
		try ( var walk = Files.walk( this.dir ) ) {
			for ( Path path : walk.sorted( java.util.Comparator.reverseOrder() ).toList() ) {
				Files.delete( path );
			}
			return true;
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Could not remove FFmpeg: " + e.getMessage(), e );
		}
	}

}
