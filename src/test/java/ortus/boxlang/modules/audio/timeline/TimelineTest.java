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
package ortus.boxlang.modules.audio.timeline;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * These use a 1000 Hz sample rate so one frame is one millisecond and the expected values are exact
 */
public class TimelineTest {

	private static final int RATE = 1000;

	private static AudioBuffer constant( int channels, int frames, int sampleRate, float value ) {
		float[][] samples = new float[ channels ][ frames ];
		for ( float[] channel : samples ) {
			java.util.Arrays.fill( channel, value );
		}
		return new AudioBuffer( samples, sampleRate );
	}

	private static Timeline stereo() {
		return new Timeline( 120, 4, RATE, 2 );
	}

	@DisplayName( "Beats and bars convert to seconds from the tempo" )
	@Test
	void testGrid() {
		Timeline t = new Timeline( 120, 4, RATE, 2 );
		assertThat( t.beat( 1 ) ).isEqualTo( 0.5 );
		assertThat( t.beat( 0 ) ).isEqualTo( 0.0 );
		assertThat( t.bar( 1 ) ).isEqualTo( 2.0 );
		assertThat( t.bar( 2.5 ) ).isEqualTo( 5.0 );
		assertThat( new Timeline( 90, 3, RATE, 1 ).bar( 1 ) ).isWithin( 1e-12 ).of( 2.0 );
		assertThat( new Timeline( 128, 4, RATE, 2 ).bar( 28 ) ).isWithin( 1e-9 ).of( 52.5 );
	}

	@DisplayName( "A sound lands on the frame for its start time" )
	@Test
	void testPlacement() {
		Timeline	t		= stereo();
		float[]		impulse	= { 1f };
		t.add( new AudioBuffer( new float[][] { impulse, impulse }, RATE ), 0.25, "drums", 0, 0 );
		AudioBuffer out = t.render( 0 );
		assertThat( out.getFrames() ).isEqualTo( 251 );
		assertThat( out.channel( 0 )[ 250 ] ).isEqualTo( 1f );
		assertThat( out.channel( 0 )[ 249 ] ).isEqualTo( 0f );
		assertThat( t.getDurationSeconds() ).isEqualTo( 0.251 );
	}

	@DisplayName( "Sounds on the same frame add together, across tracks too" )
	@Test
	void testSumming() {
		Timeline t = stereo();
		t.add( constant( 2, 10, RATE, 0.25f ), 0, "a", 0, 0 );
		t.add( constant( 2, 10, RATE, 0.25f ), 0, "a", 0, 0 );
		t.add( constant( 2, 4, RATE, 0.125f ), 0.002, "b", 0, 0 );
		AudioBuffer out = t.render( 0 );
		assertThat( out.getFrames() ).isEqualTo( 10 );
		assertThat( out.channel( 0 )[ 0 ] ).isEqualTo( 0.5f );
		assertThat( out.channel( 1 )[ 2 ] ).isEqualTo( 0.625f );
		assertThat( out.channel( 1 )[ 6 ] ).isEqualTo( 0.5f );
		assertThat( t.getTrackNames() ).containsExactly( "a", "b" ).inOrder();
	}

	@DisplayName( "Gain and pan are applied when mixing a mono sound into stereo" )
	@Test
	void testGainAndPan() {
		Timeline t = stereo();
		t.add( constant( 1, 2, RATE, 1f ), 0, "x", -6.0206, -1 );
		t.add( constant( 1, 2, RATE, 1f ), 0.01, "x", 0, 0 );
		AudioBuffer out = t.render( 0 );
		assertThat( out.channel( 0 )[ 0 ] ).isWithin( 1e-4f ).of( 0.5f );
		assertThat( out.channel( 1 )[ 0 ] ).isWithin( 1e-6f ).of( 0f );
		assertThat( out.channel( 0 )[ 10 ] ).isWithin( 1e-6f ).of( ( float ) Math.sqrt( 0.5 ) );
		assertThat( out.channel( 1 )[ 10 ] ).isWithin( 1e-6f ).of( ( float ) Math.sqrt( 0.5 ) );
	}

