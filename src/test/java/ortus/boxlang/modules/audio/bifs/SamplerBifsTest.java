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
 * audioSampler and audioNote as a script calls them. The numbers are read inside the script, since the module loads its own copy of the audio
 * classes.
 */
public class SamplerBifsTest extends BaseIntegrationTest {

	private Object run( String source ) {
		runtime.executeSource( source, context );
		return variables.get( Key.of( "result" ) );
	}

	private static double number( IStruct r, String key ) {
		return ( ( Number ) r.get( Key.of( key ) ) ).doubleValue();
	}

	@DisplayName( "A recording becomes an instrument that plays notes by name or number, and chords, on a timeline" )
	@Test
	void play() {
		IStruct r = ( IStruct ) run( """
		                             pluck = audioEnvelope( audio=audioOsc( 'saw', audioFreq( 'C4' ), 0.6 ), attack=0.002, decay=0.15 );
		                             keys = audioSampler( audio=pluck, rootNote='C4' );
		                             high = audioNote( sampler=keys, note='C5' );
		                             byNumber = audioNote( sampler=keys, note=72 );
		                             chord = audioNote( sampler=keys, note=[ 'C4', 'E4', 'G4' ], velocity=0.5 );
		                             held = audioNote( sampler=keys, note='A3', seconds=1, release=0.1 );
		                             t = audioTimeline( bpm=120, beatsPerBar=4, sampleRate=44100, channels=2 );
		                             audioAdd( timeline=t, sound=high, at=0, track='keys' );
		                             audioAdd( timeline=t, sound=chord, at=t.beat( 1 ), track='keys' );
		                             result = { highSeconds:high.getDurationSeconds(), highFrames:high.getFrames(), byNumberFrames:byNumber.getFrames(),
		                                 heldSeconds:held.getDurationSeconds(), chordFrames:chord.getFrames(), mixPeak:audioRender( timeline=t ).getPeak() }
		                             """ );
		assertThat( number( r, "highSeconds" ) ).isWithin( 0.01 ).of( 0.3 );
		assertThat( number( r, "byNumberFrames" ) ).isEqualTo( number( r, "highFrames" ) );
		assertThat( number( r, "heldSeconds" ) ).isWithin( 0.001 ).of( 1.1 );
		assertThat( number( r, "chordFrames" ) ).isAtLeast( 26000.0 );
		assertThat( number( r, "mixPeak" ) ).isGreaterThan( 0.1 );
	}

	@DisplayName( "A struct of recordings by note is accepted, and the nearest one is used" )
	@Test
	void zones() {
		IStruct r = ( IStruct ) run(
		    """
		    low = audioOsc( 'sine', audioFreq( 'C3' ), 0.5 );
		    mid = audioOsc( 'sine', audioFreq( 'C4' ), 0.5 );
		    keys = audioSampler( audio={ C3:low, C4:mid } );
		    result = { c4:audioNote( sampler=keys, note='C4' ).getDurationSeconds(), d4:audioNote( sampler=keys, note='D4' ).getDurationSeconds() }
		    """ );
		assertThat( number( r, "c4" ) ).isWithin( 0.001 ).of( 0.5 );
		assertThat( number( r, "d4" ) ).isLessThan( 0.5 );
	}

	@DisplayName( "Bad input is a typed error" )
	@Test
	void errors() {
		for ( String call : new String[] { "audioNote( sampler='no', note='C4' )", "audioNote( sampler=keys, note='H9' )", "audioNote( sampler=keys, note=[] )",
		    "audioNote( sampler=keys, note='C4', velocity=2 )", "audioNote( sampler=keys, note='C9' )", "audioSampler( audio=42 )",
		    "audioSampler( audio={} )" } ) {
			var e = assertThrows( BoxRuntimeException.class,
			    () -> run( "keys = audioSampler( audio=audioOsc( 'sine', 261.63, 0.3 ), rootNote='C4' ); " + call ), call );
			assertThat( e.getType() ).isEqualTo( "bxaudio.invalidArgument" );
		}
	}

}
