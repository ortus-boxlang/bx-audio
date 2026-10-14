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
import java.util.Locale;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Reading and writing audio files, choosing the format: reading goes by what is in the file and writing goes by the file extension.
 */
public final class AudioFiles {

	private AudioFiles() {
	}

	/**
	 * Read a WAV or MP3 file. The format is found from the content, so a file with the wrong extension still loads.
	 */
	public static AudioBuffer read( Path path ) {
		byte[] bytes;
		try {
			bytes = Files.readAllBytes( path );
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Could not read audio file [" + path + "]: " + e.getMessage(), e );
		}
		if ( bytes.length >= 12 && bytes[ 0 ] == 'R' && bytes[ 1 ] == 'I' && bytes[ 2 ] == 'F' && bytes[ 3 ] == 'F' ) {
			return WavCodec.decode( bytes );
		}
		if ( Mp3Codec.looksLikeMp3( bytes ) ) {
			return Mp3Codec.decode( bytes );
		}
		throw new BoxRuntimeException( "The file [" + path + "] is not a WAV or MP3 file" );
	}

	/**
	 * Write a file as WAV or MP3 depending on its extension, replacing any existing file. No extension means WAV.
	 *
	 * @param audio       The audio to write
	 * @param path        The destination, {@code .wav}, {@code .wave} or {@code .mp3}
	 * @param bitDepth    For WAV: 16 or 24 for integer PCM, 32 for float. Ignored for MP3.
	 * @param bitrateKbps For MP3: the constant bitrate. Ignored for WAV.
	 */
	public static void write( AudioBuffer audio, Path path, int bitDepth, int bitrateKbps ) {
		String	name	= path.getFileName().toString().toLowerCase( Locale.ROOT );
		int		dot		= name.lastIndexOf( '.' );
		String	ext		= dot < 0 ? "" : name.substring( dot );
		switch ( ext ) {
			case "", ".wav", ".wave" -> WavCodec.write( audio, path, bitDepth );
			case ".mp3" -> {
				byte[] mp3 = Mp3Codec.encode( audio, bitrateKbps );
				try {
					Files.write( path, mp3 );
				} catch ( IOException e ) {
					throw new BoxRuntimeException( "Could not write audio file [" + path + "]: " + e.getMessage(), e );
				}
			}
			default -> throw new BoxRuntimeException( "Cannot write [" + ext + "] files. Use .wav or .mp3" );
		}
	}

}
