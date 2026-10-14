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
import static com.google.common.truth.Truth.assertWithMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Keeps examples/explorer/catalog.json honest: it must point at real scripts, and its settings must match what those scripts accept
 */
public class ExplorerCatalogTest extends BaseIntegrationTest {

	private static final Path EXPLORER = Path.of( "examples", "explorer" );

	private Array loadCatalog() {
		variables.put( Key.of( "catalogPath" ), EXPLORER.resolve( "catalog.json" ).toAbsolutePath().toString() );
		runtime.executeSource( "result = jsonDeserialize( fileRead( catalogPath ) )", context );
		return ( Array ) variables.get( Key.of( "result" ) );
	}

	private static IStruct struct( Object value ) {
		return ( IStruct ) value;
	}

	private static String text( IStruct struct, String key ) {
		return String.valueOf( struct.get( Key.of( key ) ) );
	}

	private static double number( IStruct struct, String key ) {
		return ( ( Number ) struct.get( Key.of( key ) ) ).doubleValue();
	}

	@DisplayName( "Every catalog entry has a unique id and a script that exists" )
	@Test
	void testScriptsExist() {
		Array		catalog	= loadCatalog();
		Set<String>	ids		= new HashSet<>();
		assertThat( catalog.size() ).isAtLeast( 4 );
		for ( Object entry : catalog ) {
			IStruct piece = struct( entry );
			assertThat( ids.add( text( piece, "id" ) ) ).isTrue();
			assertThat( Files.isRegularFile( EXPLORER.resolve( text( piece, "script" ) ) ) ).isTrue();
			assertThat( text( piece, "title" ) ).isNotEmpty();
			assertThat( text( piece, "blurb" ) ).isNotEmpty();
		}
	}

	@DisplayName( "Each setting is a documented option of its script, with a default that is the script's own default and inside its range" )
	@Test
	void testSettingsMatchScripts() throws Exception {
		for ( Object entry : loadCatalog() ) {
			IStruct	piece	= struct( entry );
			String	script	= Files.readString( EXPLORER.resolve( text( piece, "script" ) ) );
			for ( Object p : ( Array ) piece.get( Key.of( "params" ) ) ) {
				IStruct	param	= struct( p );
				String	name	= text( param, "name" );
				String	where	= text( piece, "id" ) + "." + name;
				assertThat( script ).contains( "--" + name + "=" );
				if ( text( param, "type" ).equals( "text" ) ) {
					assertWithMessage( where )
					    .that( Pattern.compile( "\\b" + name + "\\s*:\\s*\"" + Pattern.quote( text( param, "value" ) ) + "\"" ).matcher( script ).find() )
					    .isTrue();
				} else if ( text( param, "type" ).equals( "select" ) ) {
					assertThat( ( ( Array ) param.get( Key.of( "options" ) ) ).contains( param.get( Key.of( "value" ) ) ) ).isTrue();
					assertWithMessage( where )
					    .that( Pattern.compile( "\\b" + name + "\\s*:\\s*\"" + Pattern.quote( text( param, "value" ) ) + "\"" ).matcher( script ).find() )
					    .isTrue();
				} else {
					double min = number( param, "min" ), max = number( param, "max" ), step = number( param, "step" ), value = number( param, "value" );
					assertThat( min ).isLessThan( max );
					assertThat( step ).isGreaterThan( 0.0 );
					assertThat( value ).isAtLeast( min );
					assertThat( value ).isAtMost( max );
					assertWithMessage( where )
					    .that( Pattern.compile( "\\b" + name + "\\s*:\\s*" + Pattern.quote( text( param, "value" ) ) + "\\b" ).matcher( script ).find() )
					    .isTrue();
				}
			}
		}
	}

	@DisplayName( "Every piece renders with the smallest value of each setting, the corner the page can ask for that is cheapest to check" )
	@Test
	void testMinimumSettingsRender( @TempDir Path dir ) throws Exception {
		for ( Object entry : loadCatalog() ) {
			IStruct piece = struct( entry );
			if ( piece.containsKey( Key.of( "needsVoice" ) ) ) {
				// needs the speech engine, which is a large download: its mix is tested with a recording instead, in ExamplesGenresTest
				continue;
			}
			IStruct overrides = Struct.of( "out", dir.resolve( text( piece, "id" ) + ".wav" ).toString() );
			for ( Object p : ( Array ) piece.get( Key.of( "params" ) ) ) {
				IStruct param = struct( p );
				overrides.put( Key.of( text( param, "name" ) ),
				    text( param, "type" ).equals( "select" ) ? ( ( Array ) param.get( Key.of( "options" ) ) ).get( 0 ) : param.get( Key.of( "min" ) ) );
			}
			variables.put( Key.of( "overrides" ), overrides );
			runtime.executeSource( Files.readString( EXPLORER.resolve( text( piece, "script" ) ) ), context );
			assertThat( Files.size( dir.resolve( text( piece, "id" ) + ".wav" ) ) ).isGreaterThan( 10_000L );
		}
	}

}
