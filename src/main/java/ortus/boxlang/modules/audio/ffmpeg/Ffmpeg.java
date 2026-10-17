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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.jr.ob.JSON;

import ortus.boxlang.modules.audio.codec.WavCodec;
import ortus.boxlang.modules.audio.tts.Speech;
import ortus.boxlang.modules.audio.tts.TtsHome;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.ModuleSettings;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * FFmpeg and FFprobe as separate programs. They are found in this order: the {@code ffmpeg} module setting, the {@code BX_AUDIO_FFMPEG}
 * environment variable, the copy installed in the bx-audio home, then the PATH. The module never links them, it starts them as processes
 * and reads what they print, which is also why a missing one just means the formats it handles are not available.
 */
public final class Ffmpeg {

	/**
	 * Formats the module reads and writes itself, so FFmpeg is not needed for them
	 */
	public static final List<String>	NATIVE_EXTENSIONS	= List.of( "", ".wav", ".wave", ".mp3", ".flac" );

	/**
	 * Formats that are written through FFmpeg
	 */
	public static final List<String>	ENCODE_EXTENSIONS	= List.of( ".ogg", ".oga", ".opus", ".m4a", ".mp4", ".aac" );

	private static final long			TIMEOUT_MINUTES		= 60;
	private static final long			MAX_DECODE_BYTES	= 1_800_000_000L;

	private final Path					ffmpeg;
	private final Path					ffprobe;
	private final String				source;
	private String						version;

	private Ffmpeg( Path ffmpeg, Path ffprobe, String source ) {
		this.ffmpeg		= ffmpeg;
		this.ffprobe	= ffprobe;
		this.source		= source;
	}

	/**
	 * Find FFmpeg on this computer
	 *
	 * @return The programs, or null when none works
	 */
	public static Ffmpeg locate() {
		String configured = ModuleSettings.text( "ffmpeg" );
		if ( configured.isEmpty() ) {
			configured = System.getenv( "BX_AUDIO_FFMPEG" ) == null ? "" : System.getenv( "BX_AUDIO_FFMPEG" ).trim();
		}
		if ( !configured.isEmpty() ) {
			Ffmpeg found = fromPath( Path.of( configured ), "configured" );
			if ( found == null ) {
				throw new AudioException( AudioException.FFMPEG_MISSING,
				    "The configured FFmpeg [" + configured + "] was not found or does not run. Fix the ffmpeg module setting or "
				        + "BX_AUDIO_FFMPEG, or clear it to use the installed or system FFmpeg." );
			}
			return found;
		}
		FfmpegInstaller installer = installer();
		if ( installer.isInstalled() ) {
			Ffmpeg found = fromPrograms( installer.ffmpegPath(), installer.ffprobePath(), "installed" );
			if ( found != null ) {
				return found;
			}
		}
		for ( String entry : System.getenv().getOrDefault( "PATH", "" ).split( java.io.File.pathSeparator ) ) {
			// Windows PATH entries are sometimes quoted, and a folder name that is not a valid path must not stop the search
			String dir = entry.trim();
			if ( dir.length() > 1 && dir.startsWith( "\"" ) && dir.endsWith( "\"" ) ) {
				dir = dir.substring( 1, dir.length() - 1 );
			}
			if ( dir.isBlank() ) {
				continue;
			}
			try {
				Path	folder	= Path.of( dir );
				Ffmpeg	found	= fromPrograms( folder.resolve( exe( "ffmpeg" ) ), folder.resolve( exe( "ffprobe" ) ), "system" );
				if ( found != null ) {
					return found;
				}
			} catch ( java.nio.file.InvalidPathException e ) {
				// not a folder this computer can name, so there is no FFmpeg in it
			}
		}
		return null;
	}

	public static FfmpegInstaller installer() {
		return new FfmpegInstaller( TtsHome.ffmpeg( Speech.home() ) );
	}

	private static String exe( String name ) {
		return System.getProperty( "os.name", "" ).toLowerCase( Locale.ROOT ).contains( "win" ) ? name + ".exe" : name;
	}

