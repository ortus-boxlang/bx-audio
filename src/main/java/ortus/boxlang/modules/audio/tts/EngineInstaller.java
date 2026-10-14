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
package ortus.boxlang.modules.audio.tts;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import java.util.Map;

import com.fasterxml.jackson.jr.ob.JSON;

import ortus.boxlang.modules.audio.tts.Downloader.Progress;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * The text to speech engine: the Voices library, which runs Piper models on ONNX Runtime, and what it needs. Its jars are not in the
 * module because ONNX Runtime alone is 139 MB. They are downloaded once, each checked against the SHA-1 pinned in {@code engine.json}.
 */
public final class EngineInstaller {

	/**
	 * One jar of the engine
	 */
	public record Artifact( String group, String artifact, String version, String url, String sha1, long size ) {

		/**
		 * @return The file name, for example {@code gson-2.13.2.jar}
		 */
		public String fileName() {
			return this.artifact + "-" + this.version + ".jar";
		}
	}

	private final String			engineId;
	private final List<Artifact>	artifacts;
	private final Path				dir;

	/**
	 * @param engineDir The folder with one subfolder per engine version
	 */
	public EngineInstaller( Path engineDir ) {
		this( engineDir, load() );
	}

	@SuppressWarnings( "unchecked" )
	private EngineInstaller( Path engineDir, Map<String, Object> manifest ) {
		this.engineId = ( String ) manifest.get( "engine" );
		List<Artifact> list = new ArrayList<>();
		for ( Object entry : ( List<Object> ) manifest.get( "artifacts" ) ) {
			Map<String, Object> a = ( Map<String, Object> ) entry;
			list.add( new Artifact( ( String ) a.get( "group" ), ( String ) a.get( "artifact" ), ( String ) a.get( "version" ), ( String ) a.get( "url" ),
			    ( String ) a.get( "sha1" ), ( ( Number ) a.get( "size" ) ).longValue() ) );
		}
		this.artifacts	= List.copyOf( list );
		this.dir		= engineDir.resolve( this.engineId );
	}

	private static Map<String, Object> load() {
		try ( InputStream in = EngineInstaller.class.getClassLoader().getResourceAsStream( "engine.json" ) ) {
			if ( in == null ) {
				throw new BoxRuntimeException( "The engine list (engine.json) is missing from the module" );
			}
			return JSON.std.mapFrom( in );
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Could not read the engine list: " + e.getMessage(), e );
		}
	}

	/**
	 * @return The jars the engine consists of
	 */
	public List<Artifact> getArtifacts() {
		return this.artifacts;
	}

	/**
	 * @return The folder the jars are in
	 */
	public Path getDir() {
		return this.dir;
	}

	/**
	 * @return The bytes to download for the whole engine
	 */
	public long downloadSize() {
		return this.artifacts.stream().mapToLong( Artifact::size ).sum();
	}

	/**
	 * The engine is installed when every jar is there at the size the manifest says. Jars are only moved into place after their SHA-1 was
	 * checked, so the size is enough here.
	 */
	public boolean isInstalled() {
		try {
			for ( Artifact a : this.artifacts ) {
				Path file = this.dir.resolve( a.fileName() );
				if ( !Files.isRegularFile( file ) || Files.size( file ) != a.size() ) {
					return false;
				}
			}
			return true;
		} catch ( IOException e ) {
			return false;
		}
	}

	/**
	 * Download the jars that are missing
	 *
	 * @param progress Told about progress of the file being downloaded, with its name, may be null
	 */
	public void install( NamedProgress progress ) {
		for ( Artifact a : this.artifacts ) {
			Progress p = progress == null ? null : ( done, total ) -> progress.update( a.fileName(), done, total );
			Downloader.download( a.url(), this.dir.resolve( a.fileName() ), a.size(), "SHA-1", a.sha1(), p );
		}
	}

	/**
	 * Told how far the download of a named file has got
	 */
	@FunctionalInterface
	public interface NamedProgress {

		void update( String name, long done, long total );
	}

	/**
	 * @return The URLs of the installed jars, for a class loader
	 *
	 * @throws BoxRuntimeException when the engine is not installed
	 */
	public URL[] classpath() {
		if ( !isInstalled() ) {
			throw new BoxRuntimeException( "The text to speech engine is not installed. Run `bx-audio install engine` or audioVoiceInstall( \"engine\" )." );
		}
		try {
			URL[] urls = new URL[ this.artifacts.size() ];
			for ( int i = 0; i < urls.length; i++ ) {
				urls[ i ] = this.dir.resolve( this.artifacts.get( i ).fileName() ).toUri().toURL();
			}
			return urls;
		} catch ( MalformedURLException e ) {
			throw new BoxRuntimeException( "Bad engine path: " + e.getMessage(), e );
		}
	}

	/**
	 * Delete the installed engine
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
			throw new BoxRuntimeException( "Could not remove the engine: " + e.getMessage(), e );
		}
	}

}
