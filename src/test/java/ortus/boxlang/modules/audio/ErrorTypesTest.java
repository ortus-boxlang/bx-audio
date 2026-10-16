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
package ortus.boxlang.modules.audio;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.util.AudioException;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * The errors carry a stable type, so a script can tell them apart without reading the message
 */
public class ErrorTypesTest extends BaseIntegrationTest {

	private String type( String source ) {
		BoxRuntimeException e = assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( source, context ) );
		return e.getType();
	}

	@Test
	@DisplayName( "A bad value is bxaudio.invalidArgument" )
	void invalidArgument() {
		assertThat( type( "audioOsc( 'sine', -5, 1 )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( "audioInfo( 42 )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
	}

	@Test
	@DisplayName( "A missing file is bxaudio.fileNotFound and a file that is not audio is bxaudio.unsupportedFormat" )
	void files( @TempDir Path dir ) throws Exception {
		variables.put( Key.of( "missing" ), dir.resolve( "nope.wav" ).toString() );
		assertThat( type( "audioLoad( missing )" ) ).isEqualTo( AudioException.FILE_NOT_FOUND );
		Path text = dir.resolve( "notes.txt" );
		Files.writeString( text, "this is not audio" );
		variables.put( Key.of( "text" ), text.toString() );
		assertThat( type( "audioLoad( text )" ) ).isEqualTo( AudioException.UNSUPPORTED_FORMAT );
	}

	@Test
	@DisplayName( "A damaged WAV is bxaudio.corruptAudio" )
	void corrupt( @TempDir Path dir ) throws Exception {
		Path bad = dir.resolve( "bad.wav" );
		Files.write( bad, new byte[] { 'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'A', 'V', 'E' } );
		variables.put( Key.of( "bad" ), bad.toString() );
		assertThat( type( "audioLoad( bad )" ) ).isEqualTo( AudioException.CORRUPT_AUDIO );
	}

	@Test
	@DisplayName( "Audio that does not fit together is an invalid argument, not a file error" )
	void mismatchedFormats() {
		assertThat( type( "audioMix( [ audioOsc( 'sine', 440, 1 ), audioPan( audioOsc( 'sine', 440, 1 ), 0 ) ] )" ) )
		    .isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( "audioConcat( [ audioOsc( 'sine', 440, 1 ), audioPan( audioOsc( 'sine', 440, 1 ), 0 ) ] )" ) )
		    .isEqualTo( AudioException.INVALID_ARGUMENT );
	}

	@Test
	@DisplayName( "Speech errors name the thing that is missing" )
	void speech() {
		assertThat( type( "audioSpeak( text='Hello', voice='nobody-at-all', autoInstall=false )" ) ).isEqualTo( AudioException.VOICE_NOT_FOUND );
	}

	@Test
	@DisplayName( "The renamed dB and millisecond arguments are the only names" )
	void renamedArguments() {
		runtime.executeSource( "result = audioGain( audio=audioOsc( 'sine', 440, 0.1 ), gainDb=-6 )", context );
		assertThrows( Exception.class, () -> runtime.executeSource( "audioGain( audio=audioOsc( 'sine', 440, 0.1 ), db=-6 )", context ) );
		runtime.executeSource( "result = audioCompress( audio=audioOsc( 'sine', 440, 0.5 ), attackMs=5, releaseMs=50 )", context );
		runtime.executeSource( "result = audioLimit( audio=audioOsc( 'sine', 440, 0.5 ), ceilingDb=-3, releaseMs=50, lookAheadMs=2 )", context );
	}

}
