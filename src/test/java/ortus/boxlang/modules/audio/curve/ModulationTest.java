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
package ortus.boxlang.modules.audio.curve;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.analysis.Spectrum;
import ortus.boxlang.modules.audio.dsp.Modulation;
import ortus.boxlang.modules.audio.synth.Synth;
import ortus.boxlang.modules.audio.timeline.Timeline;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

class ModulationTest {

	private static AudioBuffer sine( double hz, double seconds ) {
		return Synth.oscillator( "sine", hz, seconds, 44100, 1, null, 0.5, 0 );
	}

	private static double rms( AudioBuffer audio, int channel, double from, double to ) {
		float[]	data	= audio.channel( channel );
		int		a		= ( int ) ( from * audio.getSampleRate() );
		int		b		= Math.min( data.length, ( int ) ( to * audio.getSampleRate() ) );
		double	sum		= 0;
		for ( int i = a; i < b; i++ ) {
			sum += data[ i ] * data[ i ];
		}
		return Math.sqrt( sum / Math.max( 1, b - a ) );
	}

	private static double db( double ratio ) {
		return 20 * Math.log10( ratio );
	}

	private static double pitchOf( AudioBuffer audio, double start ) {
		Spectrum.Bins	bins	= Spectrum.fft( audio, 8192, start );
		int				best	= 1;
		for ( int i = 1; i < bins.magnitudes().length; i++ ) {
			if ( bins.magnitudes()[ i ] > bins.magnitudes()[ best ] ) {
				best = i;
			}
		}
		return bins.frequencies()[ best ];
	}

	private static String type( Runnable r ) {
		return assertThrows( AudioException.class, r::run ).getType();
	}

	@Test
	@DisplayName( "LFO shapes start where they say and swing between center - depth and center + depth" )
	void lfoShapes() {
		Curve sine = Curves.lfo( "sine", 2, 3, 10, 0, 0, 0 );
		assertThat( sine.at( 0 ) ).isWithin( 1e-9 ).of( 10 );
		assertThat( sine.at( 0.125 ) ).isWithin( 1e-9 ).of( 13 );
		assertThat( sine.at( 0.375 ) ).isWithin( 1e-9 ).of( 7 );
		Curve triangle = Curves.lfo( "triangle", 1, 1, 0, 0, 0, 0 );
		assertThat( triangle.at( 0 ) ).isWithin( 1e-9 ).of( 0 );
		assertThat( triangle.at( 0.25 ) ).isWithin( 1e-9 ).of( 1 );
		assertThat( triangle.at( 0.5 ) ).isWithin( 1e-9 ).of( 0 );
		assertThat( triangle.at( 0.75 ) ).isWithin( 1e-9 ).of( -1 );
		Curve saw = Curves.lfo( "saw", 1, 1, 0, 0, 0, 0 );
		assertThat( saw.at( 0.25 ) ).isWithin( 1e-9 ).of( 0.5 );
		assertThat( saw.at( 0.75 ) ).isWithin( 1e-9 ).of( -0.5 );
		Curve square = Curves.lfo( "square", 1, 2, 0, 0, 0, 0 );
		assertThat( square.at( 0.1 ) ).isEqualTo( 2.0 );
		assertThat( square.at( 0.6 ) ).isEqualTo( -2.0 );
		// a phase of a quarter starts a sine at its top
		assertThat( Curves.lfo( "sine", 1, 1, 0, 0.25, 0, 0 ).at( 0 ) ).isWithin( 1e-9 ).of( 1 );
	}

	@Test
	@DisplayName( "A random LFO holds a value for a cycle, differs between cycles, and repeats for the same seed" )
	void lfoRandom() {
		Curve	a	= Curves.lfo( "random", 2, 1, 0, 0, 0, 7 );
		Curve	b	= Curves.lfo( "random", 2, 1, 0, 0, 0, 7 );
		Curve	c	= Curves.lfo( "random", 2, 1, 0, 0, 0, 8 );
		assertThat( a.at( 0.1 ) ).isEqualTo( a.at( 0.4 ) );
		assertThat( a.at( 0.1 ) ).isNotEqualTo( a.at( 0.6 ) );
		assertThat( a.at( 3.3 ) ).isEqualTo( b.at( 3.3 ) );
		assertThat( a.at( 3.3 ) ).isNotEqualTo( c.at( 3.3 ) );
		for ( int i = 0; i < 200; i++ ) {
			assertThat( Math.abs( a.at( i * 0.5 + 0.1 ) ) ).isAtMost( 1.0 );
		}
	}

