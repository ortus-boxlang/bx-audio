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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ortus.boxlang.modules.audio.synth.Synth;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class Mp3CodecTest {

	/**
	 * A stereo test tone: 440 Hz on the left and 880 Hz on the right, so both channels are checked and cannot be mixed up
	 */
	private static AudioBuffer tone( int channels, double seconds, int sampleRate ) {
		float[][] samples = new float[ channels ][];
		samples[ 0 ] = Synth.oscillator( "sine", 440, seconds, sampleRate, 16, null, 0.5, 0 ).channel( 0 ).clone();
		for ( int i = 0; i < samples[ 0 ].length; i++ ) {
			samples[ 0 ][ i ] *= 0.5f;
		}
		if ( channels == 2 ) {
			samples[ 1 ] = Synth.oscillator( "sine", 880, seconds, sampleRate, 16, null, 0.5, 0 ).channel( 0 ).clone();
			for ( int i = 0; i < samples[ 1 ].length; i++ ) {
				samples[ 1 ][ i ] *= 0.5f;
			}
		}
		return new AudioBuffer( samples, sampleRate );
	}

	private static double rms( float[] data, int from, int to ) {
		double sum = 0;
		for ( int i = from; i < to; i++ ) {
			sum += ( double ) data[ i ] * data[ i ];
		}
		return Math.sqrt( sum / ( to - from ) );
	}

	/**
	 * The best correlation between the original and the decoded signal over a range of delays. MP3 encoders and decoders add about a thousand
	 * samples of delay, so the signals only line up after shifting.
	 */
	private static double bestCorrelation( float[] original, float[] decoded ) {
		int		from	= 20000, length = 20000;
		double	best	= -1;
		for ( int lag = 0; lag < 4000; lag++ ) {
			double dot = 0, a = 0, b = 0;
			for ( int i = from; i < from + length; i++ ) {
				double x = original[ i ], y = decoded[ i + lag ];
				dot	+= x * y;
				a	+= x * x;
				b	+= y * y;
			}
			best = Math.max( best, dot / Math.sqrt( a * b ) );
		}
		return best;
	}

	private static int zeroCrossings( float[] data, int from, int to ) {
		int count = 0;
		for ( int i = from + 1; i < to; i++ ) {
			if ( ( data[ i - 1 ] < 0 ) != ( data[ i ] < 0 ) ) {
				count++;
			}
		}
		return count;
	}

	@DisplayName( "The optional codec jars are found" )
	@Test
	void testAvailable() {
		assertThat( Mp3Codec.isAvailable() ).isTrue();
		Mp3Codec.requireAvailable();
	}

	@DisplayName( "A stereo round trip keeps format, pitch of each channel, level and the waveform" )
	@Test
	void testStereoRoundTrip() {
		AudioBuffer	original	= tone( 2, 2.0, 44100 );
		byte[]		mp3			= Mp3Codec.encode( original, 128 );
		AudioBuffer	back		= Mp3Codec.decode( mp3 );

		assertThat( back.getChannels() ).isEqualTo( 2 );
		assertThat( back.getSampleRate() ).isEqualTo( 44100 );
		// 128 kbps for 2 seconds is 32 KB, plus the frame that carries the encoder delay and padding
		assertThat( mp3.length ).isGreaterThan( 30000 );
		assertThat( mp3.length ).isLessThan( 36000 );
		// the decoder returns whole frames of 1152 samples and the codec pair adds a delay, so it is a little longer, never shorter
		assertThat( back.getFrames() ).isAtLeast( 2 * 44100 );
		assertThat( back.getFrames() ).isLessThan( 2 * 44100 + 5000 );
		// 440 Hz is 880 crossings a second and 880 Hz is 1760, measured over one second in the middle
		assertThat( zeroCrossings( back.channel( 0 ), 30000, 30000 + 44100 ) ).isIn( com.google.common.collect.Range.closed( 870, 890 ) );
		assertThat( zeroCrossings( back.channel( 1 ), 30000, 30000 + 44100 ) ).isIn( com.google.common.collect.Range.closed( 1750, 1770 ) );
		// the level is kept within 1 dB
		double ratio = rms( back.channel( 0 ), 20000, 60000 ) / rms( original.channel( 0 ), 20000, 60000 );
		assertThat( 20 * Math.log10( ratio ) ).isWithin( 1.0 ).of( 0.0 );
		// and the waveform itself matches the original
		assertThat( bestCorrelation( original.channel( 0 ), back.channel( 0 ) ) ).isGreaterThan( 0.99 );
		assertThat( bestCorrelation( original.channel( 1 ), back.channel( 1 ) ) ).isGreaterThan( 0.99 );
	}

	@DisplayName( "Mono audio at a lower sample rate round trips" )
	@Test
	void testMonoLowRate() {
		AudioBuffer	original	= tone( 1, 2.0, 22050 );
		byte[]		mp3			= Mp3Codec.encode( original, 64 );
		AudioBuffer	back		= Mp3Codec.decode( mp3 );
		assertThat( back.getChannels() ).isEqualTo( 1 );
		assertThat( back.getSampleRate() ).isEqualTo( 22050 );
		assertThat( mp3.length ).isGreaterThan( 14000 );
		assertThat( mp3.length ).isLessThan( 18000 );
		assertThat( zeroCrossings( back.channel( 0 ), 10000, 10000 + 22050 ) ).isIn( com.google.common.collect.Range.closed( 870, 890 ) );
	}

	@DisplayName( "The file size follows the bitrate" )
	@Test
	void testBitrateSize() {
		AudioBuffer	audio	= tone( 2, 3.0, 44100 );
		int			low		= Mp3Codec.encode( audio, 64 ).length;
		int			high	= Mp3Codec.encode( audio, 192 ).length;
		assertThat( ( double ) high / low ).isWithin( 0.35 ).of( 3.0 );
		assertThat( low ).isWithin( 2000 ).of( 64 * 1000 / 8 * 3 );
		assertThat( high ).isWithin( 3000 ).of( 192 * 1000 / 8 * 3 );
	}

	@DisplayName( "Every sample rate MP3 can hold encodes and decodes at the same rate" )
	@ParameterizedTest
	@ValueSource( ints = { 8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000 } )
	void testSampleRates( int rate ) {
		AudioBuffer	original	= tone( 2, 1.0, rate );
		AudioBuffer	back		= Mp3Codec.decode( Mp3Codec.encode( original, rate >= 32000 ? 128 : 64 ) );
		assertThat( back.getSampleRate() ).isEqualTo( rate );
		assertThat( back.getChannels() ).isEqualTo( 2 );
		assertThat( back.getFrames() ).isAtLeast( rate );
		assertThat( back.getFrames() ).isLessThan( rate + 5000 );
		assertThat( back.getPeak() ).isGreaterThan( 0.3f );
	}

	@DisplayName( "Loud and clipping input is limited and does not break the encoder" )
	@Test
	void testClipping() {
		AudioBuffer	loud	= new AudioBuffer( new float[][] { Arrays.copyOf( new float[] { 3f, -3f }, 44100 ), new float[ 44100 ] }, 44100 );
		AudioBuffer	back	= Mp3Codec.decode( Mp3Codec.encode( loud, 128 ) );
		assertThat( back.getFrames() ).isAtLeast( 44100 );
		for ( float v : back.channel( 0 ) ) {
			assertThat( Float.isNaN( v ) ).isFalse();
		}
	}

	@DisplayName( "An ID3v2 tag in front of the audio is skipped" )
	@Test
	void testId3Tag() {
		byte[]					mp3		= Mp3Codec.encode( tone( 2, 1.0, 44100 ), 128 );
		ByteArrayOutputStream	tagged	= new ByteArrayOutputStream();
		// "ID3", version 2.3.0, no flags, then a 28 bit "syncsafe" size of 20 bytes, then 20 bytes of tag
		tagged.writeBytes( new byte[] { 'I', 'D', '3', 3, 0, 0, 0, 0, 0, 20 } );
		tagged.writeBytes( new byte[ 20 ] );
		tagged.writeBytes( mp3 );
		assertThat( Mp3Codec.looksLikeMp3( tagged.toByteArray() ) ).isTrue();
		AudioBuffer	withTag		= Mp3Codec.decode( tagged.toByteArray() );
		AudioBuffer	withoutTag	= Mp3Codec.decode( mp3 );
		assertThat( withTag.getFrames() ).isEqualTo( withoutTag.getFrames() );
		assertThat( withTag.channel( 0 ) ).isEqualTo( withoutTag.channel( 0 ) );
	}

	@DisplayName( "A file cut short still decodes what is there" )
	@Test
	void testTruncated() {
		byte[]	mp3		= Mp3Codec.encode( tone( 2, 2.0, 44100 ), 128 );
		int		whole	= Mp3Codec.decode( mp3 ).getFrames();
		int		half	= Mp3Codec.decode( Arrays.copyOf( mp3, mp3.length / 2 ) ).getFrames();
		assertThat( half ).isLessThan( whole );
		assertThat( half ).isGreaterThan( whole / 3 );
	}

	@DisplayName( "MP3 data is recognised and other data is not" )
	@Test
	void testLooksLikeMp3() {
		assertThat( Mp3Codec.looksLikeMp3( new byte[] { 'I', 'D', '3', 4, 0 } ) ).isTrue();
		// a layer III frame sync: 0xFF 0xFB
		assertThat( Mp3Codec.looksLikeMp3( new byte[] { ( byte ) 0xFF, ( byte ) 0xFB, ( byte ) 0x90, 0 } ) ).isTrue();
		// reserved layer bits
		assertThat( Mp3Codec.looksLikeMp3( new byte[] { ( byte ) 0xFF, ( byte ) 0xF9, ( byte ) 0x90, 0 } ) ).isFalse();
		assertThat( Mp3Codec.looksLikeMp3( "RIFF....WAVE".getBytes() ) ).isFalse();
		assertThat( Mp3Codec.looksLikeMp3( "just some text".getBytes() ) ).isFalse();
		assertThat( Mp3Codec.looksLikeMp3( new byte[ 0 ] ) ).isFalse();
		assertThat( Mp3Codec.looksLikeMp3( new byte[] { ( byte ) 0xFF, ( byte ) 0xFB } ) ).isFalse();
	}

	@DisplayName( "Data that is not MP3 is rejected with a clear error" )
	@Test
	void testBadData() {
		assertThrows( BoxRuntimeException.class, () -> Mp3Codec.decode( new byte[ 100 ] ) );
		assertThrows( BoxRuntimeException.class, () -> Mp3Codec.decode( "this is definitely not an mp3 file at all".getBytes() ) );
		assertThrows( BoxRuntimeException.class, () -> Mp3Codec.decode( new byte[ 0 ] ) );
	}

	@DisplayName( "Unsupported formats and bitrates are rejected before encoding, saying what to use instead" )
	@Test
	void testEncodeErrors() {
		BoxRuntimeException rate = assertThrows( BoxRuntimeException.class, () -> Mp3Codec.encode( tone( 2, 0.2, 10000 ), 128 ) );
		assertThat( rate.getMessage() ).contains( "audioResample" );
		BoxRuntimeException channels = assertThrows( BoxRuntimeException.class, () -> Mp3Codec.encode( AudioBuffer.silence( 3, 1000, 44100 ), 128 ) );
		assertThat( channels.getMessage() ).contains( "audioChannels" );
		// 100 is not a valid MPEG 1 bitrate, and 192 is too high for MPEG 2 at 22.05 kHz
		BoxRuntimeException bitrate = assertThrows( BoxRuntimeException.class, () -> Mp3Codec.encode( tone( 2, 0.2, 44100 ), 100 ) );
		assertThat( bitrate.getMessage() ).contains( "192" );
		assertThrows( BoxRuntimeException.class, () -> Mp3Codec.encode( tone( 1, 0.2, 22050 ), 192 ) );
		assertThrows( BoxRuntimeException.class, () -> Mp3Codec.encode( AudioBuffer.silence( 2, 0, 44100 ), 128 ) );
	}

	@DisplayName( "Valid bitrates depend on the sample rate" )
	@Test
	void testBitrates() {
		assertThat( Mp3Codec.bitratesFor( 44100 ) ).asList().contains( 320 );
		assertThat( Mp3Codec.bitratesFor( 44100 ) ).asList().doesNotContain( 8 );
		assertThat( Mp3Codec.bitratesFor( 22050 ) ).asList().contains( 8 );
		assertThat( Mp3Codec.bitratesFor( 22050 ) ).asList().doesNotContain( 320 );
		assertThat( Mp3Codec.bitratesFor( 8000 ) ).asList().contains( 160 );
	}

}
