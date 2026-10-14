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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Renders the scripts in the examples folder with short settings, so the catalog cannot silently break
 */
public class ExamplesTest extends BaseIntegrationTest {

	private static final Path EXAMPLES = Path.of( "examples" );

	/**
	 * Run an example script with settings preset, and return audioInfo() of what it wrote
	 */
	private IStruct render( String script, IStruct overrides ) throws Exception {
		variables.put( Key.of( "overrides" ), overrides );
		runtime.executeSource( Files.readString( EXAMPLES.resolve( script ) ), context );
		variables.put( Key.of( "writtenTo" ), overrides.get( Key.of( "out" ) ) );
		runtime.executeSource( "result = audioInfo( writtenTo )", context );
		return ( IStruct ) variables.get( Key.of( "result" ) );
	}

	@DisplayName( "Pop Drive renders both styles with the right length, level and format" )
	@Test
	void testPopDrive( @TempDir Path dir ) throws Exception {
		for ( String style : new String[] { "pop", "drive" } ) {
			IStruct info = render( "01-pop-drive/pop-drive.bxs",
			    Struct.of( "out", dir.resolve( style + ".wav" ).toString(), "bars", 8, "bpm", 128, "style", style ) );
			assertThat( info.get( Key.of( "channels" ) ) ).isEqualTo( 2 );
			assertThat( info.get( Key.of( "sampleRate" ) ) ).isEqualTo( 44100 );
			// 8 bars of 1.875 s plus the 2.5 s tail
			assertThat( ( Double ) info.get( Key.of( "seconds" ) ) ).isWithin( 0.01 ).of( 17.5 );
			assertThat( ( Double ) info.get( Key.of( "peak" ) ) ).isWithin( 0.005 ).of( 0.9 );
			assertThat( ( Double ) info.get( Key.of( "rms" ) ) ).isGreaterThan( 0.05 );
			assertThat( ( Double ) info.get( Key.of( "rms" ) ) ).isLessThan( 0.4 );
		}
	}

	@DisplayName( "Rendering an example twice gives the identical file" )
	@Test
	void testRepeatable( @TempDir Path dir ) throws Exception {
		Path	first	= dir.resolve( "a.wav" );
		Path	second	= dir.resolve( "b.wav" );
		render( "01-pop-drive/pop-drive.bxs", Struct.of( "out", first.toString(), "bars", 4, "bpm", 120, "style", "pop" ) );
		render( "01-pop-drive/pop-drive.bxs", Struct.of( "out", second.toString(), "bars", 4, "bpm", 120, "style", "pop" ) );
		assertThat( Files.readAllBytes( second ) ).isEqualTo( Files.readAllBytes( first ) );
	}

	@DisplayName( "The tempo changes the length" )
	@Test
	void testTempo( @TempDir Path dir ) throws Exception {
		IStruct info = render( "01-pop-drive/pop-drive.bxs", Struct.of( "out", dir.resolve( "fast.wav" ).toString(), "bars", 4, "bpm", 240, "style", "pop" ) );
		// 4 bars at 240 bpm is 4 s, plus 2.5 s
		assertThat( ( Double ) info.get( Key.of( "seconds" ) ) ).isWithin( 0.01 ).of( 6.5 );
	}

}