	@Test
	@DisplayName( "The swing of an LFO grows over the fade in time" )
	void lfoFadeIn() {
		Curve lfo = Curves.lfo( "square", 1, 4, 0, 0, 2, 0 );
		assertThat( lfo.at( 0.1 ) ).isWithin( 1e-9 ).of( 4 * 0.05 );
		assertThat( lfo.at( 1.0 ) ).isWithin( 1e-9 ).of( 4 * 0.5 );
		assertThat( lfo.at( 2.1 ) ).isWithin( 1e-9 ).of( 4 );
	}

	@Test
	@DisplayName( "A curve through points is linear, smooth or stepped between them and holds the ends" )
	void points() {
		List<Curves.Point>	list	= List.of( new Curves.Point( 4, 0 ), new Curves.Point( 0, -10 ), new Curves.Point( 2, 10 ) );
		Curve				linear	= Curves.points( list, "linear" );
		assertThat( linear.at( -1 ) ).isEqualTo( -10.0 );
		assertThat( linear.at( 1 ) ).isWithin( 1e-9 ).of( 0 );
		assertThat( linear.at( 3 ) ).isWithin( 1e-9 ).of( 5 );
		assertThat( linear.at( 99 ) ).isEqualTo( 0.0 );
		Curve smooth = Curves.points( list, "smooth" );
		assertThat( smooth.at( 1 ) ).isWithin( 1e-9 ).of( 0 );
		assertThat( smooth.at( 0.5 ) ).isLessThan( linear.at( 0.5 ) );
		assertThat( smooth.at( 1.5 ) ).isGreaterThan( linear.at( 1.5 ) );
		Curve step = Curves.points( list, "step" );
		assertThat( step.at( 1.9 ) ).isEqualTo( -10.0 );
		assertThat( step.at( 2.0 ) ).isEqualTo( 10.0 );
		assertThat( Curves.points( List.of( new Curves.Point( 1, 5 ) ), "linear" ).at( 0 ) ).isEqualTo( 5.0 );
	}