	@DisplayName( "Stereo sounds are balanced, and a mono timeline averages them" )
	@Test
	void testChannelMapping() {
		Timeline st = stereo();
		st.add( constant( 2, 1, RATE, 1f ), 0, "x", 0, 1 );
		AudioBuffer a = st.render( 0 );
		assertThat( a.channel( 0 )[ 0 ] ).isWithin( 1e-6f ).of( 0f );
		assertThat( a.channel( 1 )[ 0 ] ).isEqualTo( 1f );

		Timeline mono = new Timeline( 120, 4, RATE, 1 );
		mono.add( new AudioBuffer( new float[][] { { 1f }, { 0f } }, RATE ), 0, "x", 0, 0.7 );
		AudioBuffer b = mono.render( 0 );
		assertThat( b.getChannels() ).isEqualTo( 1 );
		assertThat( b.channel( 0 )[ 0 ] ).isEqualTo( 0.5f );
	}

	@DisplayName( "A sound at another sample rate is converted to the timeline rate" )
	@Test
	void testAutoResample() {
		Timeline	t		= stereo();
		// one second of constant 0.5 at 2000 Hz
		AudioBuffer	sound	= constant( 1, 2000, 2 * RATE, 0.5f );
		t.add( sound, 0, "voice", 0, -1 );
		AudioBuffer out = t.render( 0 );
		assertThat( out.getFrames() ).isEqualTo( 1000 );
		assertThat( out.channel( 0 )[ 500 ] ).isWithin( 1e-3f ).of( 0.5f );
	}

	@DisplayName( "Track gain applies at render time and the timeline can be rendered again" )
	@Test
	void testTrackGainAndRerender() {
		Timeline t = stereo();
		t.add( constant( 2, 4, RATE, 0.5f ), 0, "loud", 0, 0 );
		t.add( constant( 2, 4, RATE, 0.5f ), 0, "quiet", 0, 0 );
		t.setTrackGain( "quiet", -6.0206 );
		assertThat( t.render( 0 ).channel( 0 )[ 0 ] ).isWithin( 1e-4f ).of( 0.75f );
		t.setTrackGain( "quiet", -200 );
		assertThat( t.render( 0 ).channel( 0 )[ 0 ] ).isWithin( 1e-4f ).of( 0.5f );
	}

	@DisplayName( "The tail extends the render and an empty timeline renders nothing" )
	@Test
	void testTailAndEmpty() {
		Timeline t = stereo();
		assertThat( t.render( 0 ).getFrames() ).isEqualTo( 0 );
		assertThat( t.render( 0.5 ).getFrames() ).isEqualTo( 500 );
		t.add( constant( 2, 100, RATE, 1f ), 0, "x", 0, 0 );
		assertThat( t.render( 0.25 ).getFrames() ).isEqualTo( 350 );
	}

	@DisplayName( "A pump dips the gain at each time and recovers in a straight line" )
	@Test
	void testPump() {
		Timeline t = stereo();
		t.add( constant( 2, 600, RATE, 1f ), 0, "pad", 0, 0 );
		t.pump( "pad", List.of( 0.1, 0.5 ), -6.0206, 0.2 );
		float[] out = t.render( 0 ).channel( 0 );
		assertThat( out[ 50 ] ).isEqualTo( 1f );
		assertThat( out[ 100 ] ).isWithin( 1e-4f ).of( 0.5f );
		assertThat( out[ 200 ] ).isWithin( 1e-4f ).of( 0.75f );
		assertThat( out[ 299 ] ).isWithin( 1e-2f ).of( 1f );
		assertThat( out[ 400 ] ).isEqualTo( 1f );
		assertThat( out[ 500 ] ).isWithin( 1e-4f ).of( 0.5f );
		// the second dip is cut off by the end of the audio, frame 599 is 99 frames into its 200 frame recovery
		assertThat( out[ 599 ] ).isWithin( 1e-4f ).of( 0.7475f );
	}

	@DisplayName( "Overlapping pumps multiply" )
	@Test
	void testPumpOverlap() {
		Timeline t = stereo();
		t.add( constant( 2, 100, RATE, 1f ), 0, "pad", 0, 0 );
		t.pump( "pad", List.of( 0.0, 0.0 ), -6.0206, 0.1 );
		assertThat( t.render( 0 ).channel( 0 )[ 0 ] ).isWithin( 1e-4f ).of( 0.25f );
	}

