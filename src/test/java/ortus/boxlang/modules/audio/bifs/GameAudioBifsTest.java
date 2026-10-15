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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.BaseIntegrationTest;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * The game audio BIFs as a script calls them
 */
public class GameAudioBifsTest extends BaseIntegrationTest {

	private Object run( String source ) {
		runtime.executeSource( source, context );
		return variables.get( Key.of( "result" ) );
	}

	private String message( String source ) {
		return assertThrows( BoxRuntimeException.class, () -> run( source ) ).getMessage();
	}

	@DisplayName( "a script drives a mixer: play with options, buses, fades and render the result" )
	@Test
	void testMixerFromScript() {
		String setup = """
		               mixer = audioMixer( output="memory", buses={ music: 0.5 } );
		               tone = audioGain( audioOsc( 'sine', 440, 2 ), -6 );
		               """;
		assertThat( ( ( Number ) run( setup + "result = mixer.getVolume( 'music' )" ) ).doubleValue() ).isEqualTo( 0.5 );
		assertThat( ( ( Number ) run( setup
		    + "p = mixer.play( tone, { bus: 'music', volume: 1, pan: 0, loop: true } ); mixer.setVolume( 'music', 1 ); result = mixer.render( 1 ).getPeak()" ) )
		    .doubleValue() ).isWithin( 0.05 ).of( 0.354 );
		assertThat(
		    ( ( Number ) run( setup + "p = mixer.play( tone ); p.setVolume( 0.5 ).setPan( -1 ); result = mixer.render( 0.5 ).getPeak()" ) ).doubleValue() )
		    .isWithin( 0.03 ).of( 0.25 );
		assertThat( run(
		    setup + "p = mixer.play( tone, { loop: true } ); p.stop( 0.1 ); mixer.render( 0.5 ); result = p.isFinished() && mixer.getActiveCount() == 0" ) )
		    .isEqualTo( true );
		assertThat( run( setup + "mixer.close(); result = true" ) ).isEqualTo( true );
		assertThat( message( "m = audioMixer( output='memory' ); m.play( 'not audio' )" ) ).isNotEmpty();
		assertThat( message( "result = audioMixer( output='radio' )" ) ).contains( "Unknown mixer output" );
	}

	@DisplayName( "audioPlay with output null keeps time and returns the playback" )
	@Test
	void testPlay() {
		assertThat( run( "p = audioPlay( audio=audioOsc( 'sine', 440, 0.2 ), output='null' ); result = p.isFinished()" ) )
		    .isEqualTo( true );
		assertThat( message( "audioPlay( audio=audioOsc( 'sine', 440, 0.2 ), output='memory' )" ) ).contains( "needs a mixer that runs" );
	}

	@DisplayName( "audioLoopify shortens by the crossfade and audioVariations gives distinct, repeatable copies" )
	@Test
	void testLoopifyAndVariations() {
		assertThat( ( ( Number ) run( "result = audioLoopify( audioOsc( 'sine', 437, 1.1 ), 0.25 ).getDurationSeconds()" ) ).doubleValue() ).isWithin( 0.001 )
		    .of( 0.85 );
		Array variations = ( Array ) run( "result = audioVariations( audio=audioOsc( 'sine', 440, 1 ), count=4, pitchRange=2, seed=7 )" );
		assertThat( variations.size() ).isEqualTo( 4 );
		assertThat( ( ( Number ) run(
		    "v = audioVariations( audio=audioOsc( 'sine', 440, 1 ), count=4, pitchRange=2, seed=7 ); w = audioVariations( audio=audioOsc( 'sine', 440, 1 ), count=4, pitchRange=2, seed=7 ); result = v[ 3 ].getFrames() - w[ 3 ].getFrames()" ) )
		    .intValue() )
		    .isEqualTo( 0 );
		assertThat( ( ( Number ) run(
		    "v = audioVariations( audio=audioOsc( 'sine', 440, 1 ), count=6, pitchRange=2 ); result = v.map( ( a ) => a.getFrames() ).toList().listRemoveDuplicates().listLen()" ) )
		    .intValue() )
		    .isGreaterThan( 3 );
		assertThat( message( "result = audioVariations( audio=audioOsc( 'sine', 440, 1 ), count=0 )" ) ).contains( "count must be" );
	}

	@DisplayName( "audioSprite joins sounds, maps their times, and writes a Howler style map next to the audio" )
	@Test
	void testSprite( @TempDir Path dir ) throws Exception {
		variables.put( Key.of( "target" ), dir.resolve( "sfx/sprite.wav" ).toString() );
		String	sounds	= "s = [ jump: audioOsc( 'sine', 440, 0.5 ), coin: audioOsc( 'sine', 880, 0.25 ), boom: audioOsc( 'sine', 110, 1 ) ]; ";
		IStruct	sprite	= ( IStruct ) run( sounds + "result = audioSprite( sounds=s, gap=0.1 )" );
		IStruct	map		= ( IStruct ) sprite.get( Key.of( "sprites" ) );
		assertThat( ( ( Number ) ( ( IStruct ) map.get( Key.of( "jump" ) ) ).get( Key.of( "start" ) ) ).doubleValue() ).isEqualTo( 0.0 );
		assertThat( ( ( Number ) ( ( IStruct ) map.get( Key.of( "coin" ) ) ).get( Key.of( "start" ) ) ).doubleValue() ).isWithin( 1e-6 ).of( 0.6 );
		assertThat( ( ( Number ) ( ( IStruct ) map.get( Key.of( "boom" ) ) ).get( Key.of( "end" ) ) ).doubleValue() ).isWithin( 1e-6 ).of( 1.95 );
		assertThat( ( ( Number ) run( sounds + "result = audioSprite( sounds=s, gap=0.1 ).audio.getDurationSeconds()" ) ).doubleValue() ).isWithin( 1e-3 )
		    .of( 1.95 );
		run( sounds + "result = audioSprite( sounds=s, gap=0.1, path=target )" );
		assertThat( Files.exists( dir.resolve( "sfx/sprite.wav" ) ) ).isTrue();
		String json = Files.readString( dir.resolve( "sfx/sprite.json" ) );
		assertThat( json ).contains( "\"src\"" );
		assertThat( json ).contains( "sprite.wav" );
		assertThat( json ).containsMatch( "\"coin\"\\s*:\\s*\\[\\s*600,\\s*250\\s*\\]" );
		assertThat( message( "result = audioSprite( { a: audioOsc( 'sine', 440, 1 ), b: audioSilence( seconds=1, channels=2 ) } )" ) )
		    .contains( "same channels and sample rate" );
		assertThat( message( "result = audioSprite( {} )" ) ).contains( "at least one sound" );
	}

}
