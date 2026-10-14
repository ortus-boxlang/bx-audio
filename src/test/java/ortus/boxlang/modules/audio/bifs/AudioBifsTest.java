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

public class AudioBifsTest extends BaseIntegrationTest {

	static Key result = new Key( "result" );

	@DisplayName( "audioSilence creates a buffer with defaults of stereo 44.1kHz" )
	@Test
	void testSilence() {
		// Read through BoxLang, not a Java cast: when the module is also loaded by another test, its classes live in a second class loader
		runtime.executeSource(
		    """
		    a = audioSilence( 2 );
		    result = [ a.getChannels(), a.getSampleRate(), a.getDurationSeconds() ];
		    """,
		    context );
		Array values = ( Array ) variables.get( result );
		assertThat( values.get( 0 ) ).isEqualTo( 2 );
		assertThat( values.get( 1 ) ).isEqualTo( 44100 );
		assertThat( values.get( 2 ) ).isEqualTo( 2.0 );
	}

	@DisplayName( "audioSilence takes channels and sampleRate, and its methods are callable from BoxLang" )
	@Test
	void testSilenceArguments() {
		runtime.executeSource( "result = audioSilence( seconds=0.5, channels=1, sampleRate=22050 ).getFrames()", context );
		assertThat( variables.get( result ) ).isEqualTo( 11025 );
	}

	@DisplayName( "audioSave then audioLoad round trips and audioInfo describes it" )
	@Test
	void testSaveLoadInfo( @TempDir Path dir ) {
		variables.put( Key.of( "target" ), dir.resolve( "nested/out.wav" ).toString() );
		runtime.executeSource(
		    """
		    a = audioSilence( 1, 2, 44100 );
		    saved = audioSave( a, target );
		    b = audioLoad( target );
		    result = audioInfo( b );
		    fromPath = audioInfo( target );
		    """,
		    context );
		IStruct info = ( IStruct ) variables.get( result );
		assertThat( info.get( Key.of( "channels" ) ) ).isEqualTo( 2 );
		assertThat( info.get( Key.of( "sampleRate" ) ) ).isEqualTo( 44100 );
		assertThat( info.get( Key.of( "frames" ) ) ).isEqualTo( 44100 );
		assertThat( info.get( Key.of( "seconds" ) ) ).isEqualTo( 1.0 );
		assertThat( info.get( Key.of( "peak" ) ) ).isEqualTo( 0.0 );
		assertThat( variables.get( Key.of( "saved" ) ) ).isEqualTo( dir.resolve( "nested/out.wav" ).toString() );
		assertThat( Files.exists( dir.resolve( "nested/out.wav" ) ) ).isTrue();
		assertThat( ( ( IStruct ) variables.get( Key.of( "fromPath" ) ) ).get( Key.of( "frames" ) ) ).isEqualTo( 44100 );
	}

	@DisplayName( "audioSave honours the bit depth" )
	@Test
	void testBitDepth( @TempDir Path dir ) throws Exception {
		Path file = dir.resolve( "deep.wav" );
		variables.put( Key.of( "target" ), file.toString() );
		runtime.executeSource( "audioSave( audioSilence( 1, 1, 8000 ), target, 24 )", context );
		assertThat( Files.size( file ) ).isEqualTo( 44 + 8000 * 3 );
	}

	@DisplayName( "Errors are clear for bad input" )
	@Test
	void testErrors( @TempDir Path dir ) {
		variables.put( Key.of( "missing" ), dir.resolve( "nope.wav" ).toString() );
		variables.put( Key.of( "target" ), dir.resolve( "x.wav" ).toString() );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioLoad( missing )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioInfo( missing )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioInfo( 42 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioSave( 'not audio', target )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioSave( audioSilence( 1 ), target, 12 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioSilence( -1 )", context ) );
	}

}
