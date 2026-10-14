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
import org.junit.jupiter.params.provider.ValueSource;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class DrumsTest {

	private static final int RATE = 44100;

	private static AudioBuffer hit( String type ) {
		return Drums.drum( type, RATE, 1, 1, 1, 1, 1 );
	}

	@DisplayName( "Every drum type makes a non-silent mono hit that peaks at the requested level" )
	@ParameterizedTest
	@ValueSource( strings = { "kick", "snare", "hat", "openhat", "clap", "tom", "crash", "riser" } )
	void testEveryType( String type ) {
		AudioBuffer drum = hit( type );
		assertThat( drum.getChannels() ).isEqualTo( 1 );
		assertThat( drum.getFrames() ).isGreaterThan( 1000 );
		assertThat( drum.getPeak() ).isWithin( 1e-6f ).of( 1f );
		AudioBuffer quieter = Drums.drum( type, RATE, 1, 1, 1, 0.5, 1 );
		assertThat( quieter.getPeak() ).isWithin( 1e-6f ).of( 0.5f );
	}

	@DisplayName( "A drum is the same for the same seed and different for another seed" )
	@Test
	void testSeed() {
		assertThat( Drums.drum( "snare", RATE, 5, 1, 1, 1, 1 ).channel( 0 ) ).isEqualTo( Drums.drum( "snare", RATE, 5, 1, 1, 1, 1 ).channel( 0 ) );
		assertThat( Drums.drum( "snare", RATE, 6, 1, 1, 1, 1 ).channel( 0 ) ).isNotEqualTo( Drums.drum( "snare", RATE, 5, 1, 1, 1, 1 ).channel( 0 ) );
		// the kick has no noise so the seed does not matter
		assertThat( Drums.drum( "kick", RATE, 5, 1, 1, 1, 1 ).channel( 0 ) ).isEqualTo( Drums.drum( "kick", RATE, 9, 1, 1, 1, 1 ).channel( 0 ) );
	}

	@DisplayName( "The kick sweeps down in pitch and a higher pitch setting raises it" )
	@Test
	void testKickPitch() {
		AudioBuffer	kick	= hit( "kick" );
		AudioBuffer	high	= Drums.drum( "kick", RATE, 1, 1.5, 1, 1, 1 );
		float[]		d		= kick.channel( 0 );
		int			early	= SynthTest.zeroCrossings( d, 0, 1500 );
		int			late	= SynthTest.zeroCrossings( d, 4000, 5500 );
		assertThat( early ).isGreaterThan( late );
		assertThat( SynthTest.zeroCrossings( high.channel( 0 ), 4000, 5500 ) ).isGreaterThan( late );
		assertThat( kick.getFrames() ).isEqualTo( ( int ) Math.round( 0.35 * RATE ) );
	}

	@DisplayName( "A longer decay setting makes the hit longer and louder at the tail" )
	@Test
	void testDecay() {
		assertThat( Drums.drum( "kick", RATE, 1, 1, 2, 1, 1 ).getFrames() ).isEqualTo( ( int ) Math.round( 0.7 * RATE ) );
		assertThat( Drums.drum( "hat", RATE, 1, 1, 2, 1, 1 ).getFrames() ).isGreaterThan( hit( "hat" ).getFrames() );
		float[]	normal	= hit( "snare" ).channel( 0 );
		float[]	long_	= Drums.drum( "snare", RATE, 1, 1, 3, 1, 1 ).channel( 0 );
		double	tailA	= SynthTest.rms( java.util.Arrays.copyOfRange( normal, 4000, 8000 ) );
		double	tailB	= SynthTest.rms( java.util.Arrays.copyOfRange( long_, 4000, 8000 ) );
		assertThat( tailB ).isGreaterThan( tailA );
	}

	@DisplayName( "Open hats ring longer than closed hats and crashes longer still" )
	@Test
	void testLengths() {
		assertThat( hit( "openhat" ).getFrames() ).isGreaterThan( hit( "hat" ).getFrames() );
		assertThat( hit( "crash" ).getFrames() ).isGreaterThan( hit( "openhat" ).getFrames() );
		assertThat( hit( "clap" ).getFrames() ).isEqualTo( ( int ) Math.round( 0.2 * RATE ) );
	}

	@DisplayName( "A clap has two bursts a few milliseconds apart" )
	@Test
	void testClapShape() {
		float[]	d		= hit( "clap" ).channel( 0 );
		// energy right after the first burst is high, and the signal dies away well before the end
		double	first	= SynthTest.rms( java.util.Arrays.copyOfRange( d, 0, 441 ) );
		double	last	= SynthTest.rms( java.util.Arrays.copyOfRange( d, d.length - 441, d.length ) );
		assertThat( first ).isGreaterThan( last * 10 );
	}

	@DisplayName( "A riser has the requested length and swells towards the end" )
	@Test
	void testRiser() {
		AudioBuffer riser = Drums.drum( "riser", RATE, 1, 1, 1, 1, 2.0 );
		assertThat( riser.getFrames() ).isEqualTo( 2 * RATE );
		float[]	d			= riser.channel( 0 );
		double	firstHalf	= SynthTest.rms( java.util.Arrays.copyOfRange( d, 0, RATE ) );
		double	secondHalf	= SynthTest.rms( java.util.Arrays.copyOfRange( d, RATE, 2 * RATE ) );
		assertThat( secondHalf ).isGreaterThan( firstHalf * 3 );
		assertThrows( BoxRuntimeException.class, () -> Drums.drum( "riser", RATE, 1, 1, 1, 1, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Drums.drum( "riser", RATE, 1, 1, 1, 1, Double.NaN ) );
	}

	@DisplayName( "Bad drum arguments are rejected" )
	@Test
	void testErrors() {
		assertThrows( BoxRuntimeException.class, () -> Drums.drum( "cowbell", RATE, 1, 1, 1, 1, 1 ) );
		assertThrows( BoxRuntimeException.class, () -> Drums.drum( null, RATE, 1, 1, 1, 1, 1 ) );
		assertThrows( BoxRuntimeException.class, () -> Drums.drum( "kick", RATE, 1, 0, 1, 1, 1 ) );
		assertThrows( BoxRuntimeException.class, () -> Drums.drum( "kick", RATE, 1, 1, 0, 1, 1 ) );
		assertThrows( BoxRuntimeException.class, () -> Drums.drum( "kick", RATE, 1, 1, 1, 2, 1 ) );
		assertThrows( BoxRuntimeException.class, () -> Drums.drum( "kick", RATE, 1, 100, 1, 1, 1 ) );
		assertThrows( BoxRuntimeException.class, () -> Drums.drum( "kick", 10, 1, 1, 1, 1, 1 ) );
		assertThat( Drums.drum( "KICK", RATE, 1, 1, 1, 0, 1 ).getPeak() ).isEqualTo( 0f );
	}

}
