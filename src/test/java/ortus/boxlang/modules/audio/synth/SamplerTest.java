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

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.analysis.Spectrum;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

class SamplerTest {

	private static AudioBuffer sine( double hz, double seconds ) {
		return Synth.oscillator( "sine", hz, seconds, 44100, 1, null, 0.5, 0 );
	}

	private static Sampler single( double hz, double rootMidi, boolean loop ) {
		return new Sampler( List.of( new Sampler.Zone( rootMidi, sine( hz, 0.5 ) ) ), loop, 0, 0, 0.02 );
	}

	/**
	 * The loudest frequency in the middle of the sound
	 */
	private static double pitchOf( AudioBuffer audio ) {
		Spectrum.Bins	bins	= Spectrum.fft( audio, 8192, Math.max( 0, audio.getDurationSeconds() / 2 - 0.09 ) );
		int				best	= 1;
		for ( int i = 1; i < bins.magnitudes().length; i++ ) {
			if ( bins.magnitudes()[ i ] > bins.magnitudes()[ best ] ) {
				best = i;
			}
		}
		return bins.frequencies()[ best ];
	}

	private static double rms( AudioBuffer audio, double from, double to ) {
		float[]	data	= audio.channel( 0 );
		int		a		= ( int ) ( from * audio.getSampleRate() );
		int		b		= Math.min( data.length, ( int ) ( to * audio.getSampleRate() ) );
		double	sum		= 0;
		for ( int i = a; i < b; i++ ) {
			sum += data[ i ] * data[ i ];
		}
		return Math.sqrt( sum / Math.max( 1, b - a ) );
	}

	@Test
	@DisplayName( "A note is the recording played at the speed that gives the pitch: an octave up is twice the frequency and half as long" )
	void pitch() {
		Sampler a4 = single( 440, 69, false );
		assertThat( pitchOf( a4.note( 69, 0, 1, 0, 0 ) ) ).isWithin( 6.0 ).of( 440 );
		AudioBuffer up = a4.note( 81, 0, 1, 0, 0 );
		assertThat( pitchOf( up ) ).isWithin( 8.0 ).of( 880 );
		assertThat( up.getDurationSeconds() ).isWithin( 0.01 ).of( 0.25 );
		AudioBuffer down = a4.note( 57, 0, 1, 0, 0 );
		assertThat( pitchOf( down ) ).isWithin( 6.0 ).of( 220 );
		assertThat( down.getDurationSeconds() ).isWithin( 0.01 ).of( 1.0 );
		// a fifth up is 7 semitones, which is 1.4983 times the frequency
		assertThat( pitchOf( a4.note( 76, 0, 1, 0, 0 ) ) ).isWithin( 8.0 ).of( 440 * Math.pow( 2, 7 / 12.0 ) );
	}

	@Test
	@DisplayName( "The nearest recording is used for each note" )
	void zones() {
		Sampler		sampler	= new Sampler( List.of( new Sampler.Zone( 48, sine( 130.81, 0.5 ) ), new Sampler.Zone( 72, sine( 523.25, 0.5 ) ) ), false, 0, 0,
		    0 );
		// C4 (60) is 12 from both, the lower one is used, so it is the low recording played up an octave
		AudioBuffer	c4		= sampler.note( 60, 0, 1, 0, 0 );
		assertThat( c4.getDurationSeconds() ).isWithin( 0.01 ).of( 0.25 );
		// D5 (74) is nearest to the high recording, which is played just a little faster
		AudioBuffer d5 = sampler.note( 74, 0, 1, 0, 0 );
		assertThat( d5.getDurationSeconds() ).isWithin( 0.01 ).of( 0.5 / Math.pow( 2, 2 / 12.0 ) );
		assertThat( pitchOf( d5 ) ).isWithin( 8.0 ).of( 587.33 );
	}

	@Test
	@DisplayName( "A held note is as long as it is held plus the release, and fades in and out" )
	void envelope() {
		Sampler		sampler	= single( 440, 69, true );
		AudioBuffer	note	= sampler.note( 69, 1.0, 1, 0.01, 0.2 );
		assertThat( note.getDurationSeconds() ).isWithin( 0.001 ).of( 1.2 );
		assertThat( Math.abs( note.channel( 0 )[ 0 ] ) ).isLessThan( 0.01f );
		assertThat( Math.abs( note.channel( 0 )[ note.getFrames() - 1 ] ) ).isLessThan( 0.01f );
		assertThat( rms( note, 0.5, 0.9 ) ).isGreaterThan( 0.3 );
		assertThat( rms( note, 1.15, 1.2 ) ).isLessThan( rms( note, 0.5, 0.9 ) / 3 );
	}

