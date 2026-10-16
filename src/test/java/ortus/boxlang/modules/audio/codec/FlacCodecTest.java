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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.dsp.MasteringTest;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class FlacCodecTest {

	private static AudioBuffer music( int channels, double seconds ) {
		Random		random	= new Random( 9 );
		int			rate	= 44100;
		int			frames	= ( int ) ( seconds * rate );
		float[][]	data	= new float[ channels ][ frames ];
		for ( int i = 0; i < frames; i++ ) {
			double base = 0.4 * Math.sin( 2 * Math.PI * 220 * i / rate ) + 0.2 * Math.sin( 2 * Math.PI * 1333 * i / rate );
			for ( int ch = 0; ch < channels; ch++ ) {
				data[ ch ][ i ] = ( float ) ( base * ( ch == 0 ? 1 : 0.7 ) + 0.01 * ( random.nextDouble() - 0.5 ) );
			}
		}
		return new AudioBuffer( data, rate );
	}

	private static void assertSame( AudioBuffer a, AudioBuffer b, double tolerance ) {
		assertThat( b.getChannels() ).isEqualTo( a.getChannels() );
		assertThat( b.getFrames() ).isEqualTo( a.getFrames() );
		assertThat( b.getSampleRate() ).isEqualTo( a.getSampleRate() );
		for ( int ch = 0; ch < a.getChannels(); ch++ ) {
			for ( int i = 0; i < a.getFrames(); i++ ) {
				assertThat( ( double ) b.channel( ch )[ i ] ).isWithin( tolerance ).of( a.channel( ch )[ i ] );
			}
		}
	}

	@DisplayName( "audio survives FLAC encoding and decoding at 16 and 24 bits, mono, stereo and many channels" )
	@Test
	void testRoundTrip() {
		for ( int channels : new int[] { 1, 2, 3, 6 } ) {
			AudioBuffer a = music( channels, 1.5 );
			assertSame( a, FlacCodec.decode( FlacCodec.encode( a, 16 ) ), 2.0 / 32768 );
			assertSame( a, FlacCodec.decode( FlacCodec.encode( a, 24 ) ), 1.0 / 8388608 );
		}
	}

	@DisplayName( "silence, a constant level, full scale noise and a length that is not a block multiple all round trip" )
	@Test
	void testEdgeCases() {
		assertSame( AudioBuffer.silenceSeconds( 0.3, 2, 44100 ), FlacCodec.decode( FlacCodec.encode( AudioBuffer.silenceSeconds( 0.3, 2, 44100 ), 16 ) ),
		    2.0 / 32768 );
		Random		random	= new Random( 3 );
		float[][]	noise	= new float[ 2 ][ 5000 ];
		for ( int i = 0; i < 5000; i++ ) {
			noise[ 0 ][ i ]	= random.nextBoolean() ? 1f : -1f;
			noise[ 1 ][ i ]	= ( float ) ( random.nextDouble() * 2 - 1 );
		}
		AudioBuffer loud = new AudioBuffer( noise, 48000 );
		assertSame( loud, FlacCodec.decode( FlacCodec.encode( loud, 16 ) ), 2.0 / 32768 );
		AudioBuffer tiny = new AudioBuffer( new float[][] { { 0.5f, -0.5f, 0.25f } }, 8000 );
		assertSame( tiny, FlacCodec.decode( FlacCodec.encode( tiny, 16 ) ), 2.0 / 32768 );
		AudioBuffer none = AudioBuffer.silence( 2, 0, 44100 );
		assertThat( FlacCodec.decode( FlacCodec.encode( none, 16 ) ).getFrames() ).isEqualTo( 0 );
	}

	@DisplayName( "music compresses well below the size of PCM" )
	@Test
	void testCompression() {
		AudioBuffer	a	= MasteringTest.sine( 440, 3, -6, 2 );
		int			pcm	= a.getFrames() * 2 * 2;
		assertThat( FlacCodec.encode( a, 16 ).length ).isLessThan( pcm / 3 );
		AudioBuffer m = music( 2, 2 );
		assertThat( FlacCodec.encode( m, 16 ).length ).isLessThan( m.getFrames() * 4 * 7 / 10 );
	}

	@DisplayName( "a file written by ffmpeg, with LPC subframes, decodes to the same samples ffmpeg decodes it to" )
	@Test
	void testDecodesFfmpegFile() throws IOException {
		byte[]		flac		= resource( "flac/lpc-stereo.flac" );
		byte[]		wav			= resource( "flac/lpc-stereo.wav" );
		AudioBuffer	decoded		= FlacCodec.decode( flac );
		AudioBuffer	reference	= WavCodec.decode( wav );
		assertSame( reference, decoded, 1e-9 );
	}

	private static byte[] resource( String name ) throws IOException {
		return Files.readAllBytes( Path.of( "src", "test", "resources", name ) );
	}

	@DisplayName( "damaged and foreign input is refused with a message" )
	@Test
	void testErrors( @TempDir Path dir ) throws IOException {
		assertThat( assertThrows( BoxRuntimeException.class, () -> FlacCodec.decode( new byte[] { 1, 2, 3, 4, 5 } ) ).getMessage() ).contains( "Not a FLAC" );
		byte[]	good	= FlacCodec.encode( music( 2, 0.5 ), 16 );
		byte[]	cut		= java.util.Arrays.copyOf( good, good.length / 2 );
		assertThrows( BoxRuntimeException.class, () -> FlacCodec.decode( cut ) );
		assertThat( assertThrows( BoxRuntimeException.class, () -> FlacCodec.encode( music( 2, 0.1 ), 12 ) ).getMessage() ).contains( "16 or 24" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> FlacCodec.encode( music( 9, 0.1 ), 16 ) ).getMessage() ).contains( "at most 8" );
		Path file = dir.resolve( "x.flac" );
		AudioFiles.write( music( 2, 0.2 ), file, 16, 0 );
		assertThat( AudioFiles.read( file ).getChannels() ).isEqualTo( 2 );
	}

}