	private static Ffmpeg fromPath( Path path, String source ) {
		if ( Files.isDirectory( path ) ) {
			Path bin = Files.isDirectory( path.resolve( "bin" ) ) ? path.resolve( "bin" ) : path;
			return fromPrograms( bin.resolve( exe( "ffmpeg" ) ), bin.resolve( exe( "ffprobe" ) ), source );
		}
		String	name	= path.getFileName().toString();
		Path	probe	= path.resolveSibling( name.replace( "ffmpeg", "ffprobe" ) );
		return fromPrograms( path, probe, source );
	}

	/**
	 * Programs already found to work, by their path, size and date, so FFmpeg is not started again to ask its version on every call
	 */
	private static final java.util.Map<String, Ffmpeg> VERIFIED = new java.util.concurrent.ConcurrentHashMap<>();

	private static Ffmpeg fromPrograms( Path ffmpeg, Path ffprobe, String source ) {
		if ( !Files.isRegularFile( ffmpeg ) || !Files.isExecutable( ffmpeg ) || !Files.isRegularFile( ffprobe ) || !Files.isExecutable( ffprobe ) ) {
			return null;
		}
		String key;
		try {
			key = source + "|" + ffmpeg.toAbsolutePath() + "|" + Files.size( ffmpeg ) + "|" + Files.getLastModifiedTime( ffmpeg ).toMillis() + "|"
			    + ffprobe.toAbsolutePath();
		} catch ( java.io.IOException e ) {
			return null;
		}
		Ffmpeg known = VERIFIED.get( key );
		if ( known != null ) {
			return known;
		}
		Ffmpeg candidate = new Ffmpeg( ffmpeg, ffprobe, source );
		try {
			candidate.version();
			VERIFIED.put( key, candidate );
			return candidate;
		} catch ( BoxRuntimeException e ) {
			return null;
		}
	}

	public Path getFfmpeg() {
		return this.ffmpeg;
	}

	public Path getFfprobe() {
		return this.ffprobe;
	}

	/**
	 * @return configured, installed or system
	 */
	public String getSource() {
		return this.source;
	}

	/**
	 * @return The version, for example 9.0.2
	 */
	public synchronized String version() {
		if ( this.version == null ) {
			String					first	= run( List.of( this.ffmpeg.toString(), "-hide_banner", "-version" ) ).text().lines().findFirst().orElse( "" );
			java.util.regex.Matcher	m		= java.util.regex.Pattern.compile( "version\\s+(\\S+)" ).matcher( first );
			if ( !m.find() ) {
				throw new AudioException( AudioException.FFMPEG_FAILED, "[" + this.ffmpeg + "] does not look like FFmpeg" );
			}
			this.version = m.group( 1 );
		}
		return this.version;
	}

	/**
	 * Which of the encoders the module uses this FFmpeg has
	 *
	 * @return Names such as libvorbis, libopus, aac and libmp3lame that are present
	 */
	public List<String> encoders() {
		String			all		= run( List.of( this.ffmpeg.toString(), "-hide_banner", "-encoders" ) ).text();
		List<String>	found	= new ArrayList<>();
		for ( String name : List.of( "libvorbis", "libopus", "aac", "libmp3lame", "flac" ) ) {
			if ( all.contains( " " + name + " " ) ) {
				found.add( name );
			}
		}
		return found;
	}

	/**
	 * What a process printed and how it ended
	 */
	record Result( int exit, byte[] out, String err ) {

		String text() {
			return new String( this.out, StandardCharsets.UTF_8 ) + this.err;
		}
	}

	/**
	 * Threads that read what FFmpeg prints. Their own pool, so a busy common pool cannot leave FFmpeg blocked on a full pipe.
	 */
	private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newCachedThreadPool( task -> {
		Thread thread = new Thread( task, "bxaudio-ffmpeg-io" );
		thread.setDaemon( true );
		return thread;
	} );

