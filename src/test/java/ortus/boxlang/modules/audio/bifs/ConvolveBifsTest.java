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
 * audioConvolve as a script calls it. The numbers are read inside the script, since the module loads its own copy of the audio classes.
 */
public class ConvolveBifsTest extends BaseIntegrationTest {

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

	@DisplayName( "A click through a room rings on after the click and the result is as long as both" )
	@Test
	void room() {
		IStruct r = ( IStruct ) run(
		    """
		    click = audioDrum( type='hat', seconds=0.05 );
		    room = audioEnvelope( audio=audioNoise( seconds=1.5, seed=3 ), attack=0.001, release=1.4 );
		    wet = audioConvolve( audio=click, impulse=room, mix=1 );
		    dry = audioConvolve( audio=click, impulse=room, mix=0 );
		    result = { wetSeconds:wet.getDurationSeconds(), clickSeconds:click.getDurationSeconds(), roomSeconds:room.getDurationSeconds(),
		        wetTail:audioTrim( wet, 0.5, 0.9 ).getRms(), dryTail:audioTrim( dry, 0.5, 0.9 ).getRms(), channels:wet.getChannels() }
		    """ );
		assertThat( number( r, "wetSeconds" ) ).isWithin( 0.01 ).of( number( r, "clickSeconds" ) + number( r, "roomSeconds" ) );
		assertThat( number( r, "wetTail" ) ).isGreaterThan( 0.0001 );
		assertThat( number( r, "dryTail" ) ).isEqualTo( 0.0 );
	}

	@DisplayName( "Bad input is a typed error" )
	@Test
	void errors() {
		assertThat( type( "audioConvolve( audio=audioOsc( 'sine', 440, 1 ), impulse=audioOsc( 'sine', 440, 1 ), mix=2 )" ) )
		    .isEqualTo( "bxaudio.invalidArgument" );
		assertThat( type( "audioConvolve( audio=audioOsc( 'sine', 440, 1 ), impulse='room.wav' )" ) ).isEqualTo( "bxaudio.invalidArgument" );
	}

}
