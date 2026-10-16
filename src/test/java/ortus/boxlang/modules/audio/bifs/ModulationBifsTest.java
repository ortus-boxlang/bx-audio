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
package ortus.boxlang.modules.audio.bifs;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.BaseIntegrationTest;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * audioLfo, audioCurve, audioCurveAt, audioModulate and audioAutomate as a script calls them. The numbers are read inside the script, since the
 * module loads its own copy of the audio classes.
 */
public class ModulationBifsTest extends BaseIntegrationTest {

	private Object run( String source ) {
		runtime.executeSource( source, context );
		return variables.get( Key.of( "result" ) );
	}

	private String type( String source ) {
		return assertThrows( BoxRuntimeException.class, () -> run( source ) ).getType();
	}

	private static double number( IStruct r, String key ) {
		return ( ( Number ) r.get( Key.of( key ) ) ).doubleValue();
	}

	@DisplayName( "Curves answer audioCurveAt: an LFO swings around its center and points are joined by the chosen interpolation" )
	@Test
	void curves() {
		IStruct r = ( IStruct ) run(
		    """
		    lfo = audioLfo( shape='sine', rate=2, depth=3, center=10 );
		    line = audioCurve( points=[ { at:0, value:0 }, { at:2, value:10 } ] );
		    steps = audioCurve( points=[ { time:0, value:1 }, { time:1, value:5 } ], interpolation='step' );
		    result = { start:audioCurveAt( curve=lfo, seconds=0 ), peak:audioCurveAt( curve=lfo, seconds=0.125 ),
		        mid:audioCurveAt( curve=line, seconds=1 ), after:audioCurveAt( curve=line, seconds=9 ), held:audioCurveAt( curve=steps, seconds=0.9 ) }
		    """ );
		assertThat( number( r, "start" ) ).isWithin( 1e-9 ).of( 10 );
		assertThat( number( r, "peak" ) ).isWithin( 1e-9 ).of( 13 );
		assertThat( number( r, "mid" ) ).isWithin( 1e-9 ).of( 5 );
		assertThat( number( r, "after" ) ).isWithin( 1e-9 ).of( 10 );
		assertThat( number( r, "held" ) ).isWithin( 1e-9 ).of( 1 );
	}

	@DisplayName( "audioModulate changes volume, position, filter and pitch of a sound, and a mono sound panned becomes stereo" )
	@Test
	void modulate() {
		IStruct r = ( IStruct ) run( """
		                             tone = audioOsc( 'sine', 440, 2 );
		                             tremolo = audioModulate( audio=tone, param='gainDb', curve=audioLfo( shape='sine', rate=4, depth=6, center=-6 ) );
		                             autoPan = audioModulate( audio=tone, param='pan', curve=audioLfo( shape='sine', rate=1, depth=1 ) );
		                             wah = audioModulate( audio=audioNoise( seconds=2 ), param='cutoff', filter='bandpass', q=4,
		                                 curve=audioCurve( points=[ { at:0, value:400 }, { at:2, value:4000 } ] ) );
		                             up = audioModulate( audio=tone, param='pitch', curve=audioCurve( points=[ { at:0, value:12 } ] ) );
		                             vibrato = audioModulate( audio=tone, param='pitch', curve=audioLfo( shape='sine', rate=5, depth=0.3 ) );
		                             result = { tremoloPeak:tremolo.getPeak(), tremoloFrames:tremolo.getFrames(), toneFrames:tone.getFrames(),
		                                 panChannels:autoPan.getChannels(), wahPeak:wah.getPeak(), upSeconds:up.getDurationSeconds(),
		                                 vibratoSeconds:vibrato.getDurationSeconds() }
		                             """ );
		assertThat( number( r, "tremoloPeak" ) ).isGreaterThan( 0.2 );
		assertThat( number( r, "tremoloPeak" ) ).isLessThan( 1.0 );
		assertThat( number( r, "tremoloFrames" ) ).isEqualTo( number( r, "toneFrames" ) );
		assertThat( number( r, "panChannels" ) ).isEqualTo( 2.0 );
		assertThat( number( r, "wahPeak" ) ).isGreaterThan( 0.0 );
		assertThat( number( r, "upSeconds" ) ).isWithin( 0.01 ).of( 1.0 );
		assertThat( number( r, "vibratoSeconds" ) ).isWithin( 0.02 ).of( 2.0 );
	}

	@DisplayName( "audioAutomate moves a track's volume and position during the render" )
	@Test
	void automate() {
		IStruct r = ( IStruct ) run(
		    """
		    t = audioTimeline( bpm=120, beatsPerBar=4, sampleRate=44100, channels=2 );
		    audioAdd( timeline=t, sound=audioOsc( 'sine', 440, 2 ), at=0, track='lead' );
		    flat = audioRender( timeline=t ).getPeak();
		    audioAutomate( timeline=t, track='lead', param='gainDb', curve=audioCurve( points=[ { at:0, value:-60 }, { at:2, value:-60 } ] ) );
		    quiet = audioRender( timeline=t ).getPeak();
		    result = { flat:flat, quiet:quiet }
		    """ );
		assertThat( number( r, "flat" ) ).isGreaterThan( 0.3 );
		assertThat( number( r, "quiet" ) ).isLessThan( 0.01 );
	}

	@DisplayName( "Bad input is a typed error" )
	@Test
	void errors() {
		assertThat( type( "audioLfo( shape='wobble', rate=1, depth=1 )" ) ).isEqualTo( "bxaudio.invalidArgument" );
		assertThat( type( "audioCurve( points=[] )" ) ).isEqualTo( "bxaudio.invalidArgument" );
		assertThat( type( "audioCurve( points=[ { at:1, value:1 }, { at:1, value:2 } ] )" ) ).isEqualTo( "bxaudio.invalidArgument" );
		assertThat( type( "audioModulate( audio=audioOsc( 'sine', 440, 1 ), param='volume', curve=audioLfo( shape='sine', rate=1, depth=1 ) )" ) )
		    .isEqualTo( "bxaudio.invalidArgument" );
		assertThat( type( "audioModulate( audio=audioOsc( 'sine', 440, 1 ), param='gainDb', curve='loud' )" ) ).isEqualTo( "bxaudio.invalidArgument" );
		assertThat( type(
		    "t = audioTimeline( bpm=120, beatsPerBar=4, sampleRate=44100, channels=1 ); audioAutomate( timeline=t, track='a', param='pan', curve=audioLfo( shape='sine', rate=1, depth=1 ) )" ) )
		    .isEqualTo( "bxaudio.invalidArgument" );
	}

}
