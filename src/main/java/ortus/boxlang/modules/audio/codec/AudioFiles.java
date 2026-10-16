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
package ortus.boxlang.modules.audio.codec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import ortus.boxlang.modules.audio.ffmpeg.FfmpegSupport;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Reading and writing audio files, choosing the format: reading goes by what is in the file and writing goes by the file extension.
 */
public final class AudioFiles {

	/**
	 * Extensions of files FFmpeg is asked to read when the content is none of WAV, MP3 and FLAC
	 */
	private static final Set<String> FFMPEG_READABLE = Set.of( ".ogg", ".oga", ".opus", ".m4a", ".mp4", ".m4v", ".mov", ".mkv", ".webm", ".avi", ".aac", ".wma",
	    ".wmv", ".aiff", ".aif", ".aifc", ".ac3", ".amr", ".3gp", ".flv", ".mpg", ".mpeg", ".ts", ".caf", ".mka", ".wv", ".ape" );

	private AudioFiles() {
	}

	/**
	 * @return The extension of a file name with its dot, in lower case, or an empty string
	 */
	static String extension( Path path ) {
		String	name	= path.getFileName().toString().toLowerCase( Locale.ROOT );
		int		dot		= name.lastIndexOf( '.' );
		return dot < 0 ? "" : name.substring( dot );
	}

	/**
	 * @return Whether the bytes start like a container FFmpeg reads: Ogg, MP4 and QuickTime, Matroska and WebM, AIFF, ASF
	 */
	private static boolean looksLikeFfmpegContainer( byte[] b ) {
		if ( b.length < 12 ) {
			return false;
		}
		String head = new String( b, 0, 12, java.nio.charset.StandardCharsets.ISO_8859_1 );
		return head.startsWith( "OggS" ) || head.startsWith( "FORM" ) || head.startsWith( "caff" ) || head.startsWith( "\u001a\u0045\u00df\u00a3" )
		    || head.substring( 4 ).startsWith( "ftyp" ) || head.startsWith( "\u0030\u0026\u00b2\u0075" );
	}

	/**
	 * Stop early, before any folder is made, when a file cannot be written because of its extension
	 *
	 * @throws BoxRuntimeException for an extension that is not supported
	 */
	public static void checkWritable( Path path ) {
		String ext = extension( path );
		switch ( ext ) {
			case "", ".wav", ".wave", ".mp3", ".flac", ".ogg", ".oga", ".opus", ".m4a", ".mp4", ".aac" -> {
			}
			default -> throw new BoxRuntimeException( "Cannot write [" + ext + "] files. Use .wav, .mp3, .flac, .ogg, .opus, .m4a or .aac" );
		}
	}

	/**
	 * Write to a temporary file next to the target and move it into place, so a failure half way never leaves a truncated file where a good one
	 * was. The temporary name starts with a prefix and keeps the real name last, so the extension still tells the encoder the format.
	 */
	private static void atomically( Path target, java.util.function.Consumer<Path> writer ) {
		Path	dir		= target.toAbsolutePath().getParent();
		Path	temp	= dir.resolve( ".bxaudio-" + UUID.randomUUID().toString().substring( 0, 8 ) + "-" + target.getFileName() );
		try {
			writer.accept( temp );
			try {
				Files.move( temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE );
			} catch ( java.nio.file.AtomicMoveNotSupportedException e ) {
				Files.move( temp, target, StandardCopyOption.REPLACE_EXISTING );
			}
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Could not write audio file [" + target + "]: " + e.getMessage(), e );
		} finally {
			try {
				Files.deleteIfExists( temp );
			} catch ( IOException ignored ) {
				// a leftover temporary file is not worth hiding the real result
			}
		}
	}

	/**
	 * Read a WAV, MP3 or FLAC file. The format is found from the content, so a file with the wrong extension still loads.
	 */
	public static AudioBuffer read( Path path ) {
		byte[] bytes;
		try {
			bytes = Files.readAllBytes( path );
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Could not read audio file [" + path + "]: " + e.getMessage(), e );
		}
		bytes = withoutId3BeforeFlac( bytes );
		if ( bytes.length >= 12 && bytes[ 0 ] == 'R' && bytes[ 1 ] == 'I' && bytes[ 2 ] == 'F' && bytes[ 3 ] == 'F' ) {
			return WavCodec.decode( bytes );
		}
		if ( Mp3Codec.looksLikeMp3( bytes ) ) {
			return Mp3Codec.decode( bytes );
		}
		if ( FlacCodec.looksLikeFlac( bytes ) ) {
			return FlacCodec.decode( bytes );
		}
		if ( !FFMPEG_READABLE.contains( extension( path ) ) && !looksLikeFfmpegContainer( bytes ) ) {
			throw new BoxRuntimeException( "[" + path.getFileName()
			    + "] is not an audio file this module can read: it is not WAV, MP3 or FLAC, and its extension is not one FFmpeg is asked to read" );
		}
		return FfmpegSupport.require( "Reading [" + path.getFileName() + "], which is not a WAV, MP3 or FLAC file," ).decode( path );
	}

	/**
	 * A FLAC file may start with an ID3v2 tag. The tag is cut off then, so the file is not taken for an MP3.
	 */
	private static byte[] withoutId3BeforeFlac( byte[] bytes ) {
		if ( bytes.length > 14 && bytes[ 0 ] == 'I' && bytes[ 1 ] == 'D' && bytes[ 2 ] == '3' ) {
			int	size	= ( ( bytes[ 6 ] & 0x7F ) << 21 ) | ( ( bytes[ 7 ] & 0x7F ) << 14 ) | ( ( bytes[ 8 ] & 0x7F ) << 7 ) | ( bytes[ 9 ] & 0x7F );
			int	at		= 10 + size;
			if ( at + 4 <= bytes.length && bytes[ at ] == 'f' && bytes[ at + 1 ] == 'L' && bytes[ at + 2 ] == 'a' && bytes[ at + 3 ] == 'C' ) {
				return Arrays.copyOfRange( bytes, at, bytes.length );
			}
		}
		return bytes;
	}

	/**
	 * Write a file as WAV, MP3 or FLAC depending on its extension, replacing any existing file. No extension means WAV.
	 *
	 * @param audio       The audio to write
	 * @param path        The destination, {@code .wav}, {@code .wave}, {@code .mp3} or {@code .flac}
	 * @param bitDepth    For WAV: 16 or 24 for integer PCM, 32 for float. For FLAC: 16 or 24. Ignored for MP3.
	 * @param bitrateKbps For MP3, OGG, Opus and AAC: the bitrate in kbps, 0 for the format's default. Ignored for WAV and FLAC.
	 */
	public static void write( AudioBuffer audio, Path path, int bitDepth, int bitrateKbps ) {
		String ext = extension( path );
		checkWritable( path );
		atomically( path, temp -> {
			try {
				switch ( ext ) {
					case "", ".wav", ".wave" -> WavCodec.write( audio, temp, bitDepth );
					case ".mp3" -> Files.write( temp, Mp3Codec.encode( audio, bitrateKbps ) );
					case ".flac" -> Files.write( temp, FlacCodec.encode( audio, bitDepth == 24 ? 24 : 16 ) );
					default -> FfmpegSupport.require( "Writing " + ext + " files" ).encode( audio, temp, bitrateKbps );
				}
			} catch ( IOException e ) {
				throw new BoxRuntimeException( "Could not write audio file [" + path + "]: " + e.getMessage(), e );
			}
		} );
	}

}
