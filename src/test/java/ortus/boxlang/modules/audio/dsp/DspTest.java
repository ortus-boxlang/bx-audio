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
package ortus.boxlang.modules.audio.dsp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class DspTest {

	private static AudioBuffer constant( int channels, int frames, int sampleRate, float value ) {
		float[][] samples = new float[ channels ][ frames ];
		for ( float[] channel : samples ) {
			java.util.Arrays.fill( channel, value );
		}
		return new AudioBuffer( samples, sampleRate );
	}

	private static AudioBuffer sine( double freq, double seconds, int sampleRate ) {
		int		frames	= ( int ) Math.round( seconds * sampleRate );
		float[]	data	= new float[ frames ];
		for ( int i = 0; i < frames; i++ ) {
			data[ i ] = ( float ) Math.sin( 2 * Math.PI * freq * i / sampleRate );
		}
		return new AudioBuffer( new float[][] { data }, sampleRate );
	}

	/**
	 * RMS over a section, to compare away from the filter edges
	 */
	private static double rms( float[] data, int from, int to ) {
		double sum = 0;
		for ( int i = from; i < to; i++ ) {
			sum += data[ i ] * data[ i ];
		}
		return Math.sqrt( sum / ( to - from ) );
	}

	@DisplayName( "Gain in decibels scales amplitude and leaves the input alone" )
	@Test
	void testGain() {
		AudioBuffer	in		= constant( 2, 4, 44100, 0.5f );
		AudioBuffer	louder	= Dsp.gain( in, 6.0206 );
		assertThat( louder.channel( 0 )[ 0 ] ).isWithin( 1e-4f ).of( 1.0f );
		assertThat( Dsp.gain( in, -6.0206 ).channel( 1 )[ 3 ] ).isWithin( 1e-4f ).of( 0.25f );
		assertThat( Dsp.gain( in, 0 ).channel( 0 )[ 0 ] ).isEqualTo( 0.5f );
		assertThat( in.channel( 0 )[ 0 ] ).isEqualTo( 0.5f );
		assertThrows( BoxRuntimeException.class, () -> Dsp.gain( in, Double.NaN ) );
	}

	@DisplayName( "Mono pan uses an equal power law" )
	@Test
	void testPanMono() {
		AudioBuffer	in		= constant( 1, 2, 44100, 1f );
		AudioBuffer	center	= Dsp.pan( in, 0 );
		assertThat( center.getChannels() ).isEqualTo( 2 );
		assertThat( center.channel( 0 )[ 0 ] ).isWithin( 1e-6f ).of( ( float ) Math.sqrt( 0.5 ) );
		assertThat( center.channel( 1 )[ 0 ] ).isWithin( 1e-6f ).of( ( float ) Math.sqrt( 0.5 ) );
		AudioBuffer left = Dsp.pan( in, -1 );
		assertThat( left.channel( 0 )[ 0 ] ).isWithin( 1e-6f ).of( 1f );
		assertThat( left.channel( 1 )[ 0 ] ).isWithin( 1e-6f ).of( 0f );
		AudioBuffer right = Dsp.pan( in, 1 );
		assertThat( right.channel( 0 )[ 0 ] ).isWithin( 1e-6f ).of( 0f );
		assertThat( right.channel( 1 )[ 0 ] ).isWithin( 1e-6f ).of( 1f );
		// total power stays constant across the field
		AudioBuffer	quarter	= Dsp.pan( in, 0.5 );
		double		power	= quarter.channel( 0 )[ 0 ] * quarter.channel( 0 )[ 0 ] + quarter.channel( 1 )[ 0 ] * quarter.channel( 1 )[ 0 ];
		assertThat( power ).isWithin( 1e-6 ).of( 1.0 );
	}

	@DisplayName( "Stereo pan is a balance control and bad input is rejected" )
	@Test
	void testPanStereoAndErrors() {
		AudioBuffer	in		= constant( 2, 1, 44100, 1f );
		AudioBuffer	right	= Dsp.pan( in, 1 );
		assertThat( right.channel( 0 )[ 0 ] ).isWithin( 1e-6f ).of( 0f );
		assertThat( right.channel( 1 )[ 0 ] ).isEqualTo( 1f );
		AudioBuffer left = Dsp.pan( in, -0.5 );
		assertThat( left.channel( 0 )[ 0 ] ).isEqualTo( 1f );
		assertThat( left.channel( 1 )[ 0 ] ).isWithin( 1e-6f ).of( ( float ) Math.cos( Math.PI / 4 ) );
		assertThrows( BoxRuntimeException.class, () -> Dsp.pan( in, 1.5 ) );
		assertThrows( BoxRuntimeException.class, () -> Dsp.pan( constant( 3, 1, 44100, 1f ), 0 ) );
	}

	@DisplayName( "Fades ramp the edges and leave the middle alone" )
	@Test
	void testFade() {
		// 1000 Hz sample rate so one frame is one millisecond
		AudioBuffer	in		= constant( 1, 1000, 1000, 1f );
		AudioBuffer	faded	= Dsp.fade( in, 0.1, 0.2, false );
		float[]		d		= faded.channel( 0 );
		assertThat( d[ 0 ] ).isEqualTo( 0f );
		assertThat( d[ 50 ] ).isWithin( 1e-6f ).of( 0.5f );
		assertThat( d[ 500 ] ).isEqualTo( 1f );
		assertThat( d[ 999 ] ).isEqualTo( 0f );
		// the last frame is exactly 0, so the halfway point of a 200 frame fade out is 100 frames before the end
		assertThat( d[ 899 ] ).isWithin( 1e-6f ).of( 0.5f );
		// smooth: halfway through the ramp is also half gain, but the slope starts flat
		float[] s = Dsp.fade( in, 0.1, 0, true ).channel( 0 );
		assertThat( s[ 50 ] ).isWithin( 1e-6f ).of( 0.5f );
		assertThat( s[ 5 ] ).isLessThan( d[ 5 ] );
		assertThat( in.channel( 0 )[ 0 ] ).isEqualTo( 1f );
	}

	@DisplayName( "Fades longer than the audio are clamped and invalid ones rejected" )
	@Test
	void testFadeEdges() {
		AudioBuffer in = constant( 1, 10, 1000, 1f );
		assertThat( Dsp.fade( in, 100, 100, false ).getFrames() ).isEqualTo( 10 );
		assertThat( Dsp.fade( AudioBuffer.silence( 1, 0, 44100 ), 1, 1, false ).getFrames() ).isEqualTo( 0 );
		assertThrows( BoxRuntimeException.class, () -> Dsp.fade( in, -1, 0, false ) );
		assertThrows( BoxRuntimeException.class, () -> Dsp.fade( in, Double.POSITIVE_INFINITY, 0, false ) );
	}

	@DisplayName( "Normalize brings the peak to the target level" )
	@Test
	void testNormalize() {
		AudioBuffer in = new AudioBuffer( new float[][] { { 0.1f, -0.25f, 0.05f } }, 44100 );
		assertThat( Dsp.normalize( in, 0 ).getPeak() ).isWithin( 1e-6f ).of( 1f );
		assertThat( Dsp.normalize( in, -6.0206 ).getPeak() ).isWithin( 1e-4f ).of( 0.5f );
		// quiet to loud keeps proportions
		assertThat( Dsp.normalize( in, 0 ).channel( 0 )[ 0 ] ).isWithin( 1e-6f ).of( 0.4f );
		AudioBuffer silent = AudioBuffer.silence( 1, 5, 44100 );
		assertThat( Dsp.normalize( silent, 0 ).getPeak() ).isEqualTo( 0f );
	}

	@DisplayName( "Trim cuts by seconds and clamps to the audio" )
	@Test
	void testTrim() {
		float[]		ramp	= { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
		// 1000 Hz so one frame is one millisecond
		AudioBuffer	in		= new AudioBuffer( new float[][] { ramp }, 1000 );
		AudioBuffer	cut		= Dsp.trim( in, 0.002, 0.005 );
		assertThat( cut.getFrames() ).isEqualTo( 3 );
		assertThat( cut.channel( 0 )[ 0 ] ).isEqualTo( 2f );
		assertThat( cut.channel( 0 )[ 2 ] ).isEqualTo( 4f );
		assertThat( Dsp.trim( in, 0.005, 100 ).getFrames() ).isEqualTo( 5 );
		assertThat( Dsp.trim( in, 50, 100 ).getFrames() ).isEqualTo( 0 );
		assertThat( Dsp.trim( in, 0.003, 0.003 ).getFrames() ).isEqualTo( 0 );
		assertThrows( BoxRuntimeException.class, () -> Dsp.trim( in, 0.005, 0.002 ) );
		assertThrows( BoxRuntimeException.class, () -> Dsp.trim( in, -1, 0.002 ) );
	}

	@DisplayName( "Concat joins matching buffers and refuses mismatched ones" )
	@Test
	void testConcat() {
		AudioBuffer	a		= constant( 2, 3, 44100, 0.25f );
		AudioBuffer	b		= constant( 2, 2, 44100, 0.75f );
		AudioBuffer	joined	= Dsp.concat( List.of( a, b ) );
		assertThat( joined.getFrames() ).isEqualTo( 5 );
		assertThat( joined.channel( 1 )[ 2 ] ).isEqualTo( 0.25f );
		assertThat( joined.channel( 1 )[ 3 ] ).isEqualTo( 0.75f );
		assertThat( Dsp.concat( List.of( a ) ).getFrames() ).isEqualTo( 3 );
		assertThrows( BoxRuntimeException.class, () -> Dsp.concat( List.of() ) );
		assertThrows( BoxRuntimeException.class, () -> Dsp.concat( List.of( a, constant( 1, 2, 44100, 0f ) ) ) );
		assertThrows( BoxRuntimeException.class, () -> Dsp.concat( List.of( a, constant( 2, 2, 22050, 0f ) ) ) );
	}

	@DisplayName( "Reverse flips each channel" )
	@Test
	void testReverse() {
		AudioBuffer	in	= new AudioBuffer( new float[][] { { 1, 2, 3 }, { 4, 5, 6 } }, 44100 );
		AudioBuffer	out	= Dsp.reverse( in );
		assertThat( out.channel( 0 ) ).isEqualTo( new float[] { 3, 2, 1 } );
		assertThat( out.channel( 1 ) ).isEqualTo( new float[] { 6, 5, 4 } );
		assertThat( in.channel( 0 )[ 0 ] ).isEqualTo( 1f );
	}

	@DisplayName( "Channel conversion copies up from mono and averages down" )
	@Test
	void testChannels() {
		AudioBuffer	mono	= new AudioBuffer( new float[][] { { 0.5f, -0.5f } }, 44100 );
		AudioBuffer	stereo	= Dsp.toChannels( mono, 2 );
		assertThat( stereo.getChannels() ).isEqualTo( 2 );
		assertThat( stereo.channel( 1 ) ).isEqualTo( new float[] { 0.5f, -0.5f } );
		stereo.channel( 1 )[ 0 ] = 0f;
		assertThat( mono.channel( 0 )[ 0 ] ).isEqualTo( 0.5f );
		AudioBuffer down = Dsp.toChannels( new AudioBuffer( new float[][] { { 1f, 0f }, { 0f, 1f } }, 44100 ), 1 );
		assertThat( down.channel( 0 ) ).isEqualTo( new float[] { 0.5f, 0.5f } );
		assertThat( Dsp.toChannels( mono, 1 ).channel( 0 ) ).isEqualTo( mono.channel( 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Dsp.toChannels( stereo, 6 ) );
		assertThrows( BoxRuntimeException.class, () -> Dsp.toChannels( mono, 0 ) );
	}

	@DisplayName( "Resampling keeps the length ratio and the pitch" )
	@Test
	void testResampleDown() {
		AudioBuffer	in	= sine( 1000, 1.0, 44100 );
		AudioBuffer	out	= Dsp.resample( in, 22050 );
		assertThat( out.getSampleRate() ).isEqualTo( 22050 );
		assertThat( out.getFrames() ).isEqualTo( 22050 );
		// compare with the ideal sine at the new rate, away from the edges
		AudioBuffer	ideal	= sine( 1000, 1.0, 22050 );
		double		err		= 0;
		for ( int i = 2000; i < 20000; i++ ) {
			err = Math.max( err, Math.abs( out.channel( 0 )[ i ] - ideal.channel( 0 )[ i ] ) );
		}
		assertThat( err ).isLessThan( 0.01 );
	}

	@DisplayName( "Resampling up and back down returns close to the original" )
	@Test
	void testResampleRoundTrip() {
		AudioBuffer	in	= sine( 440, 1.0, 22050 );
		AudioBuffer	up	= Dsp.resample( in, 44100 );
		assertThat( up.getFrames() ).isEqualTo( 44100 );
		AudioBuffer back = Dsp.resample( up, 22050 );
		assertThat( back.getFrames() ).isEqualTo( in.getFrames() );
		double err = 0;
		for ( int i = 2000; i < 20000; i++ ) {
			err = Math.max( err, Math.abs( back.channel( 0 )[ i ] - in.channel( 0 )[ i ] ) );
		}
		assertThat( err ).isLessThan( 0.01 );
	}

	@DisplayName( "Resampling to a rate that is not a simple ratio works and removes aliasing" )
	@Test
	void testResampleOddRatioAndAliasing() {
		AudioBuffer odd = Dsp.resample( sine( 500, 0.5, 48000 ), 44100 );
		assertThat( odd.getFrames() ).isEqualTo( 22050 );
		assertThat( rms( odd.channel( 0 ), 1000, 20000 ) ).isWithin( 0.02 ).of( Math.sqrt( 0.5 ) );
		// 15 kHz is above the new Nyquist of 8 kHz, so it must be filtered out and not folded back
		AudioBuffer high = Dsp.resample( sine( 15000, 0.5, 44100 ), 16000 );
		assertThat( rms( high.channel( 0 ), 500, 7000 ) ).isLessThan( 0.02 );
	}

	@DisplayName( "A constant signal keeps its level through resampling, edges included" )
	@Test
	void testResampleConstant() {
		AudioBuffer out = Dsp.resample( constant( 2, 4410, 44100, 0.5f ), 48000 );
		assertThat( out.getChannels() ).isEqualTo( 2 );
		for ( int ch = 0; ch < 2; ch++ ) {
			assertThat( out.channel( ch )[ 0 ] ).isWithin( 1e-3f ).of( 0.5f );
			assertThat( out.channel( ch )[ 2000 ] ).isWithin( 1e-3f ).of( 0.5f );
			assertThat( out.channel( ch )[ out.getFrames() - 1 ] ).isWithin( 1e-3f ).of( 0.5f );
		}
	}

	@DisplayName( "Resample same rate copies, bad rates and empty audio are handled" )
	@Test
	void testResampleEdges() {
		AudioBuffer in = sine( 100, 0.1, 44100 );
		assertThat( Dsp.resample( in, 44100 ) ).isNotSameInstanceAs( in );
		assertThat( Dsp.resample( in, 44100 ).channel( 0 ) ).isEqualTo( in.channel( 0 ) );
		assertThat( Dsp.resample( AudioBuffer.silence( 1, 0, 44100 ), 48000 ).getFrames() ).isEqualTo( 0 );
		assertThrows( BoxRuntimeException.class, () -> Dsp.resample( in, 10 ) );
		assertThrows( BoxRuntimeException.class, () -> Dsp.resample( in, 10_000_000 ) );
	}

	@DisplayName( "Saturate sets the peak to the level whatever the input level was, and rounds the curve" )
	@Test
	void testSaturate() {
		AudioBuffer	in	= new AudioBuffer( new float[][] { { 0.01f, 0.5f, -1f, 0.25f } }, 44100 );
		AudioBuffer	out	= Dsp.saturate( in, 2.2, 0.9 );
		assertThat( out.getPeak() ).isWithin( 1e-6f ).of( 0.9f );
		assertThat( out.channel( 0 )[ 2 ] ).isWithin( 1e-6f ).of( -0.9f );
		// quiet by comparison with the peak, the curve is almost linear, mid level samples are lifted relative to the peak
		AudioBuffer quiet = new AudioBuffer( new float[][] { { 0.001f, 0.05f, -0.1f, 0.025f } }, 44100 );
		assertThat( Dsp.saturate( quiet, 2.2, 0.9 ).channel( 0 )[ 1 ] ).isWithin( 1e-6f ).of( out.channel( 0 )[ 1 ] );
		assertThat( out.channel( 0 )[ 1 ] ).isGreaterThan( 0.45f );
		// silence stays silent, and the input is untouched
		assertThat( Dsp.saturate( AudioBuffer.silence( 1, 3, 44100 ), 2, 0.5 ).getPeak() ).isEqualTo( 0f );
		assertThat( in.channel( 0 )[ 1 ] ).isEqualTo( 0.5f );
		assertThrows( BoxRuntimeException.class, () -> Dsp.saturate( in, 0, 0.9 ) );
		assertThrows( BoxRuntimeException.class, () -> Dsp.saturate( in, 2, 1.5 ) );
		assertThrows( BoxRuntimeException.class, () -> Dsp.saturate( in, Double.NaN, 0.5 ) );
	}

}
