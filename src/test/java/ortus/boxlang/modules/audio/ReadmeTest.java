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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.types.Argument;

/**
 * The function tables of the readme and the count on the home page say what the functions really take
 */
public class ReadmeTest {

	private static final Path BIFS = Path.of( "src/main/java/ortus/boxlang/modules/audio/bifs" );

	/**
	 * Each function with the names of its arguments, in order, read from the classes
	 */
	private static Map<String, List<String>> declared() throws Exception {
		Map<String, List<String>> out = new LinkedHashMap<>();
		try ( Stream<Path> files = Files.list( BIFS ) ) {
			for ( Path file : files.sorted().toList() ) {
				String			className	= file.getFileName().toString().replace( ".java", "" );
				BIF				bif			= ( BIF ) Class.forName( "ortus.boxlang.modules.audio.bifs." + className ).getDeclaredConstructor().newInstance();
				List<String>	names		= new ArrayList<>();
				for ( Argument argument : bif.getDeclaredArguments() ) {
					names.add( argument.name().getName().toLowerCase() );
				}
				out.put( Character.toLowerCase( className.charAt( 0 ) ) + className.substring( 1 ), names );
			}
		}
		return out;
	}

	/**
	 * The argument names written in a row of the readme, at the top level only
	 */
	private static List<String> written( String args ) {
		List<String>	names	= new ArrayList<>();
		StringBuilder	current	= new StringBuilder();
		int				depth	= 0;
		for ( char c : args.toCharArray() ) {
			if ( c == '[' || c == '(' || c == '{' ) {
				depth++;
			} else if ( c == ']' || c == ')' || c == '}' ) {
				depth--;
			}
			if ( c == ',' && depth == 0 ) {
				names.add( current.toString() );
				current.setLength( 0 );
			} else {
				current.append( c );
			}
		}
		if ( !current.toString().isBlank() ) {
			names.add( current.toString() );
		}
		return names.stream().map( n -> n.trim().split( "=" )[ 0 ].trim().toLowerCase() ).toList();
	}

	@Test
	@DisplayName( "every function is in a table of the readme, with the arguments it takes" )
	void readmeTables() throws Exception {
		Map<String, List<String>>	declared	= declared();
		Map<String, List<String>>	rows		= new LinkedHashMap<>();
		Matcher						matcher		= Pattern.compile( "^\\| `((?:audio|video)\\w+)\\(([^`]*)\\)` \\|", Pattern.MULTILINE )
		    .matcher( Files.readString( Path.of( "readme.md" ) ).replace( "\r\n", "\n" ) );
		while ( matcher.find() ) {
			rows.put( matcher.group( 1 ), written( matcher.group( 2 ).trim() ) );
		}
		for ( Map.Entry<String, List<String>> function : declared.entrySet() ) {
			assertThat( rows ).containsKey( function.getKey() );
			assertWithMessage( "arguments of " + function.getKey() + " in the readme" ).that( rows.get( function.getKey() ) ).isEqualTo( function.getValue() );
		}
		assertThat( rows.keySet() ).isEqualTo( declared.keySet() );
	}

	@Test
	@DisplayName( "the home page gives the number of functions there are" )
	void homePageCount() throws Exception {
		String page = Files.readString( Path.of( "docs", "index.md" ) );
		assertThat( page ).contains( "All " + declared().size() + " functions" );
	}

	private static final String[] NUMBERS = { "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
	    "thirteen",
	    "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty", "twenty-one", "twenty-two", "twenty-three", "twenty-four",
	    "twenty-five" };

	private static String read( String file ) throws Exception {
		return Files.readString( Path.of( file ) ).replace( "\r\n", "\n" );
	}

	@Test
	@DisplayName( "every command of the command line is in the readme's table" )
	void readmeCommands() throws Exception {
		String	readme		= read( "readme.md" );
		Matcher	commands	= Pattern.compile( "\\{ name : \"(\\w+)\", usage" ).matcher( read( "src/main/bx/cli/Cli.bx" ) );
		int		count		= 0;
		while ( commands.find() ) {
			String name = commands.group( 1 );
			if ( name.equals( "help" ) ) {
				continue;
			}
			count++;
			assertWithMessage( "command " + name ).that( readme ).containsMatch( "\\`" + name + "[ \\`]" );
		}
		assertThat( count ).isGreaterThan( 25 );
	}

	@Test
	@DisplayName( "the pages that count the example pieces and the voices give the numbers there are" )
	void pageCounts() throws Exception {
		int		pieces	= ( int ) Pattern.compile( "\"id\" *:" ).matcher( read( "examples/explorer/catalog.json" ) ).results().count();
		String	word	= NUMBERS[ pieces ];
		String	capital	= Character.toUpperCase( word.charAt( 0 ) ) + word.substring( 1 );
		assertThat( pieces ).isAtLeast( 16 );
		assertThat( read( "docs/getting-started.md" ) ).contains( "ships " + word + " complete pieces" );
		assertThat( read( "docs/index.md" ) ).contains( capital + " complete pieces" );
		assertThat( read( "docs/roadmap.md" ) ).contains( "catalog of " + word + " example scripts" );
		assertThat( read( "docs/try-it.md" ) ).contains( "# the " + pieces + " pieces" );
		assertThat( read( "docs/examples/index.md" ) ).contains( "summary: " + capital + " complete pieces" );
		assertThat( read( "docs/.theme/home.bxm" ) ).contains( "<b>" + pieces + "</b> pieces made from code" );
		assertThat( read( "docs/.theme/home.bxm" ) ).contains( "<b>" + declared().size() + "</b> functions" );
		assertThat( read( "docs/.theme/home.bxm" ) ).contains( capital + " complete pieces rendered" );
	}

}