	@DisplayName( "A sidechain duck lowers a track while the trigger is loud and releases afterwards" )
	@Test
	void testSidechainDuck() {
		Timeline t = stereo();
		t.add( constant( 2, 2000, RATE, 0.5f ), 0, "music", 0, 0 );
		t.add( constant( 2, 400, RATE, 0.5f ), 0.5, "voice", 0, 0 );
		t.duck( "music", "voice", -12, -40, 0.005, 0.1 );
		float[]	out		= t.render( 0 ).channel( 0 );
		float	full	= 0.5f;
		// before the voice: untouched
		assertThat( out[ 200 ] ).isWithin( 1e-4f ).of( full );
		// in the middle of the voice the music has dropped by 12 dB and the voice is added on top
		float ducked = full * ( float ) Math.pow( 10, -12.0 / 20 );
		assertThat( out[ 800 ] ).isWithin( 0.01f ).of( ducked + full );
		// long after the voice it is back at full level
		assertThat( out[ 1800 ] ).isWithin( 0.005f ).of( full );
		// and it was already recovering in between
		assertThat( out[ 1000 ] ).isGreaterThan( ducked + 0.02f );
	}

	@DisplayName( "A trigger below the threshold does not duck" )
	@Test
	void testSidechainThreshold() {
		Timeline t = stereo();
		t.add( constant( 2, 500, RATE, 0.5f ), 0, "music", 0, 0 );
		t.add( constant( 2, 500, RATE, 0.001f ), 0, "voice", 0, 0 );
		t.duck( "music", "voice", -12, -20, 0.005, 0.1 );
		assertThat( t.render( 0 ).channel( 0 )[ 250 ] ).isWithin( 1e-3f ).of( 0.501f );
	}

	@DisplayName( "Ducking does not change the stored tracks, so a later render without the duck is possible via a fresh timeline" )
	@Test
	void testDuckIsNonDestructive() {
		Timeline t = stereo();
		t.add( constant( 2, 100, RATE, 1f ), 0, "pad", 0, 0 );
		float first = t.render( 0 ).channel( 0 )[ 0 ];
		t.pump( "pad", List.of( 0.0 ), -6.0206, 0.05 );
		float ducked = t.render( 0 ).channel( 0 )[ 0 ];
		assertThat( first ).isEqualTo( 1f );
		assertThat( ducked ).isWithin( 1e-4f ).of( 0.5f );
		// rendering again gives the same result, nothing accumulates
		assertThat( t.render( 0 ).channel( 0 )[ 0 ] ).isEqualTo( ducked );
	}

	@DisplayName( "Bad construction arguments are rejected" )
	@Test
	void testConstructionErrors() {
		assertThrows( BoxRuntimeException.class, () -> new Timeline( 0, 4, RATE, 2 ) );
		assertThrows( BoxRuntimeException.class, () -> new Timeline( Double.NaN, 4, RATE, 2 ) );
		assertThrows( BoxRuntimeException.class, () -> new Timeline( 120, 0, RATE, 2 ) );
		assertThrows( BoxRuntimeException.class, () -> new Timeline( 120, 4, 10, 2 ) );
		assertThrows( BoxRuntimeException.class, () -> new Timeline( 120, 4, RATE, 3 ) );
	}

	@DisplayName( "Bad placement, ducking and track arguments are rejected" )
	@Test
	void testUsageErrors() {
		Timeline	t		= stereo();
		AudioBuffer	sound	= constant( 2, 4, RATE, 1f );
		assertThrows( BoxRuntimeException.class, () -> t.add( sound, -1, "x", 0, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> t.add( sound, Double.NaN, "x", 0, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> t.add( sound, 0, " ", 0, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> t.add( sound, 0, "x", 0, 2 ) );
		assertThrows( BoxRuntimeException.class, () -> t.add( sound, 0, "x", Double.POSITIVE_INFINITY, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> t.add( sound, Timeline.MAX_SECONDS + 1, "x", 0, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> t.add( AudioBuffer.silence( 3, 4, RATE ), 0, "x", 0, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> t.pump( "x", List.of( 0.0 ), 3, 0.1 ) );
		assertThrows( BoxRuntimeException.class, () -> t.pump( "x", List.of( 0.0 ), -3, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> t.pump( "x", List.of( -1.0 ), -3, 0.1 ) );
		assertThrows( BoxRuntimeException.class, () -> t.duck( "x", "nope", -6, -40, 0.01, 0.1 ) );
		t.add( sound, 0, "y", 0, 0 );
		assertThrows( BoxRuntimeException.class, () -> t.duck( "y", "y", -6, -40, 0.01, 0.1 ) );
		assertThrows( BoxRuntimeException.class, () -> t.duck( "x", "y", 6, -40, 0.01, 0.1 ) );
		assertThrows( BoxRuntimeException.class, () -> t.render( -1 ) );
		assertThrows( BoxRuntimeException.class, () -> t.render( Timeline.MAX_SECONDS + 1 ) );
		assertThrows( BoxRuntimeException.class, () -> t.setTrackGain( "x", Double.NaN ) );
	}

}
