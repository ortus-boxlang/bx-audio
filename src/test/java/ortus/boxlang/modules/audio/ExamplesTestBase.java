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

import java.nio.file.Files;
import java.nio.file.Path;

import ortus.boxlang.modules.audio.codec.WavCodec;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Shared helpers for the tests that render the scripts in the examples folder
 */
public abstract class ExamplesTestBase extends BaseIntegrationTest {

	protected static final Path EXAMPLES = Path.of( "examples" );

	/**
	 * Run an example script with settings preset, and return audioInfo() of what it wrote
	 */
	protected IStruct render( String script, IStruct overrides ) throws Exception {
		variables.put( Key.of( "overrides" ), overrides );
		runtime.executeSource( Files.readString( EXAMPLES.resolve( script ) ), context );
		variables.put( Key.of( "writtenTo" ), overrides.get( Key.of( "out" ) ) );
		runtime.executeSource( "result = audioInfo( writtenTo, true )", context );
		return ( IStruct ) variables.get( Key.of( "result" ) );
	}

	/**
	 * Render an example to a file with the given settings and read it back
	 */
	protected AudioBuffer renderAudio( Path dir, String name, String script, IStruct settings ) throws Exception {
		Path out = dir.resolve( name + ".wav" );
		settings.put( "out", out.toString() );
		render( script, settings );
		return WavCodec.read( out );
	}

}
