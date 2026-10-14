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
package ortus.boxlang.modules.audio.synth;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class SynthTest {

	private static final int RATE = 44100;

	static int zeroCrossings( float[] data, int from, int to ) {
		int count = 0;
		for ( int i = from + 1; i < to; i++ ) {
			if ( ( data[ i - 1 ] < 0 ) != ( data[ i ] < 0 ) ) {
				count++;
			}
		}
		return count;
	}

	static double rms( float[] data ) {
		double sum = 0;
		for ( float v : data ) {
			sum += ( double ) v * v;
		}
		return Math.sqrt( sum / data.length );
	}

	static AudioBuffer osc( String shape, double freq ) {
		return Synth.oscillator( shape, freq, 1.0, RATE, 16, null, 0.5, 0 );
	}

	@DisplayName( "Note numbers convert to the standard equal tempered pitches" )
	@Test
	void testMidiToHz() {
		assertThat( Synth.midiToHz( 69 ) ).isWithin( 1e-9 ).of( 440.0 );
		assertThat( Synth.midiToHz( 81 ) ).isWithin( 1e-9 ).of( 880.0 );
		assertThat( Synth.midiToHz( 57 ) ).isWithin( 1e-9 ).of( 220.0 );
		assertThat( Synth.midiToHz( 60 ) ).isWithin( 1e-3 ).of( 261.6256 );
		// ten cents sharp
		assertThat( Synth.midiToHz( 69.1 ) ).isWithin( 1e-3 ).of( 440.0 * Math.pow( 2, 0.1 / 12 ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.midiToHz( Double.NaN ) );
	}

	@DisplayName( "Note names parse to MIDI numbers" )
	@ParameterizedTest
	@CsvSource( { "C4,60", "A4,69", "c4,60", "C#4,61", "Db4,61", "Bb2,46", "B3,59", "C-1,0", "G9,127", "E5,76", "F#3,54", "Cb4,59" } )
	void testNoteNames( String name, int midi ) {
		assertThat( Synth.noteNameToMidi( name ) ).isEqualTo( midi );
	}

	@DisplayName( "Bad note names are rejected with a helpful message" )
	@ParameterizedTest
	@CsvSource( { "''", "H4", "C", "C#", "Cx4", "4C", "C10", "C-2", "G#9", "hello" } )
	void testBadNoteNames( String name ) {
		assertThrows( BoxRuntimeException.class, () -> Synth.noteNameToMidi( name ) );
	}

	@DisplayName( "A sine has the right pitch, length and level" )
	@Test
	void testSine() {
		AudioBuffer sine = osc( "sine", 100 );
		assertThat( sine.getChannels() ).isEqualTo( 1 );
		assertThat( sine.getFrames() ).isEqualTo( RATE );
		assertThat( sine.getPeak() ).isWithin( 1e-3f ).of( 1f );
		assertThat( rms( sine.channel( 0 ) ) ).isWithin( 1e-3 ).of( Math.sqrt( 0.5 ) );
		assertThat( zeroCrossings( sine.channel( 0 ), 0, RATE ) ).isAnyOf( 199, 200, 201 );
		// starts at the zero crossing
		assertThat( sine.channel( 0 )[ 0 ] ).isEqualTo( 0f );
	}

	@DisplayName( "A quarter cycle phase starts a sine at its peak" )
	@Test
	void testPhase() {
		AudioBuffer shifted = Synth.oscillator( "sine", 100, 0.1, RATE, 16, null, 0.5, 0.25 );
		assertThat( shifted.channel( 0 )[ 0 ] ).isWithin( 1e-6f ).of( 1f );
	}

	@DisplayName( "Saw, square and triangle have their textbook RMS levels and the same pitch" )
	@Test
	void testBandLimitedShapes() {
		AudioBuffer	saw			= Synth.oscillator( "saw", 100, 1.0, RATE, 200, null, 0.5, 0 );
		AudioBuffer	square		= Synth.oscillator( "square", 100, 1.0, RATE, 200, null, 0.5, 0 );
		AudioBuffer	triangle	= Synth.oscillator( "triangle", 100, 1.0, RATE, 200, null, 0.5, 0 );
		// saw and triangle: 1/sqrt(3), square: 1
		assertThat( rms( saw.channel( 0 ) ) ).isWithin( 0.01 ).of( 1 / Math.sqrt( 3 ) );
		assertThat( rms( triangle.channel( 0 ) ) ).isWithin( 0.01 ).of( 1 / Math.sqrt( 3 ) );
		assertThat( rms( square.channel( 0 ) ) ).isWithin( 0.02 ).of( 1.0 );
		// a band limited square overshoots a little (Gibbs) but is near 1
		assertThat( square.getPeak() ).isWithin( 0.2f ).of( 1f );
		assertThat( triangle.getPeak() ).isWithin( 0.02f ).of( 1f );
		assertThat( zeroCrossings( square.channel( 0 ), 0, RATE ) ).isAnyOf( 199, 200, 201 );
		// the triangle rises first and the saw ramps up then drops
		assertThat( triangle.channel( 0 )[ 20 ] ).isGreaterThan( 0f );
		assertThat( saw.channel( 0 )[ 20 ] ).isGreaterThan( 0f );
	}

	@DisplayName( "Harmonics are limited so nothing is generated above the Nyquist frequency" )
	@Test
	void testNoAliasing() {
		// A 10 kHz saw at 44.1 kHz can only hold harmonics 1 (10 kHz) and 2 (20 kHz), the third would be at 30 kHz.
		// With the saw's 2/pi scaling those are amplitude 2/pi and 1/pi, so the RMS is (2/pi) * sqrt( ( 1 + 0.25 ) / 2 ).
		AudioBuffer saw = Synth.oscillator( "saw", 10000, 0.5, RATE, 50, null, 0.5, 0 );
		assertThat( rms( saw.channel( 0 ) ) ).isWithin( 0.005 ).of( ( 2 / Math.PI ) * Math.sqrt( 0.625 ) );
		// a square only has odd harmonics, so at 10 kHz it is just the fundamental
		AudioBuffer square = Synth.oscillator( "square", 10000, 0.5, RATE, 50, null, 0.5, 0 );
		assertThat( rms( square.channel( 0 ) ) ).isWithin( 0.005 ).of( ( 4 / Math.PI ) * Math.sqrt( 0.5 ) );
	}

	@DisplayName( "Fewer harmonics make a duller saw with less energy" )
	@Test
	void testHarmonicCount() {
		double	few		= rms( Synth.oscillator( "saw", 100, 0.5, RATE, 3, null, 0.5, 0 ).channel( 0 ) );
		double	many	= rms( Synth.oscillator( "saw", 100, 0.5, RATE, 100, null, 0.5, 0 ).channel( 0 ) );
		assertThat( few ).isLessThan( many );
		assertThat( Synth.oscillator( "saw", 100, 0.1, RATE, 1, null, 0.5, 0 ).getPeak() ).isWithin( 1e-2f ).of( 2f / ( float ) Math.PI );
	}

	@DisplayName( "Pulse waves spend the requested fraction of each cycle high" )
	@Test
	void testPulse() {
		for ( double width : new double[] { 0.125, 0.25, 0.5, 0.75 } ) {
			AudioBuffer	pulse	= Synth.oscillator( "pulse", 100, 1.0, RATE, 16, null, width, 0 );
			float[]		data	= pulse.channel( 0 );
			int			high	= 0;
			for ( float v : data ) {
				assertThat( Math.abs( v ) ).isEqualTo( 1f );
				if ( v > 0 ) {
					high++;
				}
			}
			assertThat( ( double ) high / data.length ).isWithin( 0.01 ).of( width );
		}
		assertThrows( BoxRuntimeException.class, () -> Synth.oscillator( "pulse", 100, 1, RATE, 16, null, 0, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.oscillator( "pulse", 100, 1, RATE, 16, null, 1, 0 ) );
	}

	@DisplayName( "Additive synthesis sums the given partials without normalising" )
	@Test
	void testAdditive() {
		double[][]	partials	= { { 1, 0.5 }, { 2, 0.25 } };
		AudioBuffer	tone		= Synth.oscillator( "additive", 100, 0.5, RATE, 16, partials, 0.5, 0 );
		assertThat( tone.getPeak() ).isAtMost( 0.75f );
		assertThat( tone.getPeak() ).isGreaterThan( 0.6f );
		// partials past Nyquist are dropped quietly
		AudioBuffer high = Synth.oscillator( "additive", 10000, 0.1, RATE, 16, new double[][] { { 1, 0.5 }, { 3, 1.0 } }, 0.5, 0 );
		assertThat( high.getPeak() ).isWithin( 1e-2f ).of( 0.5f );
		assertThrows( BoxRuntimeException.class, () -> Synth.oscillator( "additive", 100, 1, RATE, 16, null, 0.5, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.oscillator( "additive", 100, 1, RATE, 16, new double[][] { { 1 } }, 0.5, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.oscillator( "additive", 100, 1, RATE, 16, new double[][] { { 0, 1 } }, 0.5, 0 ) );
	}

	@DisplayName( "Oscillator arguments are validated" )
	@Test
	void testOscillatorErrors() {
		assertThrows( BoxRuntimeException.class, () -> Synth.oscillator( "wobble", 100, 1, RATE, 16, null, 0.5, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.oscillator( null, 100, 1, RATE, 16, null, 0.5, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.oscillator( "sine", 0, 1, RATE, 16, null, 0.5, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.oscillator( "sine", 22050, 1, RATE, 16, null, 0.5, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.oscillator( "sine", Double.NaN, 1, RATE, 16, null, 0.5, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.oscillator( "sine", 100, -1, RATE, 16, null, 0.5, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.oscillator( "sine", 100, 100000, RATE, 16, null, 0.5, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.oscillator( "sine", 100, 1, 10, 16, null, 0.5, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.oscillator( "saw", 100, 1, RATE, 0, null, 0.5, 0 ) );
		assertThat( Synth.oscillator( "SINE", 100, 0, RATE, 16, null, 0.5, 0 ).getFrames() ).isEqualTo( 0 );
	}

	@DisplayName( "A sweep starts near the start pitch and settles on the end pitch" )
	@Test
	void testSweep() {
		AudioBuffer	sweep	= Synth.sweep( 400, 100, 1.0, 20, RATE );
		float[]		data	= sweep.channel( 0 );
		assertThat( sweep.getFrames() ).isEqualTo( RATE );
		assertThat( sweep.getPeak() ).isWithin( 1e-3f ).of( 1f );
		// the first 20 ms are fast, the last half second is 100 Hz
		int	early	= zeroCrossings( data, 0, 882 );
		int	late	= zeroCrossings( data, RATE / 2, RATE );
		assertThat( late ).isAnyOf( 99, 100, 101 );
		assertThat( early ).isGreaterThan( 8 );
		// speed 0 holds the start pitch
		AudioBuffer held = Synth.sweep( 200, 50, 1.0, 0, RATE );
		assertThat( zeroCrossings( held.channel( 0 ), 0, RATE ) ).isAnyOf( 399, 400, 401 );
		assertThrows( BoxRuntimeException.class, () -> Synth.sweep( 0, 100, 1, 10, RATE ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.sweep( 100, 30000, 1, 10, RATE ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.sweep( 100, 50, 1, -1, RATE ) );
	}

	@DisplayName( "Noise is repeatable per seed, at the documented level, in every colour" )
	@Test
	void testNoiseBasics() {
		for ( String color : Synth.NOISE_COLORS ) {
			AudioBuffer a = Synth.noise( 1.0, RATE, 7, color );
			assertThat( a.getFrames() ).isEqualTo( RATE );
			assertThat( rms( a.channel( 0 ) ) ).isWithin( 1e-4 ).of( Synth.NOISE_RMS );
			assertThat( Synth.noise( 1.0, RATE, 7, color ).channel( 0 ) ).isEqualTo( a.channel( 0 ) );
			assertThat( Synth.noise( 1.0, RATE, 8, color ).channel( 0 ) ).isNotEqualTo( a.channel( 0 ) );
		}
		assertThat( Synth.noise( 0, RATE, 1, "white" ).getFrames() ).isEqualTo( 0 );
		assertThrows( BoxRuntimeException.class, () -> Synth.noise( 1, RATE, 1, "green" ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.noise( 1, RATE, 1, null ) );
	}

	@DisplayName( "Noise colours tilt the spectrum the right way" )
	@Test
	void testNoiseColours() {
		// the ratio of RMS of the first difference to the RMS is large for hissy noise and small for rumble
		double	white	= tilt( Synth.noise( 2.0, RATE, 3, "white" ) );
		double	pink	= tilt( Synth.noise( 2.0, RATE, 3, "pink" ) );
		double	brown	= tilt( Synth.noise( 2.0, RATE, 3, "brown" ) );
		double	blue	= tilt( Synth.noise( 2.0, RATE, 3, "blue" ) );
		assertThat( white ).isWithin( 0.05 ).of( Math.sqrt( 2 ) );
		assertThat( brown ).isLessThan( pink );
		assertThat( pink ).isLessThan( white );
		assertThat( white ).isLessThan( blue );
	}

	private static double tilt( AudioBuffer noise ) {
		float[]	d	= noise.channel( 0 );
		double	sum	= 0;
		for ( int i = 1; i < d.length; i++ ) {
			sum += ( d[ i ] - d[ i - 1 ] ) * ( d[ i ] - d[ i - 1 ] );
		}
		return Math.sqrt( sum / ( d.length - 1 ) ) / rms( d );
	}

	@DisplayName( "An envelope ramps up linearly and decays exponentially" )
	@Test
	void testEnvelope() {
		float[] ones = new float[ 1000 ];
		java.util.Arrays.fill( ones, 1f );
		AudioBuffer	env	= Synth.envelope( new AudioBuffer( new float[][] { ones }, 1000 ), 0.1, 0.5 );
		float[]		d	= env.channel( 0 );
		assertThat( d[ 0 ] ).isEqualTo( 0f );
		assertThat( d[ 50 ] ).isWithin( 1e-5f ).of( ( float ) ( 0.5 * Math.exp( -0.05 / 0.5 ) ) );
		assertThat( d[ 100 ] ).isWithin( 1e-5f ).of( ( float ) Math.exp( -0.1 / 0.5 ) );
		assertThat( d[ 500 ] ).isWithin( 1e-5f ).of( ( float ) Math.exp( -1.0 ) );
		// no decay holds the level, no attack starts at full level
		float[] held = Synth.envelope( new AudioBuffer( new float[][] { ones }, 1000 ), 0, 0 ).channel( 0 );
		assertThat( held[ 0 ] ).isEqualTo( 1f );
		assertThat( held[ 999 ] ).isEqualTo( 1f );
		assertThrows( BoxRuntimeException.class, () -> Synth.envelope( env, -1, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.envelope( env, 0, -1 ) );
		assertThrows( BoxRuntimeException.class, () -> Synth.envelope( env, Double.NaN, 0 ) );
	}

	@DisplayName( "Envelopes work on stereo input and leave the input alone" )
	@Test
	void testEnvelopeStereo() {
		AudioBuffer	in	= new AudioBuffer( new float[][] { { 1f, 1f }, { 0.5f, 0.5f } }, 1000 );
		AudioBuffer	out	= Synth.envelope( in, 0, 0.001 );
		assertThat( out.getChannels() ).isEqualTo( 2 );
		assertThat( out.channel( 1 )[ 0 ] ).isEqualTo( 0.5f );
		assertThat( in.channel( 0 )[ 1 ] ).isEqualTo( 1f );
	}

}
