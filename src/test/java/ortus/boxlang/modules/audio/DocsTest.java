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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.jr.ob.JSON;

/**
 * The documentation cannot drift from the module: every function and command is documented, every link goes somewhere, and the examples that
 * are marked to run, run.
 */
public class DocsTest extends BaseIntegrationTest {

	private static final Path DOCS = Path.of( "docs" );

	/**
	 * A file as text with Unix line endings: a Windows checkout converts them
	 */
	private static String read( Path file ) throws java.io.IOException {
		return Files.readString( file ).replace( "\r\n", "\n" );
	}

	private static List<Path> pages() throws Exception {
		try ( Stream<Path> walk = Files.walk( DOCS ) ) {
			return walk.filter( p -> p.toString().endsWith( ".md" ) ).sorted().toList();
		}
	}

	@DisplayName( "every function has a section in the reference and nothing is documented that does not exist" )
	@Test
	void testFunctionsDocumented() throws Exception {
		List<String> functions = new ArrayList<>();
		try ( Stream<Path> files = Files.list( Path.of( "src/main/java/ortus/boxlang/modules/audio/bifs" ) ) ) {
			files.map( p -> p.getFileName().toString().replace( ".java", "" ) )
			    .forEach( n -> functions.add( Character.toLowerCase( n.charAt( 0 ) ) + n.substring( 1 ) ) );
		}
		StringBuilder reference = new StringBuilder();
		try ( Stream<Path> files = Files.list( DOCS.resolve( "reference/functions" ) ) ) {
			for ( Path p : files.toList() ) {
				reference.append( read( p ) ).append( '\n' );
			}
		}
		for ( String name : functions ) {
			assertThat( reference.toString() ).contains( "\n## " + name + "\n" );
		}
		Matcher	sections	= Pattern.compile( "(?m)^## (\\w+)$" ).matcher( reference );
		int		count		= 0;
		while ( sections.find() ) {
			assertThat( functions ).contains( sections.group( 1 ) );
			count++;
		}
		assertThat( count ).isEqualTo( functions.size() );
	}

	@DisplayName( "every command of the command line has a section in the reference" )
	@Test
	void testCommandsDocumented() throws Exception {
		String	cli		= read( Path.of( "src/main/bx/cli/Cli.bx" ) );
		String	docs	= read( DOCS.resolve( "reference/cli.md" ) );
		Matcher	names	= Pattern.compile( "\\{ name : \"(\\w+)\", usage" ).matcher( cli );
		int		count	= 0;
		while ( names.find() ) {
			assertThat( docs ).contains( "\n## " + names.group( 1 ) + "\n" );
			count++;
		}
		assertThat( count ).isAtLeast( 25 );
	}

	@DisplayName( "every page has a title, the navigation lists real pages, and every relative link and image points at a file" )
	@Test
	void testLinks() throws Exception {
		@SuppressWarnings( "unchecked" )
		List<Object>	nav		= JSON.std.listFrom( read( DOCS.resolve( "nav.json" ) ) );
		List<String>	listed	= new ArrayList<>();
		collect( nav, listed );
		for ( String file : listed ) {
			assertThat( Files.exists( DOCS.resolve( file ) ) ).isTrue();
		}
		Pattern link = Pattern.compile( "!?\\[[^\\]]*\\]\\(([^)\\s]+)\\)" );
		for ( Path page : pages() ) {
			String text = read( page );
			assertThat( text ).startsWith( "---\n" );
			assertThat( text ).containsMatch( "(?m)^title: .+$" );
			assertThat( listed ).contains( DOCS.relativize( page ).toString().replace( '\\', '/' ) );
			Matcher m = link.matcher( text.replaceAll( "(?s)```.*?```", "" ) );
			while ( m.find() ) {
				String target = m.group( 1 );
				if ( target.startsWith( "http" ) || target.startsWith( "#" ) || target.startsWith( "mailto:" ) ) {
					continue;
				}
				Path file = page.getParent().resolve( target.replaceAll( "#.*$", "" ) ).normalize();
				assertWithMessage( page + " links to " + target ).that( Files.exists( file ) ).isTrue();
			}
		}
	}

	@SuppressWarnings( "unchecked" )
	private static void collect( List<Object> nav, List<String> files ) {
		for ( Object item : nav ) {
			if ( item instanceof String s ) {
				files.add( s );
			} else {
				collect( ( List<Object> ) ( ( java.util.Map<String, Object> ) item ).getOrDefault( "children", List.of() ), files );
			}
		}
	}

	@DisplayName( "the audio the examples page plays exists" )
	@Test
	void testClipsExist() throws Exception {
		String	page	= read( DOCS.resolve( "examples/index.md" ) );
		Matcher	clips	= Pattern.compile( "src=\"\\.\\./assets/audio/([\\w\\-]+\\.mp3)\"" ).matcher( page );
		int		count	= 0;
		while ( clips.find() ) {
			assertThat( Files.size( DOCS.resolve( "assets/audio" ).resolve( clips.group( 1 ) ) ) ).isGreaterThan( 10_000L );
			count++;
		}
		assertThat( count ).isEqualTo( 15 );
	}

	@DisplayName( "the examples marked to run in the guides run" )
	@Test
	void testSnippetsRun( @TempDir Path dir ) throws Exception {
		useNoDownloads();
		Pattern	snippet	= Pattern.compile( "<!-- run -->\\n```js\\n(.*?)```", Pattern.DOTALL );
		Pattern	file	= Pattern.compile( "\"((?:[\\w\\-]+/)*[\\w\\-]+\\.(?:wav|mp3|flac|json|edl|txt|srt))\"" );
		int		ran		= 0;
		for ( Path page : pages() ) {
			Matcher m = snippet.matcher( read( page ) );
			while ( m.find() ) {
				// Files the example writes go to a temporary folder, not the project
				String code = file.matcher( m.group( 1 ) ).replaceAll( r -> "\"" + dir.toString().replace( '\\', '/' ) + "/" + r.group( 1 ) + "\"" );
				try {
					runtime.executeSource( code, context );
				} catch ( RuntimeException e ) {
					throw new AssertionError( "The example in " + page + " failed: " + e.getMessage() + "\n" + m.group( 1 ), e );
				}
				ran++;
			}
		}
		assertThat( ran ).isAtLeast( 15 );
	}

}