	static Result run( List<String> command ) {
		Process process = null;
		try {
			process = new ProcessBuilder( command ).redirectInput( ProcessBuilder.Redirect.from( nullFile() ) ).start();
			final Process				started	= process;
			CompletableFuture<byte[]>	out		= CompletableFuture.supplyAsync( () -> drain( started.getInputStream() ), IO );
			CompletableFuture<byte[]>	err		= CompletableFuture.supplyAsync( () -> drain( started.getErrorStream() ), IO );
			if ( !process.waitFor( TIMEOUT_MINUTES, TimeUnit.MINUTES ) ) {
				process.destroyForcibly();
				throw new AudioException( AudioException.FFMPEG_FAILED, "FFmpeg took more than " + TIMEOUT_MINUTES + " minutes and was stopped" );
			}
			return new Result( process.exitValue(), out.get(), new String( err.get(), StandardCharsets.UTF_8 ) );
		} catch ( IOException e ) {
			throw new AudioException( AudioException.FFMPEG_FAILED, "Could not start " + command.get( 0 ) + ": " + e.getMessage(), e );
		} catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new AudioException( AudioException.FFMPEG_FAILED, "FFmpeg was interrupted", e );
		} catch ( java.util.concurrent.ExecutionException e ) {
			throw new AudioException( AudioException.IO, "Could not read the output of FFmpeg: " + e.getMessage(), e );
		} finally {
			// An interrupted or failed wait must not leave FFmpeg running with its temp files open
			if ( process != null && process.isAlive() ) {
				process.destroyForcibly();
			}
		}
	}

	private static java.io.File nullFile() {
		return new java.io.File( System.getProperty( "os.name", "" ).toLowerCase( Locale.ROOT ).contains( "win" ) ? "NUL" : "/dev/null" );
	}

	private static byte[] drain( InputStream in ) {
		try ( in ) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			in.transferTo( out );
			return out.toByteArray();
		} catch ( IOException e ) {
			return new byte[ 0 ];
		}
	}

	private Result ffmpeg( List<String> args ) {
		List<String> command = new ArrayList<>( List.of( this.ffmpeg.toString(), "-hide_banner", "-nostdin", "-v", "error" ) );
		command.addAll( args );
		Result result = run( command );
		if ( result.exit() != 0 ) {
			throw new AudioException( AudioException.FFMPEG_FAILED, "FFmpeg failed: " + result.err().trim() );
		}
		return result;
	}

	/**
	 * What FFprobe says about a file
	 *
	 * @return The parsed JSON with format and streams
	 */
	@SuppressWarnings( "unchecked" )
	public Map<String, Object> probe( Path file ) {
		if ( !Files.isRegularFile( file ) ) {
			throw new AudioException( AudioException.FILE_NOT_FOUND, "The file [" + file + "] does not exist" );
		}
		Result result = run( List.of( this.ffprobe.toString(), "-v", "error", "-print_format", "json", "-show_format", "-show_streams", file.toString() ) );
		if ( result.exit() != 0 ) {
			throw new AudioException( AudioException.FFMPEG_FAILED, "FFprobe could not read [" + file + "]: " + result.err().trim() );
		}
		try {
			return JSON.std.mapFrom( new String( result.out(), StandardCharsets.UTF_8 ) );
		} catch ( IOException e ) {
			throw new AudioException( AudioException.FFMPEG_FAILED, "Could not read the FFprobe output: " + e.getMessage(), e );
		}
	}

	/**
	 * Decode the first audio stream of any file FFmpeg can read, video files included, to 32 bit float at the stream's own rate and channels
	 */
	@SuppressWarnings( "unchecked" )
	public AudioBuffer decode( Path file ) {
		Map<String, Object>	info	= probe( file );
		Map<String, Object>	audio	= null;
		for ( Object s : ( List<Object> ) info.getOrDefault( "streams", List.of() ) ) {
			Map<String, Object> stream = ( Map<String, Object> ) s;
			if ( "audio".equals( stream.get( "codec_type" ) ) ) {
				audio = stream;
				break;
			}
		}
		if ( audio == null ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "The file [" + file + "] has no audio stream" );
		}
		int		channels	= Math.min( AudioBuffer.MAX_CHANNELS, ( ( Number ) audio.get( "channels" ) ).intValue() );
		int		rate		= Integer.parseInt( String.valueOf( audio.get( "sample_rate" ) ) );
		// FFmpeg writes the raw samples to a file and they are read from there a block at a time, so memory holds the samples once, not
		// the bytes and then the samples
		Path	raw			= null;
		try {
			raw = Files.createTempFile( "bxaudio-decode-", ".f32" );
			ffmpeg( List.of( "-y", "-i", file.toString(), "-vn", "-map", "0:a:0", "-ac", String.valueOf( channels ), "-f", "f32le", "-acodec", "pcm_f32le",
			    raw.toString() ) );
			long frameCount = Files.size( raw ) / 4 / channels;
			if ( frameCount > Integer.MAX_VALUE - 16 || frameCount * channels * 4L > MAX_DECODE_BYTES ) {
				throw new AudioException( AudioException.TOO_LARGE, "The audio of [" + file + "] is too long to hold in memory" );
			}
			int			frames	= ( int ) frameCount;
			float[][]	data;
			try {
				data = new float[ channels ][ frames ];
			} catch ( OutOfMemoryError e ) {
				throw new AudioException( AudioException.TOO_LARGE, "The audio of [" + file + "] is too long to hold in memory" );
			}
			try ( java.nio.channels.FileChannel in = java.nio.channels.FileChannel.open( raw, java.nio.file.StandardOpenOption.READ ) ) {
				ByteBuffer	block	= ByteBuffer.allocate( 4096 * channels * 4 ).order( ByteOrder.LITTLE_ENDIAN );
				int			done	= 0;
				while ( done < frames ) {
					block.clear();
					block.limit( Math.min( block.capacity(), ( frames - done ) * channels * 4 ) );
					while ( block.hasRemaining() && in.read( block ) >= 0 ) {
						// keep reading until the block is full or the file ends
					}
					block.flip();
					int count = block.remaining() / ( channels * 4 );
					if ( count == 0 ) {
						break;
					}
					for ( int i = 0; i < count; i++ ) {
						for ( int ch = 0; ch < channels; ch++ ) {
							data[ ch ][ done + i ] = block.getFloat();
						}
					}
					done += count;
				}
			}
			return new AudioBuffer( data, rate );
		} catch ( IOException e ) {
			throw new AudioException( AudioException.IO, "Could not read the audio of [" + file + "]: " + e.getMessage(), e );
		} finally {
			try {
				if ( raw != null ) {
					Files.deleteIfExists( raw );
				}
			} catch ( IOException ignored ) {
				// a leftover temporary file is not worth hiding the result
			}
		}
	}

	/**
	 * Write audio as OGG Vorbis, Opus or AAC (m4a or aac)
	 *
	 * @param bitrateKbps Bits per second in thousands, 0 for a good default for the format
	 */
	public void encode( AudioBuffer audio, Path target, int bitrateKbps ) {
		String			name	= target.getFileName().toString().toLowerCase( Locale.ROOT );
		String			ext		= name.contains( "." ) ? name.substring( name.lastIndexOf( '.' ) ) : "";
		List<String>	codec	= switch ( ext ) {
									case ".ogg", ".oga" -> bitrateKbps > 0 ? List.of( "-c:a", "libvorbis", "-b:a", bitrateKbps + "k" )
									    : List.of( "-c:a", "libvorbis", "-q:a", "5" );
									case ".opus" -> List.of( "-c:a", "libopus", "-b:a", ( bitrateKbps > 0 ? bitrateKbps : 128 ) + "k", "-ar", "48000" );
									case ".m4a", ".mp4" ->
									    List.of( "-c:a", "aac", "-b:a", ( bitrateKbps > 0 ? bitrateKbps : 192 ) + "k", "-movflags", "+faststart" );
									case ".aac" -> List.of( "-c:a", "aac", "-b:a", ( bitrateKbps > 0 ? bitrateKbps : 192 ) + "k", "-f", "adts" );
									default ->
									    throw new AudioException( AudioException.UNSUPPORTED_FORMAT,
									        "Cannot write [" + ext + "] files. Use .wav, .mp3, .flac, .ogg, .opus, .m4a or .aac" );
								};
		try {
			Path temp = Files.createTempFile( "bxaudio-", ".wav" );
			try {
				WavCodec.write( audio, temp, 32 );
				Files.createDirectories( target.toAbsolutePath().getParent() );
				List<String> args = new ArrayList<>( List.of( "-y", "-i", temp.toString(), "-vn" ) );
				args.addAll( codec );
				args.add( target.toString() );
				ffmpeg( args );
			} finally {
				Files.deleteIfExists( temp );
			}
		} catch ( IOException e ) {
			throw new AudioException( AudioException.IO, "Could not write [" + target + "]: " + e.getMessage(), e );
		}
	}

	/**
	 * Put audio on a video
	 *
	 * @param video        The video file
	 * @param audio        The audio to add
	 * @param target       The output, whose extension chooses the container (mp4, mov, mkv, webm)
	 * @param keepOriginal True to mix the video's own audio under the new audio, false to replace it
	 * @param originalDb   Gain of the original audio when kept, in dB
	 * @param offset       Seconds to delay the new audio
	 * @param bitrateKbps  AAC bitrate, 0 for 192
	 */
	public void mux( Path video, AudioBuffer audio, Path target, boolean keepOriginal, double originalDb, double offset, int bitrateKbps ) {
		if ( !Files.isRegularFile( video ) ) {
			throw new AudioException( AudioException.FILE_NOT_FOUND, "The video [" + video + "] does not exist" );
		}
		String	name	= target.getFileName().toString().toLowerCase( Locale.ROOT );
		boolean	webm	= name.endsWith( ".webm" );
		try {
			Path temp = Files.createTempFile( "bxaudio-", ".wav" );
			try {
				WavCodec.write( audio, temp, 32 );
				Files.createDirectories( target.toAbsolutePath().getParent() );
				List<String>	args	= new ArrayList<>( List.of( "-y", "-i", video.toString(), "-i", temp.toString() ) );
				String			delay	= offset > 0 ? ",adelay=" + Math.round( offset * 1000 ) + ":all=1" : "";
				if ( keepOriginal && hasAudio( video ) ) {
					args.addAll( List.of( "-filter_complex",
					    "[0:a]volume=" + originalDb + "dB[o];[1:a]anull" + delay + "[n];[o][n]amix=inputs=2:duration=longest:normalize=0[a]",
					    "-map", "0:v:0", "-map", "[a]" ) );
				} else if ( offset > 0 ) {
					args.addAll( List.of( "-filter_complex", "[1:a]anull" + delay + "[a]", "-map", "0:v:0", "-map", "[a]" ) );
				} else {
					args.addAll( List.of( "-map", "0:v:0", "-map", "1:a:0" ) );
				}
				args.addAll( List.of( "-c:v", "copy", "-ac", String.valueOf( Math.min( 2, audio.getChannels() ) ) ) );
				args.addAll( webm ? List.of( "-c:a", "libopus", "-b:a", ( bitrateKbps > 0 ? bitrateKbps : 160 ) + "k", "-ar", "48000" )
				    : List.of( "-c:a", "aac", "-b:a", ( bitrateKbps > 0 ? bitrateKbps : 192 ) + "k" ) );
				args.addAll( List.of( "-t", String.valueOf( videoSeconds( video ) ) ) );
				// Written next to the target and moved into place, so a failure never leaves a broken file where a good one was
				Path part = target.toAbsolutePath()
				    .resolveSibling( ".bxaudio-" + java.util.UUID.randomUUID().toString().substring( 0, 8 ) + "-" + target.getFileName() );
				args.add( part.toString() );
				try {
					ffmpeg( args );
					try {
						Files.move( part, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE );
					} catch ( java.nio.file.AtomicMoveNotSupportedException e ) {
						Files.move( part, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING );
					}
				} finally {
					Files.deleteIfExists( part );
				}
			} finally {
				Files.deleteIfExists( temp );
			}
		} catch ( IOException e ) {
			throw new AudioException( AudioException.IO, "Could not write [" + target + "]: " + e.getMessage(), e );
		}
	}

	@SuppressWarnings( "unchecked" )
	private boolean hasAudio( Path file ) {
		for ( Object s : ( List<Object> ) probe( file ).getOrDefault( "streams", List.of() ) ) {
			if ( "audio".equals( ( ( Map<String, Object> ) s ).get( "codec_type" ) ) ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The length of the video, so the result is never longer than the picture
	 */
	@SuppressWarnings( "unchecked" )
	private double videoSeconds( Path video ) {
		Object duration = ( ( Map<String, Object> ) probe( video ).get( "format" ) ).get( "duration" );
		return duration == null ? 1e9 : Double.parseDouble( String.valueOf( duration ) );
	}

}
