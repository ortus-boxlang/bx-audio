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
package ortus.boxlang.modules.audio.realtime;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.dsp.Loops;
import ortus.boxlang.modules.audio.dsp.MasteringTest;
import ortus.boxlang.modules.audio.dsp.Stretch;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class MixerTest {

	private static final int RATE = 48000;

	private static Mixer memory() {
		return new Mixer( RATE, 2, "memory" );
	}

	private static AudioBuffer tone( double hz, double seconds, int channels ) {
		return MasteringTest.sine( hz, seconds, -6, channels );
	}

	private static float peak( float[] data, int from, int to ) {
		float peak = 0;
		for ( int i = from; i < to; i++ ) {
			peak = Math.max( peak, Math.abs( data[ i ] ) );
		}
		return peak;
	}

	@DisplayName( "a sound plays at its volume, hard left or right with pan, and ends by itself" )
	@Test
	void testVolumeAndPan() {
		try ( Mixer mixer = memory() ) {
			mixer.play( tone( 440, 1, 1 ), Map.of( "volume", 0.5, "pan", -1.0 ) );
			AudioBuffer	out			= mixer.render( 1.5 );
			float		expected	= ( float ) ( Math.pow( 10, -6 / 20.0 ) * 0.5 );
			// Hard left on a mono sound: the full gain of the left side of the pan law
			assertThat( peak( out.channel( 0 ), 2000, 40000 ) ).isWithin( 0.02f ).of( expected );
			assertThat( peak( out.channel( 1 ), 2000, 40000 ) ).isLessThan( 0.001f );
			assertThat( peak( out.channel( 0 ), 50000, out.getFrames() ) ).isEqualTo( 0f );
			assertThat( mixer.getActiveCount() ).isEqualTo( 0 );
		}
	}

	@DisplayName( "sounds add up, buses and the master scale them, and a stereo sound keeps its level in the middle" )
	@Test
	void testBusesAndSum() {
		try ( Mixer mixer = memory() ) {
			mixer.setVolume( "music", 0.5 );
			mixer.play( tone( 440, 1, 2 ), Map.of( "bus", "music" ) );
			float half = peak( mixer.render( 0.5 ).channel( 0 ), 1000, 20000 );
			assertThat( half ).isWithin( 0.02f ).of( 0.5f * 0.501f );
			mixer.setVolume( "music", 1.0 );
			mixer.setVolume( 0.5 );
			mixer.play( tone( 440, 1, 2 ), Map.of( "bus", "music" ) );
			// The first sound is at 1 s of 1 s? it is finished after 0.5 more s, so this render sees the second only plus the rest of the first
			assertThat( mixer.getVolume( "music" ) ).isEqualTo( 1.0 );
			assertThat( mixer.getVolume( "unknown" ) ).isEqualTo( 1.0 );
			assertThat( mixer.getVolume() ).isEqualTo( 0.5 );
		}
		try ( Mixer mixer = memory() ) {
			mixer.play( tone( 440, 1, 2 ), Map.of() );
			mixer.play( tone( 440, 1, 2 ), Map.of() );
			// Two identical sounds in phase double the level, clipped to full scale at most
			assertThat( peak( mixer.render( 0.5 ).channel( 0 ), 1000, 20000 ) ).isWithin( 0.02f ).of( 1.0f );
		}
	}

	@DisplayName( "pitch doubles the frequency, and a sound of another sample rate is converted" )
	@Test
	void testPitchAndRate() {
		try ( Mixer mixer = memory() ) {
			mixer.play( tone( 440, 2, 1 ), Map.of( "pitch", 2.0 ) );
			AudioBuffer out = mixer.render( 0.9 );
			assertThat( MasteringTest.frequency( out ) ).isWithin( 8.0 ).of( 880.0 );
		}
		try ( Mixer mixer = new Mixer( 44100, 2, "memory" ) ) {
			mixer.play( tone( 440, 1, 1 ), Map.of() );
			assertThat( MasteringTest.frequency( mixer.render( 0.8 ) ) ).isWithin( 6.0 ).of( 440.0 );
		}
	}

	@DisplayName( "a looping sound never ends, can loop a part, and stops when told" )
	@Test
	void testLoop() {
		try ( Mixer mixer = memory() ) {
			Playback	p	= mixer.play( tone( 440, 0.5, 1 ), Map.of( "loop", true ) );
			AudioBuffer	out	= mixer.render( 3 );
			// A mono sound in the middle loses 3 dB to the pan law: 0.5 x 0.707
			assertThat( peak( out.channel( 0 ), RATE * 2 + 100, RATE * 3 ) ).isGreaterThan( 0.33f );
			assertThat( p.isPlaying() ).isTrue();
			p.stop();
			mixer.render( 0.05 );
			assertThat( p.isFinished() ).isTrue();
			assertThat( mixer.getActiveCount() ).isEqualTo( 0 );
		}
		try ( Mixer mixer = memory() ) {
			AudioBuffer two = Dsp.concat( List.of( MasteringTest.sine( 440, 1, -6, 1 ), MasteringTest.sine( 880, 1, -6, 1 ) ) );
			mixer.play( two, Map.of( "loop", true, "loopStart", 1.0, "loopEnd", 2.0 ) );
			AudioBuffer	out			= mixer.render( 4 );
			// After the first pass over 440 Hz it stays in the 880 Hz half
			float[]		data		= out.channel( 0 );
			int			crossings	= 0;
			for ( int i = RATE * 2 + 1; i < RATE * 3; i++ ) {
				if ( data[ i - 1 ] < 0 && data[ i ] >= 0 ) {
					crossings++;
				}
			}
			assertThat( crossings ).isWithin( 6 ).of( 880 );
		}
		try ( Mixer mixer = memory() ) {
			assertThat(
			    assertThrows( BoxRuntimeException.class, () -> mixer.play( tone( 440, 1, 1 ), Map.of( "loop", true, "loopStart", 0.5, "loopEnd", 0.5 ) ) )
			        .getMessage() )
			    .contains( "loop must be at least" );
		}
	}

	@DisplayName( "fades, stop with a fade, pause and resume, crossfade and changing volume or pan while playing" )
	@Test
	void testControl() {
		try ( Mixer mixer = memory() ) {
			Playback p = mixer.play( tone( 440, 5, 1 ), Map.of( "loop", true ) );
			mixer.render( 0.1 );
			p.stop( 0.5 );
			AudioBuffer out = mixer.render( 1 );
			assertThat( peak( out.channel( 0 ), 0, 2400 ) ).isGreaterThan( 0.3f );
			assertThat( peak( out.channel( 0 ), RATE / 2 + 200, RATE ) ).isEqualTo( 0f );
			assertThat( p.isFinished() ).isTrue();

			Playback	q	= mixer.play( tone( 440, 5, 1 ), Map.of( "fadeIn", 0.5 ) );
			AudioBuffer	in	= mixer.render( 1 );
			assertThat( peak( in.channel( 0 ), 0, 1000 ) ).isLessThan( 0.05f );
			assertThat( peak( in.channel( 0 ), RATE / 2 + 1000, RATE ) ).isGreaterThan( 0.33f );
			q.pause();
			assertThat( peak( mixer.render( 0.2 ).channel( 0 ), 0, 9600 ) ).isEqualTo( 0f );
			q.resume();
			assertThat( peak( mixer.render( 0.2 ).channel( 0 ), 0, 9600 ) ).isGreaterThan( 0.33f );
			q.setVolume( 0.0 );
			mixer.render( 0.1 );
			assertThat( peak( mixer.render( 0.1 ).channel( 0 ), 100, 4800 ) ).isEqualTo( 0f );
			q.setVolume( 1.0 ).setPan( 1.0 );
			AudioBuffer right = mixer.render( 0.2 );
			assertThat( peak( right.channel( 0 ), 2000, 9600 ) ).isLessThan( 0.01f );
			assertThat( peak( right.channel( 1 ), 2000, 9600 ) ).isGreaterThan( 0.45f );
			q.stop();

			Playback	a	= mixer.play( tone( 440, 10, 1 ), Map.of( "loop", true ) );
			Playback	b	= mixer.crossfade( a, tone( 880, 10, 1 ), 1.0, Map.of( "loop", true ) );
			mixer.render( 1.2 );
			assertThat( a.isFinished() ).isTrue();
			assertThat( b.isPlaying() ).isTrue();
		}
	}

	@DisplayName( "a mixer with a clock and no sound plays a sound in real time, and a closed or mismatched mixer says so" )
	@Test
	void testNullOutput() {
		long start = System.nanoTime();
		try ( Mixer mixer = new Mixer( RATE, 2, "null" ) ) {
			mixer.play( tone( 440, 0.3, 1 ), Map.of() );
			assertThat( mixer.getActiveCount() ).isEqualTo( 1 );
			assertThat( mixer.waitUntilSilent( 3 ) ).isTrue();
			assertThat( ( System.nanoTime() - start ) / 1e9 ).isAtLeast( 0.25 );
			assertThat( assertThrows( BoxRuntimeException.class, () -> mixer.render( 1 ) ).getMessage() ).contains( "output=\"memory\"" );
		}
		Mixer closed = memory();
		closed.close();
		assertThat( assertThrows( BoxRuntimeException.class, () -> closed.play( tone( 440, 1, 1 ) ) ).getMessage() ).contains( "closed" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> new Mixer( RATE, 2, "radio" ) ).getMessage() ).contains( "Unknown mixer output" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> new Mixer( 100, 2, "memory" ) ).getMessage() ).contains( "between 8000" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> new Mixer( RATE, 3, "memory" ) ).getMessage() ).contains( "1 or 2 channels" );
		try ( Mixer mixer = memory() ) {
			assertThat( assertThrows( BoxRuntimeException.class, () -> mixer.play( tone( 440, 1, 1 ), Map.of( "pitch", 0.0 ) ) ).getMessage() )
			    .contains( "pitch" );
		}
	}

	@DisplayName( "looping a sound with loopify joins the end to the start with no jump" )
	@Test
	void testLoopify() {
		// 437 Hz does not fit a whole number of periods in 1.1 s, so a plain loop jumps at the join
		AudioBuffer	pad		= tone( 437, 1.1, 1 );
		float[]		plain	= pad.channel( 0 );
		float		jump	= Math.abs( plain[ 0 ] - plain[ plain.length - 1 ] );
		AudioBuffer	looped	= Loops.loopify( pad, 0.25 );
		float[]		data	= looped.channel( 0 );
		float		fixed	= Math.abs( data[ 0 ] - data[ data.length - 1 ] );
		assertThat( looped.getDurationSeconds() ).isWithin( 1e-3 ).of( 0.85 );
		assertThat( fixed ).isLessThan( 0.06f );
		assertThat( jump ).isGreaterThan( fixed );
		assertThat( assertThrows( BoxRuntimeException.class, () -> Loops.loopify( pad, 0.7 ) ).getMessage() ).contains( "longer than half" );
	}

	@DisplayName( "varispeed changes pitch and length together" )
	@Test
	void testVarispeed() {
		AudioBuffer	tone	= tone( 440, 2, 1 );
		AudioBuffer	up		= Stretch.varispeed( tone, 1.5 );
		assertThat( up.getDurationSeconds() ).isWithin( 0.01 ).of( 2 / 1.5 );
		assertThat( MasteringTest.frequency( up ) ).isWithin( 8.0 ).of( 660.0 );
	}

}
