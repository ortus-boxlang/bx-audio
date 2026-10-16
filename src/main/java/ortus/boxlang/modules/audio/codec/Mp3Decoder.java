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

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * The part of the MP3 codec that uses JLayer. Only loaded when MP3 is used, see {@link Mp3Codec}.
 */
final class Mp3Decoder {

	private Mp3Decoder() {
	}

	static AudioBuffer decode( byte[] bytes ) {
		Bitstream	stream		= new Bitstream( new ByteArrayInputStream( bytes ) );
		Decoder		decoder		= new Decoder();
		short[]		pcm			= new short[ 1 << 16 ];
		int			used		= 0;
		int			channels	= 0;
		int			rate		= 0;
		try {
			Header header;
			while ( ( header = stream.readFrame() ) != null ) {
				SampleBuffer frame = ( SampleBuffer ) decoder.decodeFrame( header, stream );
				if ( channels == 0 ) {
					channels	= frame.getChannelCount();
					rate		= frame.getSampleFrequency();
				}
				// A stream that changes format part way through cannot be held in one buffer
				if ( frame.getChannelCount() != channels || frame.getSampleFrequency() != rate ) {
					throw new AudioException( AudioException.UNSUPPORTED_FORMAT,
					    "This MP3 changes its sample rate or channel count part way through, which is not supported" );
				}
				int length = frame.getBufferLength();
				if ( used + length > pcm.length ) {
					pcm = Arrays.copyOf( pcm, Math.max( pcm.length * 2, used + length ) );
				}
				System.arraycopy( frame.getBuffer(), 0, pcm, used, length );
				used += length;
				stream.closeFrame();
			}
		} catch ( BitstreamException | DecoderException e ) {
			throw new AudioException( AudioException.IO, "Could not decode the MP3: " + e.getMessage(), e );
		} finally {
			try {
				stream.close();
			} catch ( BitstreamException ignored ) {
				// nothing useful to do with a stream that will not close
			}
		}
		if ( channels == 0 || used == 0 ) {
			throw new AudioException( AudioException.CORRUPT_AUDIO, "No MP3 audio was found in the file" );
		}
		int				total	= used / channels;
		// The Info frame holds no sound. When it carries a LAME tag the delay at the start and the padding at the end are cut as well, so the
		// sound has the length and the timing it was encoded with. Without a tag nothing is known about them and nothing more is cut.
		Mp3Gapless.Info	info	= Mp3Gapless.read( bytes );
		int				from	= 0;
		int				to		= total;
		if ( info.infoLength() > 0 ) {
			from = info.samples();
			if ( info.delay() >= 0 ) {
				from += info.delay() + Mp3Gapless.DECODER_DELAY;
				long end = info.frames() >= 0 ? info.samples() + info.frames() * info.samples() : total;
				to = ( int ) Math.min( total, end - info.padding() + Mp3Gapless.DECODER_DELAY );
			}
			from	= Math.min( from, total );
			to		= Math.max( from, to );
		}
		int frames = to - from;
		if ( frames == 0 ) {
			throw new AudioException( AudioException.CORRUPT_AUDIO, "No MP3 audio was found in the file" );
		}
		float[][] out = new float[ channels ][ frames ];
		for ( int i = 0; i < frames; i++ ) {
			for ( int ch = 0; ch < channels; ch++ ) {
				out[ ch ][ i ] = pcm[ ( from + i ) * channels + ch ] / 32768f;
			}
		}
		return new AudioBuffer( out, rate );
	}

}
