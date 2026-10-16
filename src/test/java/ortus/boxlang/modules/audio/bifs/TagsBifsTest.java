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
 * audioTags as a script calls it
 */
public class TagsBifsTest extends BaseIntegrationTest {

	private Object run( String source ) {
		runtime.executeSource( source, context );
		return variables.get( Key.of( "result" ) );
	}

	private String type( String source ) {
		return assertThrows( BoxRuntimeException.class, () -> run( source ) ).getType();
	}

	@DisplayName( "Reading gives every tag, the cover and the chapters with a fixed shape, empty when the file has none" )
	@Test
	void readShape( @TempDir Path dir ) {
		variables.put( Key.of( "file" ), dir.resolve( "plain.mp3" ).toString() );
		IStruct r = ( IStruct ) run( "audioSave( audio=audioOsc( 'sine', 440, 1 ), path=file ); result = audioTags( path=file )" );
		assertThat( r.keySet().stream().map( Key::getName ).toList() )
		    .containsExactly( "format", "title", "artist", "album", "albumArtist", "composer", "genre",
		        "year", "track", "disc", "comment", "cover", "chapters" )
		    .inOrder();
		assertThat( r.get( Key.of( "format" ) ) ).isEqualTo( "mp3" );
		assertThat( r.get( Key.of( "title" ) ) ).isEqualTo( "" );
		assertThat( r.get( Key.of( "cover" ) ) ).isNull();
		assertThat( ( ( Array ) r.get( Key.of( "chapters" ) ) ).size() ).isEqualTo( 0 );
	}

	@DisplayName( "Writing returns what was stored and ignored, and reading gives it back, with markers accepted as chapters" )
	@Test
	void writeAndRead( @TempDir Path dir ) throws Exception {
		variables.put( Key.of( "file" ), dir.resolve( "ep.flac" ).toString() );
		IStruct	r		= ( IStruct ) run( """
		                                   audioSave( audio=audioOsc( 'sine', 440, 3 ), path=file );
		                                   written = audioTags( path=file, tags={ title:'Episode 1', year:2026, track:3,
		                                       chapters:[ { time:2, label:'Second' }, { start:0, title:'First' } ] } );
		                                   result = { written:written, read:audioTags( path=file ) }
		                                   """ );
		IStruct	written	= ( IStruct ) r.get( Key.of( "written" ) );
		assertThat( written.get( Key.of( "format" ) ) ).isEqualTo( "flac" );
		assertThat( ( ( Array ) written.get( Key.of( "written" ) ) ).toArray() ).asList().containsExactly( "title", "year", "track", "chapters" ).inOrder();
		assertThat( ( ( Array ) written.get( Key.of( "ignored" ) ) ).size() ).isEqualTo( 0 );
		IStruct read = ( IStruct ) r.get( Key.of( "read" ) );
		assertThat( read.get( Key.of( "title" ) ) ).isEqualTo( "Episode 1" );
		assertThat( read.get( Key.of( "year" ) ) ).isEqualTo( "2026" );
		assertThat( read.get( Key.of( "track" ) ) ).isEqualTo( "3" );
		Array chapters = ( Array ) read.get( Key.of( "chapters" ) );
		assertThat( ( ( IStruct ) chapters.get( 0 ) ).get( Key.of( "title" ) ) ).isEqualTo( "First" );
		assertThat( ( ( IStruct ) chapters.get( 1 ) ).get( Key.of( "start" ) ) ).isEqualTo( 2.0 );
	}

	@DisplayName( "A cover can be a path, binary data or a struct, and is read back with its type and size" )
	@Test
	void covers( @TempDir Path dir ) throws Exception {
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage( 4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB );
		javax.imageio.ImageIO.write( image, "png", dir.resolve( "c.png" ).toFile() );
		variables.put( Key.of( "file" ), dir.resolve( "c.mp3" ).toString() );
		variables.put( Key.of( "picture" ), dir.resolve( "c.png" ).toString() );
		IStruct	r		= ( IStruct ) run( """
		                                   audioSave( audio=audioOsc( 'sine', 440, 1 ), path=file );
		                                   audioTags( path=file, tags={ cover:picture } );
		                                   byPath = audioTags( path=file ).cover;
		                                   audioTags( path=file, tags={ cover:'' } );
		                                   removed = audioTags( path=file ).cover;
		                                   audioTags( path=file, tags={ cover:{ data:fileReadBinary( picture ) } } );
		                                   result = { byPath:byPath, removed:removed, byData:audioTags( path=file ).cover }
		                                   """ );
		IStruct	byPath	= ( IStruct ) r.get( Key.of( "byPath" ) );
		assertThat( byPath.get( Key.of( "mimeType" ) ) ).isEqualTo( "image/png" );
		assertThat( byPath.get( Key.of( "size" ) ) ).isEqualTo( ( int ) Files.size( dir.resolve( "c.png" ) ) );
		assertThat( r.get( Key.of( "removed" ) ) ).isNull();
		assertThat( ( ( IStruct ) r.get( Key.of( "byData" ) ) ).get( Key.of( "mimeType" ) ) ).isEqualTo( "image/png" );
	}

	@DisplayName( "Bad input is a typed error and leaves the file exactly as it was" )
	@Test
	void errors( @TempDir Path dir ) throws Exception {
		Path file = dir.resolve( "e.mp3" );
		variables.put( Key.of( "file" ), file.toString() );
		variables.put( Key.of( "missing" ), dir.resolve( "nope.mp3" ).toString() );
		Files.writeString( dir.resolve( "notpic.txt" ), "this is not a picture" );
		variables.put( Key.of( "notPicture" ), dir.resolve( "notpic.txt" ).toString() );
		run( "audioSave( audio=audioOsc( 'sine', 440, 1 ), path=file ); audioTags( path=file, tags={ title:'Keep' } )" );
		byte[] before = Files.readAllBytes( file );

		assertThat( type( "audioTags( path=missing )" ) ).isEqualTo( "bxaudio.fileNotFound" );
		assertThat( type( "audioTags( path=file, tags={ color:'red' } )" ) ).isEqualTo( "bxaudio.invalidArgument" );
		assertThat( type( "audioTags( path=file, tags={ title:'x', cover:notPicture } )" ) ).isEqualTo( "bxaudio.invalidArgument" );
		assertThat( type( "audioTags( path=file, tags={ cover:'/no/such/picture.png' } )" ) ).isEqualTo( "bxaudio.fileNotFound" );
		assertThat( type( "audioTags( path=file, tags={ chapters:[ { title:'no start' } ] } )" ) ).isEqualTo( "bxaudio.invalidArgument" );
		assertThat( type( "audioTags( path=file, tags={ chapters:[ { start:5, end:2 } ] } )" ) ).isEqualTo( "bxaudio.invalidArgument" );
		assertThat( type( "many = []; for( i = 1; i <= 300; i++ ){ many.append( { start:i } ); } audioTags( path=file, tags={ chapters:many } )" ) )
		    .isEqualTo( "bxaudio.invalidArgument" );
		assertThat( type( "audioTags( path=notPicture )" ) ).isEqualTo( "bxaudio.unsupportedFormat" );
		assertThat( Files.readAllBytes( file ) ).isEqualTo( before );
	}

}
