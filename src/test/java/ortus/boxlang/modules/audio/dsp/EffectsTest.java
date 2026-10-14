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

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.synth.Synth;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class EffectsTest {

	/**
	 * An impulse of 1 at frame 0 on every channel, at 1000 Hz so one frame is one millisecond
	 */
	private static AudioBuffer impulse( int channels, int frames ) {
		float[][] samples = new float[ channels ][ frames ];
		for ( float[] channel : samples ) {
			channel[ 0 ] = 1f;
		}
		return new AudioBuffer( samples, 1000 );
	}

	private static AudioBuffer constant( int channels, int frames, int rate, float value ) {
		float[][] samples = new float[ channels ][ frames ];
		for ( float[] channel : samples ) {
			Arrays.fill( channel, value );
		}
		return new AudioBuffer( samples, rate );
	}

	/**
	 * A short burst of noise on two channels at 44.1 kHz, the thing we send into a reverb
	 */
	private static AudioBuffer burst() {
		AudioBuffer noise = Synth.noise( 0.05, 44100, 11, "white" );
		return new AudioBuffer( new float[][] { noise.channel( 0 ).clone(), noise.channel( 0 ).clone() }, 44100 );
	}

	private static double rms( AudioBuffer audio, double fromSeconds, double toSeconds ) {
		int		from	= ( int ) ( fromSeconds * audio.getSampleRate() );
		int		to		= ( int ) Math.min( audio.getFrames(), toSeconds * audio.getSampleRate() );
		double	sum		= 0;
		for ( int ch = 0; ch < audio.getChannels(); ch++ ) {
			for ( int i = from; i < to; i++ ) {
				sum += audio.channel( ch )[ i ] * audio.channel( ch )[ i ];
			}
		}
		return Math.sqrt( sum / ( ( double ) audio.getChannels() * ( to - from ) ) );
	}

	@DisplayName( "Echoes land one delay time apart and fade by the feedback" )
	@Test
	void testDelayEchoes() {
		AudioBuffer	out	= Effects.delay( impulse( 1, 10 ), 0.1, 0.5, 0.5, false, null );
		float[]		d	= out.channel( 0 );
		assertThat( d[ 0 ] ).isEqualTo( 0.5f );
		assertThat( d[ 1 ] ).isEqualTo( 0f );
		assertThat( d[ 100 ] ).isEqualTo( 0.5f );
		assertThat( d[ 200 ] ).isEqualTo( 0.25f );
		assertThat( d[ 300 ] ).isEqualTo( 0.125f );
		assertThat( d[ 150 ] ).isEqualTo( 0f );
		// repeats are followed until they are 60 dB down: ceil( ln 0.001 / ln 0.5 ) = 10 repeats of 100 frames
		assertThat( out.getFrames() ).isEqualTo( 10 + 1000 );
	}

	@DisplayName( "No feedback gives a single echo, no mix gives the dry signal and the input is untouched" )
	@Test
	void testDelayEdges() {
		AudioBuffer	in		= impulse( 2, 10 );
		AudioBuffer	single	= Effects.delay( in, 0.1, 0, 1, false, null );
		assertThat( single.getFrames() ).isEqualTo( 110 );
		assertThat( single.channel( 1 )[ 100 ] ).isEqualTo( 1f );
		assertThat( single.channel( 1 )[ 105 ] ).isEqualTo( 0f );
		AudioBuffer dryOnly = Effects.delay( in, 0.1, 0.5, 0, false, null );
		assertThat( dryOnly.channel( 0 )[ 0 ] ).isEqualTo( 1f );
		assertThat( dryOnly.getPeak() ).isEqualTo( 1f );
		assertThat( dryOnly.channel( 0 )[ 100 ] ).isEqualTo( 0f );
		assertThat( in.getFrames() ).isEqualTo( 10 );
		assertThat( in.channel( 0 )[ 0 ] ).isEqualTo( 1f );
	}

	@DisplayName( "An explicit tail sets the added length and the automatic tail is capped" )
	@Test
	void testDelayTail() {
		assertThat( Effects.delay( impulse( 1, 10 ), 0.1, 0.5, 0.5, false, 0.05 ).getFrames() ).isEqualTo( 60 );
		assertThat( Effects.delay( impulse( 1, 10 ), 0.1, 0.5, 0.5, false, 0.0 ).getFrames() ).isEqualTo( 10 );
		// feedback 0.95 would take over 100 repeats of 0.5 s, the automatic tail stops at 10 s
		assertThat( Effects.delay( impulse( 1, 10 ), 0.5, 0.95, 0.5, false, null ).getFrames() ).isEqualTo( 10 + 10000 );
		assertThrows( BoxRuntimeException.class, () -> Effects.delay( impulse( 1, 10 ), 0.1, 0.5, 0.5, false, -1.0 ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.delay( impulse( 1, 10 ), 0.1, 0.5, 0.5, false, 61.0 ) );
	}

	@DisplayName( "Ping pong bounces the repeats between the sides" )
	@Test
	void testPingPong() {
		AudioBuffer out = Effects.delay( impulse( 2, 10 ), 0.1, 0.5, 1, true, null );
		assertThat( out.channel( 1 )[ 100 ] ).isEqualTo( 1f );
		assertThat( out.channel( 0 )[ 100 ] ).isEqualTo( 0f );
		assertThat( out.channel( 0 )[ 200 ] ).isEqualTo( 0.5f );
		assertThat( out.channel( 1 )[ 200 ] ).isEqualTo( 0f );
		assertThat( out.channel( 1 )[ 300 ] ).isEqualTo( 0.25f );
		assertThat( out.channel( 0 )[ 0 ] ).isEqualTo( 0f );
		assertThrows( BoxRuntimeException.class, () -> Effects.delay( impulse( 1, 10 ), 0.1, 0.5, 0.5, true, null ) );
	}

	@DisplayName( "Bad delay settings are rejected" )
	@Test
	void testDelayErrors() {
		AudioBuffer in = impulse( 1, 10 );
		assertThrows( BoxRuntimeException.class, () -> Effects.delay( in, 0, 0.5, 0.5, false, null ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.delay( in, 6, 0.5, 0.5, false, null ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.delay( in, 0.1, 1.0, 0.5, false, null ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.delay( in, 0.1, -0.1, 0.5, false, null ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.delay( in, 0.1, 0.5, 1.5, false, null ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.delay( in, Double.NaN, 0.5, 0.5, false, null ) );
	}

	@DisplayName( "Reverb adds a tail that dies away, and a bigger room rings longer" )
	@Test
	void testReverbDecay() {
		AudioBuffer	in		= burst();
		AudioBuffer	small	= Effects.reverb( in, 0.1, 0.5, 1, 6.0 );
		AudioBuffer	large	= Effects.reverb( in, 0.9, 0.5, 1, 6.0 );
		assertThat( small.getFrames() ).isEqualTo( in.getFrames() + 6 * 44100 );
		assertThat( rms( small, 0, 0.5 ) ).isGreaterThan( 0.05 );
		// the small room is silent a second in, the large one is still ringing
		assertThat( rms( small, 1.0, 1.5 ) ).isLessThan( 1e-3 );
		assertThat( rms( large, 1.0, 1.5 ) ).isGreaterThan( 1e-2 );
		assertThat( rms( large, 1.0, 1.5 ) ).isGreaterThan( rms( large, 3.0, 3.5 ) * 5 );
		// and every tail ends far below where it started
		assertThat( rms( large, 6.0, 6.5 ) ).isLessThan( rms( large, 0, 0.5 ) / 1000 );
	}

	@DisplayName( "The automatic reverb tail follows the room size and is quiet at its end" )
	@Test
	void testReverbAutoTail() {
		AudioBuffer	in		= burst();
		AudioBuffer	room	= Effects.reverb( in, 0.5, 0.5, 0.3, null );
		assertThat( room.getFrames() ).isEqualTo( in.getFrames() + ( int ) Math.round( 3.5 * 44100 ) );
		assertThat( Effects.reverb( in, 1.0, 0.5, 0.3, null ).getFrames() ).isGreaterThan( room.getFrames() );
		assertThat( Effects.reverb( in, 0.0, 0.5, 0.3, null ).getFrames() ).isLessThan( room.getFrames() );
		assertThat( rms( room, 3.0, 3.5 ) ).isLessThan( 1e-3 );
	}

	@DisplayName( "Reverb wet level is close to the input level, and mix 0 leaves the dry signal" )
	@Test
	void testReverbLevelAndMix() {
		AudioBuffer	noise		= Synth.noise( 3.0, 44100, 5, "white" );
		AudioBuffer	stereo		= new AudioBuffer( new float[][] { noise.channel( 0 ), noise.channel( 0 ).clone() }, 44100 );
		// On sustained sound a bigger room builds up more level, up to about 2.6 times the input at the largest size
		double		previous	= 0;
		for ( double room : new double[] { 0.1, 0.5, 0.9 } ) {
			double wet = rms( Effects.reverb( stereo, room, 0.5, 1, null ), 1.0, 3.0 );
			assertThat( wet ).isGreaterThan( 0.15 );
			assertThat( wet ).isLessThan( 0.8 );
			assertThat( wet ).isGreaterThan( previous );
			previous = wet;
		}
		AudioBuffer dry = Effects.reverb( stereo, 0.5, 0.5, 0, 1.0 );
		assertThat( Arrays.copyOf( dry.channel( 0 ), stereo.getFrames() ) ).isEqualTo( stereo.channel( 0 ) );
		assertThat( rms( dry, 3.0, 4.0 ) ).isEqualTo( 0.0 );
	}

	@DisplayName( "Damping removes the highs from the tail" )
	@Test
	void testReverbDamp() {
		AudioBuffer	in		= burst();
		double		bright	= brightness( Effects.reverb( in, 0.7, 0.0, 1, 3.0 ) );
		double		dark	= brightness( Effects.reverb( in, 0.7, 1.0, 1, 3.0 ) );
		assertThat( bright ).isGreaterThan( dark * 1.3 );
	}

	/**
	 * RMS of the first difference over RMS: higher means more high frequency content
	 */
	private static double brightness( AudioBuffer audio ) {
		float[]	d		= audio.channel( 0 );
		int		from	= 4410;
		int		to		= 3 * 44100;
		double	diff	= 0, level = 0;
		for ( int i = from + 1; i < to; i++ ) {
			diff	+= ( d[ i ] - d[ i - 1 ] ) * ( d[ i ] - d[ i - 1 ] );
			level	+= d[ i ] * d[ i ];
		}
		return Math.sqrt( diff / level );
	}

	@DisplayName( "Stereo reverb has different left and right tails, mono stays mono, and it stays stable" )
	@Test
	void testReverbChannelsAndStability() {
		AudioBuffer out = Effects.reverb( burst(), 0.8, 0.3, 1, 2.0 );
		assertThat( out.channel( 0 ) ).isNotEqualTo( out.channel( 1 ) );
		AudioBuffer mono = Effects.reverb( new AudioBuffer( new float[][] { burst().channel( 0 ) }, 44100 ), 0.5, 0.5, 0.5, 1.0 );
		assertThat( mono.getChannels() ).isEqualTo( 1 );
		assertThat( mono.getPeak() ).isGreaterThan( 0f );
		// the loudest settings on a long noise input neither blow up nor produce NaN
		AudioBuffer	noise	= Synth.noise( 5.0, 44100, 9, "white" );
		AudioBuffer	loud	= Effects.reverb( new AudioBuffer( new float[][] { noise.channel( 0 ) }, 44100 ), 1.0, 0.0, 1, 4.0 );
		assertThat( loud.getPeak() ).isLessThan( 20f );
		for ( float v : loud.channel( 0 ) ) {
			assertThat( Float.isNaN( v ) ).isFalse();
		}
	}

	@DisplayName( "Reverb honours other sample rates by scaling the filter lengths" )
	@Test
	void testReverbSampleRates() {
		AudioBuffer	noise	= Synth.noise( 0.05, 22050, 3, "white" );
		AudioBuffer	slow	= Effects.reverb( new AudioBuffer( new float[][] { noise.channel( 0 ) }, 22050 ), 0.5, 0.5, 1, 1.0 );
		assertThat( slow.getSampleRate() ).isEqualTo( 22050 );
		assertThat( slow.getFrames() ).isEqualTo( noise.getFrames() + 22050 );
		assertThat( rms( slow, 0.0, 0.3 ) ).isGreaterThan( 0.01 );
	}

	@DisplayName( "Bad reverb settings are rejected" )
	@Test
	void testReverbErrors() {
		AudioBuffer in = burst();
		assertThrows( BoxRuntimeException.class, () -> Effects.reverb( in, 1.1, 0.5, 0.5, null ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.reverb( in, -0.1, 0.5, 0.5, null ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.reverb( in, 0.5, 2, 0.5, null ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.reverb( in, 0.5, 0.5, 2, null ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.reverb( in, Double.NaN, 0.5, 0.5, null ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.reverb( AudioBuffer.silence( 3, 10, 44100 ), 0.5, 0.5, 0.5, null ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.reverb( in, 0.5, 0.5, 0.5, 100.0 ) );
	}

	@DisplayName( "Chorus with no depth is a fixed 20 ms delay mixed with the dry signal" )
	@Test
	void testChorusFixedDelay() {
		AudioBuffer out = Effects.chorus( constant( 1, 200, 1000, 1f ), 1, 0, 0.5 );
		assertThat( out.getFrames() ).isEqualTo( 200 );
		assertThat( out.channel( 0 )[ 10 ] ).isEqualTo( 0.5f );
		assertThat( out.channel( 0 )[ 19 ] ).isEqualTo( 0.5f );
		assertThat( out.channel( 0 )[ 20 ] ).isEqualTo( 1f );
		assertThat( out.channel( 0 )[ 199 ] ).isEqualTo( 1f );
	}

	@DisplayName( "Mix 0 returns the input, and a moving delay changes the sound without changing its level much" )
	@Test
	void testChorusMixAndDepth() {
		AudioBuffer sine = Synth.oscillator( "sine", 220, 1.0, 44100, 16, null, 0.5, 0 );
		assertThat( Effects.chorus( sine, 0.8, 0.003, 0 ).channel( 0 ) ).isEqualTo( sine.channel( 0 ) );
		AudioBuffer wet = Effects.chorus( sine, 0.8, 0.003, 0.5 );
		assertThat( wet.channel( 0 ) ).isNotEqualTo( sine.channel( 0 ) );
		assertThat( rms( wet, 0.2, 1.0 ) ).isGreaterThan( 0.3 );
		assertThat( wet.getPeak() ).isAtMost( 1.01f );
	}

	@DisplayName( "Stereo chorus swings the two sides a quarter cycle apart" )
	@Test
	void testChorusStereo() {
		AudioBuffer	mono	= Synth.oscillator( "sine", 220, 1.0, 44100, 16, null, 0.5, 0 );
		AudioBuffer	in		= new AudioBuffer( new float[][] { mono.channel( 0 ), mono.channel( 0 ).clone() }, 44100 );
		AudioBuffer	out		= Effects.chorus( in, 1.0, 0.004, 0.5 );
		assertThat( out.getChannels() ).isEqualTo( 2 );
		assertThat( out.channel( 0 ) ).isNotEqualTo( out.channel( 1 ) );
		assertThat( in.channel( 0 )[ 100 ] ).isEqualTo( in.channel( 1 )[ 100 ] );
	}

	@DisplayName( "Bad chorus settings are rejected" )
	@Test
	void testChorusErrors() {
		AudioBuffer in = constant( 1, 100, 1000, 1f );
		assertThrows( BoxRuntimeException.class, () -> Effects.chorus( in, 0, 0.003, 0.5 ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.chorus( in, 30, 0.003, 0.5 ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.chorus( in, 1, 0.02, 0.5 ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.chorus( in, 1, -1, 0.5 ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.chorus( in, 1, 0.003, 2 ) );
		assertThrows( BoxRuntimeException.class, () -> Effects.chorus( in, Double.NaN, 0.003, 0.5 ) );
	}

}