	@Test
	@DisplayName( "With a loop a long note keeps sounding, without one it ends with the recording" )
	void looping() {
		AudioBuffer	looped	= single( 440, 69, true ).note( 69, 2.0, 1, 0, 0 );
		AudioBuffer	plain	= single( 440, 69, false ).note( 69, 2.0, 1, 0, 0 );
		assertThat( looped.getDurationSeconds() ).isWithin( 0.001 ).of( 2.0 );
		assertThat( rms( looped, 1.5, 1.9 ) ).isWithin( 0.05 ).of( rms( looped, 0.1, 0.4 ) );
		assertThat( pitchOf( looped ) ).isWithin( 8.0 ).of( 440 );
		assertThat( rms( plain, 1.5, 1.9 ) ).isEqualTo( 0.0 );
		// the loop has no jump at its joins: no sample step much bigger than the largest step of the sine itself
		float[]	data	= looped.channel( 0 );
		double	biggest	= 0;
		for ( int i = 1; i < data.length; i++ ) {
			biggest = Math.max( biggest, Math.abs( data[ i ] - data[ i - 1 ] ) );
		}
		assertThat( biggest ).isLessThan( 2 * Math.PI * 440 / 44100 * 1.2 );
	}

	@Test
	@DisplayName( "Velocity is the loudness" )
	void velocity() {
		Sampler sampler = single( 440, 69, false );
		assertThat( sampler.note( 69, 0, 0.5, 0, 0 ).getPeak() ).isWithin( 0.02f ).of( 0.5f );
		assertThat( sampler.note( 69, 0, 0, 0, 0 ).getPeak() ).isEqualTo( 0f );
	}

	@Test
	@DisplayName( "Fractions of a note detune it, and the result is cached" )
	void detune() {
		Sampler sampler = single( 440, 69, false );
		assertThat( pitchOf( sampler.note( 69.5, 0, 1, 0, 0 ) ) ).isWithin( 8.0 ).of( 440 * Math.pow( 2, 0.5 / 12 ) );
		assertThat( sampler.note( 70, 0, 1, 0, 0 ).getFrames() ).isEqualTo( sampler.note( 70, 0, 1, 0, 0 ).getFrames() );
	}

	@Test
	@DisplayName( "Bad settings are invalid arguments" )
	void errors() {
		Sampler sampler = single( 440, 69, false );
		// more than three octaves away from the only recording
		assertThat( assertThrows( AudioException.class, () -> sampler.note( 20, 0, 1, 0, 0 ) ).getType() ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( assertThrows( AudioException.class, () -> sampler.note( 69, 0, 1.5, 0, 0 ) ).getType() ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( assertThrows( AudioException.class, () -> sampler.note( 69, -1, 1, 0, 0 ) ).getType() ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( assertThrows( AudioException.class, () -> sampler.note( Double.NaN, 0, 1, 0, 0 ) ).getType() ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( assertThrows( AudioException.class, () -> new Sampler( List.of(), false, 0, 0, 0 ) ).getType() )
		    .isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat(
		    assertThrows( AudioException.class, () -> new Sampler( List.of( new Sampler.Zone( 60, sine( 440, 0.5 ) ) ), true, 0.4, 0.3, 0 ) ).getType() )
		    .isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( assertThrows( AudioException.class, () -> new Sampler( List.of( new Sampler.Zone( 60, sine( 440, 0.5 ) ) ), true, 0, 2.0, 0 ) ).getType() )
		    .isEqualTo( AudioException.INVALID_ARGUMENT );
		AudioBuffer stereo = ortus.boxlang.modules.audio.dsp.Dsp.pan( sine( 440, 0.5 ), 0 );
		assertThat(
		    assertThrows( AudioException.class, () -> new Sampler( List.of( new Sampler.Zone( 60, sine( 440, 0.5 ) ), new Sampler.Zone( 72, stereo ) ), false,
		        0, 0, 0 ) ).getMessage() )
		    .contains( "same channels" );
	}

}