	@Test
	@DisplayName( "Bad curves are invalid arguments" )
	void curveErrors() {
		assertThat( type( () -> Curves.lfo( "wobble", 1, 1, 0, 0, 0, 0 ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> Curves.lfo( "sine", 0, 1, 0, 0, 0, 0 ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> Curves.lfo( "sine", 5000, 1, 0, 0, 0, 0 ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> Curves.lfo( "sine", 1, Double.NaN, 0, 0, 0, 0 ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> Curves.lfo( "sine", 1, 1, 0, 2, 0, 0 ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> Curves.points( List.of(), "linear" ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> Curves.points( List.of( new Curves.Point( 1, 0 ), new Curves.Point( 1, 2 ) ), "linear" ) ) )
		    .isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> Curves.points( List.of( new Curves.Point( -1, 0 ) ), "linear" ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> Curves.points( List.of( new Curves.Point( 0, 0 ) ), "wavy" ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
	}

	@Test
	@DisplayName( "Tremolo: the level swings by twice the depth in dB, and a line of points is a fade of the slope it describes" )
	void gain() {
		AudioBuffer	tone	= sine( 440, 2 );
		AudioBuffer	tremolo	= Modulation.apply( tone, "gainDb", Curves.lfo( "sine", 4, 6, -6, 0, 0, 0 ), "lowpass", 0.7071 );
		// at a quarter cycle (0.0625 s) the gain is 0 dB, at three quarters (0.1875 s) it is -12 dB
		double		top		= rms( tremolo, 0, 0.0575, 0.0675 );
		double		bottom	= rms( tremolo, 0, 0.1825, 0.1925 );
		assertThat( db( top / bottom ) ).isWithin( 1.0 ).of( 12 );
		assertThat( tremolo.getFrames() ).isEqualTo( tone.getFrames() );

		AudioBuffer fade = Modulation.apply( tone, "gainDb", Curves.points( List.of( new Curves.Point( 0, -60 ), new Curves.Point( 1, 0 ) ), "linear" ),
		    "lowpass",
		    0.7071 );
		assertThat( db( rms( fade, 0, 0.9, 0.91 ) / rms( fade, 0, 0.1, 0.11 ) ) ).isWithin( 1.5 ).of( 48 );
		// after the last point the gain stays at 0 dB
		assertThat( rms( fade, 0, 1.5, 1.6 ) ).isWithin( 0.01 ).of( rms( tone, 0, 1.5, 1.6 ) );
	}

	@Test
	@DisplayName( "Auto pan: a mono sound becomes stereo and sits on the side the curve says" )
	void pan() {
		AudioBuffer panned = Modulation.apply( sine( 440, 2 ), "pan", Curves.lfo( "sine", 1, 1, 0, 0, 0, 0 ), "lowpass", 0.7071 );
		assertThat( panned.getChannels() ).isEqualTo( 2 );
		// a quarter of a second in, the LFO is at +1: all on the right. Three quarters: all on the left.
		assertThat( rms( panned, 0, 0.24, 0.26 ) ).isLessThan( 0.02 );
		assertThat( rms( panned, 1, 0.24, 0.26 ) ).isGreaterThan( 0.3 );
		assertThat( rms( panned, 1, 0.74, 0.76 ) ).isLessThan( 0.02 );
		assertThat( rms( panned, 0, 0.74, 0.76 ) ).isGreaterThan( 0.3 );
		// where the LFO crosses zero (half a second) both sides are equal
		assertThat( db( rms( panned, 0, 0.495, 0.505 ) / rms( panned, 1, 0.495, 0.505 ) ) ).isWithin( 0.5 ).of( 0 );
	}

	@Test
	@DisplayName( "A filter sweep opens up: noise has little above 3 kHz at the start and a lot at the end, and nothing blows up with resonance" )
	void cutoff() {
		AudioBuffer	noise	= Synth.noise( 3, 44100, 1, "white" );
		Curve		sweep	= Curves.points( List.of( new Curves.Point( 0, 300 ), new Curves.Point( 3, 12000 ) ), "linear" );
		AudioBuffer	swept	= Modulation.apply( noise, "cutoff", sweep, "lowpass", 3 );
		double		early	= highEnergy( swept, 0.2 );
		double		late	= highEnergy( swept, 2.6 );
		assertThat( db( late / early ) ).isGreaterThan( 20 );
		assertThat( swept.getPeak() ).isLessThan( 10f );
		for ( float v : swept.channel( 0 ) ) {
			assertThat( Float.isFinite( v ) ).isTrue();
		}
		// a high pass sweep does the opposite
		AudioBuffer high = Modulation.apply( noise, "cutoff", Curves.points( List.of( new Curves.Point( 0, 12000 ), new Curves.Point( 3, 300 ) ), "linear" ),
		    "highpass", 0.7071 );
		assertThat( db( highEnergy( high, 2.6 ) / highEnergy( high, 0.2 ) ) ).isGreaterThan( 10 );
	}

	/**
	 * Energy between 3 and 8 kHz in a window
	 */
	private static double highEnergy( AudioBuffer audio, double start ) {
		Spectrum.Bins	bins	= Spectrum.fft( audio, 4096, start );
		double			sum		= 0;
		for ( int i = 0; i < bins.frequencies().length; i++ ) {
			if ( bins.frequencies()[ i ] > 3000 && bins.frequencies()[ i ] < 8000 ) {
				sum += bins.magnitudes()[ i ] * bins.magnitudes()[ i ];
			}
		}
		return Math.sqrt( sum );
	}

	@Test
	@DisplayName( "Pitch: a constant curve shifts the pitch and the length like a tape, a vibrato keeps both" )
	void pitch() {
		AudioBuffer	tone	= sine( 440, 2 );
		AudioBuffer	up		= Modulation.apply( tone, "pitch", Curves.points( List.of( new Curves.Point( 0, 12 ) ), "linear" ), "lowpass", 0.7071 );
		assertThat( up.getDurationSeconds() ).isWithin( 0.01 ).of( 1.0 );
		assertThat( pitchOf( up, 0.3 ) ).isWithin( 8.0 ).of( 880 );
		AudioBuffer down = Modulation.apply( tone, "pitch", Curves.points( List.of( new Curves.Point( 0, -12 ) ), "linear" ), "lowpass", 0.7071 );
		assertThat( down.getDurationSeconds() ).isWithin( 0.01 ).of( 4.0 );
		assertThat( pitchOf( down, 0.3 ) ).isWithin( 6.0 ).of( 220 );
		assertThat( up.getPeak() ).isWithin( 0.02f ).of( 1.0f );

		AudioBuffer vibrato = Modulation.apply( tone, "pitch", Curves.lfo( "sine", 5, 0.3, 0, 0, 0, 0 ), "lowpass", 0.7071 );
		assertThat( vibrato.getDurationSeconds() ).isWithin( 0.02 ).of( 2.0 );
		assertThat( pitchOf( vibrato, 0.5 ) ).isWithin( 30.0 ).of( 440 );
		assertThat( vibrato.getPeak() ).isWithin( 0.03f ).of( 1.0f );
	}

	@Test
	@DisplayName( "Unknown parameters and filters are invalid arguments, and an absurd slowdown is refused" )
	void applyErrors() {
		AudioBuffer	tone	= sine( 440, 1 );
		Curve		any		= Curves.lfo( "sine", 1, 1, 0, 0, 0, 0 );
		assertThat( type( () -> Modulation.apply( tone, "volume", any, "lowpass", 0.7 ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> Modulation.apply( tone, "cutoff", any, "comb", 0.7 ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> Modulation.apply( tone, "cutoff", any, "lowpass", 0 ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		AudioBuffer longer = sine( 440, 400 );
		assertThat( type( () -> Modulation.apply( longer, "pitch", Curves.points( List.of( new Curves.Point( 0, -36 ) ), "linear" ), "lowpass", 0.7 ) ) )
		    .isEqualTo( AudioException.TOO_LARGE );
		AudioBuffer surround = new AudioBuffer( new float[][] { new float[ 100 ], new float[ 100 ], new float[ 100 ] }, 44100 );
		assertThat( type( () -> Modulation.apply( surround, "pan", any, "lowpass", 0.7 ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
	}

	@Test
	@DisplayName( "Track automation moves a track's volume and balance during the render, and the last call for a parameter wins" )
	void timeline() {
		Timeline t = new Timeline( 120, 4, 44100, 2 );
		t.add( Modulation.apply( sine( 440, 2 ), "pan", Curves.points( List.of( new Curves.Point( 0, 0 ) ), "linear" ), "lowpass", 0.7071 ), 0, "a", 0, 0 );
		t.automate( "a", "gainDb", Curves.points( List.of( new Curves.Point( 0, 0 ), new Curves.Point( 1, -20 ), new Curves.Point( 2, -20 ) ), "linear" ) );
		AudioBuffer mix = t.render( 0 );
		// 20 dB down over the first second, then steady
		assertThat( db( rms( mix, 0, 1.45, 1.5 ) / rms( mix, 0, 0.0, 0.05 ) ) ).isWithin( 2.0 ).of( -20 + 0.9 );
		assertThat( db( rms( mix, 0, 1.9, 1.95 ) / rms( mix, 0, 1.45, 1.5 ) ) ).isWithin( 0.5 ).of( 0 );

		// the balance goes from the left to the right
		t.automate( "a", "gainDb", Curves.points( List.of( new Curves.Point( 0, 0 ) ), "linear" ) );
		t.automate( "a", "pan", Curves.points( List.of( new Curves.Point( 0, -1 ), new Curves.Point( 2, 1 ) ), "linear" ) );
		AudioBuffer balanced = t.render( 0 );
		assertThat( rms( balanced, 1, 0.0, 0.05 ) ).isLessThan( 0.1 );
		assertThat( rms( balanced, 0, 0.0, 0.05 ) ).isGreaterThan( 0.3 );
		assertThat( rms( balanced, 0, 1.9, 1.95 ) ).isLessThan( 0.1 );
		assertThat( rms( balanced, 1, 1.9, 1.95 ) ).isGreaterThan( 0.3 );
		// the gain automation of the first call was replaced by a steady 0 dB
		assertThat( rms( balanced, 0, 0.0, 0.05 ) ).isWithin( 0.05 ).of( rms( t.render( 0 ), 0, 0.0, 0.05 ) );
	}

	@Test
	@DisplayName( "Only gainDb and pan of a track can be automated, and pan only on a stereo timeline" )
	void timelineErrors() {
		Timeline	stereo	= new Timeline( 120, 4, 44100, 2 );
		Timeline	mono	= new Timeline( 120, 4, 44100, 1 );
		Curve		any		= Curves.lfo( "sine", 1, 1, 0, 0, 0, 0 );
		assertThat( type( () -> stereo.automate( "a", "cutoff", any ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> mono.automate( "a", "pan", any ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> stereo.automate( "a", "gainDb", null ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		mono.automate( "a", "gainDb", any );
	}

}
