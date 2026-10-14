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

import java.io.ByteArrayOutputStream;
import javax.sound.sampled.AudioFormat;

import de.sciss.jump3r.lowlevel.LameEncoder;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * The part of the MP3 codec that uses jump3r, which describes its input with a {@code javax.sound.sampled.AudioFormat}. Only loaded when
 * MP3 is used, see {@link Mp3Codec}.
 */
final class Mp3Encoder {

	private Mp3Encoder() {
	}

	static byte[] encode( AudioBuffer audio, int bitrateKbps ) {
		int			channels	= audio.getChannels();
		int			frames		= audio.getFrames();
		AudioFormat	format		= new AudioFormat( AudioFormat.Encoding.PCM_SIGNED, audio.getSampleRate(), 16, channels, channels * 2, audio.getSampleRate(),
		    false );
		LameEncoder	encoder		= new LameEncoder( format, bitrateKbps, LameEncoder.CHANNEL_MODE_AUTO, LameEncoder.QUALITY_HIGH, false );
		try {
			ByteArrayOutputStream	mp3		= new ByteArrayOutputStream( Math.max( 1024, frames / 8 ) );
			// Interleaved 16 bit little endian samples, converted a block at a time to keep memory flat
			int						block	= Math.max( 1, encoder.getPCMBufferSize() / ( channels * 2 ) );
			byte[]					pcm		= new byte[ block * channels * 2 ];
			byte[]					out		= new byte[ Math.max( encoder.getMP3BufferSize(), encoder.getPCMBufferSize() ) ];
			for ( int start = 0; start < frames; start += block ) {
				int	count	= Math.min( block, frames - start );
				int	at		= 0;
				for ( int i = 0; i < count; i++ ) {
					for ( int ch = 0; ch < channels; ch++ ) {
						float	sample	= Math.max( -1f, Math.min( 1f, audio.channel( ch )[ start + i ] ) );
						int		v		= Math.round( sample * 32767f );
						pcm[ at++ ]	= ( byte ) v;
						pcm[ at++ ]	= ( byte ) ( v >> 8 );
					}
				}
				int written = encoder.encodeBuffer( pcm, 0, count * channels * 2, out );
				mp3.write( out, 0, written );
			}
			int written = encoder.encodeFinish( out );
			mp3.write( out, 0, written );
			return mp3.toByteArray();
		} catch ( RuntimeException e ) {
			throw new BoxRuntimeException( "Could not encode the MP3: " + e.getMessage(), e );
		} finally {
			encoder.close();
		}
	}

}
