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
package ortus.boxlang.modules.audio.tags;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.codec.AudioFiles;
import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.synth.Synth;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Tags, covers and chapters of MP3, FLAC and WAV files, including files that other programs wrote
 */
class TagsTest {

	private static final Path FIXTURES = Path.of( "src/test/resources/tags" );

	private static AudioBuffer tone() {
		return Dsp.gain( Synth.oscillator( "sine", 440, 3, 44100, 1, null, 0.5, 0 ), -12 );
	}

	private static Path copy( Path dir, String name ) throws Exception {
		Path target = dir.resolve( name );
		Files.copy( FIXTURES.resolve( name ), target, StandardCopyOption.REPLACE_EXISTING );
		return target;
	}

	private static Path make( Path dir, String name ) {
		Path file = dir.resolve( name );
		AudioFiles.write( tone(), file, 16, 64 );
		return file;
	}

	private static byte[] png() throws Exception {
		BufferedImage			image	= new BufferedImage( 8, 8, BufferedImage.TYPE_INT_RGB );
		ByteArrayOutputStream	out		= new ByteArrayOutputStream();
		ImageIO.write( image, "png", out );
		return out.toByteArray();
	}

	private static TagSet read( Path file ) {
		return ( TagSet ) FileTags.read( file )[ 1 ];
	}

	private static String utf16( String text ) {
		return new String( text.getBytes( StandardCharsets.UTF_16LE ), StandardCharsets.ISO_8859_1 );
	}

	private static TagSet edit( String... keyValues ) {
		TagSet set = new TagSet();
		for ( int i = 0; i < keyValues.length; i += 2 ) {
			set.text.put( keyValues[ i ], keyValues[ i + 1 ] );
		}
		return set;
	}

	@Test
	@DisplayName( "A version 2.3 tag written by another program is read: UTF-16 text, TYER, a numbered genre, the plain comment and the chapters" )
	void readsMutagenMp3( @TempDir Path dir ) throws Exception {
		TagSet tags = read( copy( dir, "mutagen-v23.mp3" ) );
		assertThat( tags.text.get( "title" ) ).isEqualTo( "Mutagen ✓ Title" );
		assertThat( tags.text.get( "artist" ) ).isEqualTo( "Bob" );
		assertThat( tags.text.get( "year" ) ).isEqualTo( "1999" );
		assertThat( tags.text.get( "genre" ) ).isEqualTo( "Rock" );
		// the iTunNORM comment is data of another program, not the comment
		assertThat( tags.text.get( "comment" ) ).isEqualTo( "plain comment" );
		assertThat( tags.chapters ).hasSize( 2 );
		assertThat( tags.chapters.get( 0 ).title() ).isEqualTo( "L".repeat( 200 ) );
		assertThat( tags.chapters.get( 0 ).start() ).isEqualTo( 0.0 );
		assertThat( tags.chapters.get( 0 ).end() ).isEqualTo( 0.25 );
		assertThat( tags.chapters.get( 1 ).title() ).isEqualTo( "Two" );
	}

	@Test
	@DisplayName( "Editing a tag keeps the frames the module does not manage, turns TYER into TDRC, and re-codes the chapter sub frames" )
	void editKeepsOtherFrames( @TempDir Path dir ) throws Exception {
		Path		file	= copy( dir, "mutagen-v23.mp3" );
		AudioBuffer	before	= AudioFiles.read( file );
		FileTags.write( file, edit( "title", "Edited" ) );
		TagSet tags = read( file );
		assertThat( tags.text.get( "title" ) ).isEqualTo( "Edited" );
		assertThat( tags.text.get( "artist" ) ).isEqualTo( "Bob" );
		assertThat( tags.text.get( "year" ) ).isEqualTo( "1999" );
		assertThat( tags.text.get( "comment" ) ).isEqualTo( "plain comment" );
		// the 200 character title needs a two byte size, which version 2.4 codes differently from 2.3
		assertThat( tags.chapters.get( 0 ).title() ).isEqualTo( "L".repeat( 200 ) );
		String raw = new String( Files.readAllBytes( file ), StandardCharsets.ISO_8859_1 );
		// these frames were written as UTF-16, so the letters have a zero byte between them
		assertThat( raw ).contains( utf16( "MY_KEY" ) );
		assertThat( raw ).contains( utf16( "iTunNORM" ) );
		assertThat( raw ).doesNotContain( "TYER" );
		assertThat( raw ).startsWith( "ID3\u0004" );
		AudioBuffer after = AudioFiles.read( file );
		assertThat( after.getFrames() ).isEqualTo( before.getFrames() );
		assertThat( after.getRms() ).isEqualTo( before.getRms() );
	}

	@Test
	@DisplayName( "A FLAC file tagged by another program is read, and an edit keeps its other comments and the audio" )
	void mutagenFlac( @TempDir Path dir ) throws Exception {
		Path	file	= copy( dir, "mutagen.flac" );
		TagSet	tags	= read( file );
		assertThat( tags.text.get( "title" ) ).isEqualTo( "Flac by mutagen" );
		assertThat( tags.text.get( "artist" ) ).isEqualTo( "A, B" );
		assertThat( tags.text.get( "track" ) ).isEqualTo( "7/9" );
		AudioBuffer before = AudioFiles.read( file );
		FileTags.write( file, edit( "album", "New", "title", "Edited" ) );
		tags = read( file );
		assertThat( tags.text.get( "title" ) ).isEqualTo( "Edited" );
		assertThat( tags.text.get( "album" ) ).isEqualTo( "New" );
		assertThat( tags.text.get( "artist" ) ).isEqualTo( "A, B" );
		assertThat( new String( Files.readAllBytes( file ), StandardCharsets.ISO_8859_1 ) ).contains( "MYCUSTOM=keep me too" );
		AudioBuffer after = AudioFiles.read( file );
		assertThat( after.getFrames() ).isEqualTo( before.getFrames() );
		assertThat( after.getRms() ).isEqualTo( before.getRms() );
	}

	@Test
	@DisplayName( "Every tag, the cover and the chapters round trip in MP3 and FLAC" )
	void roundTrip( @TempDir Path dir ) throws Exception {
		for ( String name : List.of( "a.mp3", "a.flac" ) ) {
			Path	file	= make( dir, name );
			TagSet	set		= edit( "title", "Tést ✓", "artist", "Ada", "album", "Alb", "albumArtist", "Ortus", "composer", "Luis", "genre", "Podcast",
			    "year", "2026", "track", "3/12", "disc", "1/2", "comment", "Hi there" );
			set.cover			= new TagSet.Cover( "image/png", png() );
			set.coverTouched	= true;
			set.chapters		= List.of( new TagSet.Chapter( 0, null, "Intro" ), new TagSet.Chapter( 1.5, null, "Middle" ),
			    new TagSet.Chapter( 2.25, 2.75, "End" ) );
			FileTags.Outcome outcome = FileTags.write( file, set );
			assertThat( outcome.ignored() ).isEmpty();
			assertThat( outcome.written() ).containsAtLeast( "title", "cover", "chapters" ).inOrder();
			TagSet back = read( file );
			assertThat( back.text ).containsAtLeastEntriesIn( set.text );
			assertThat( back.cover.mimeType() ).isEqualTo( "image/png" );
			assertThat( back.cover.data() ).isEqualTo( set.cover.data() );
			assertThat( back.chapters.stream().map( TagSet.Chapter::title ).toList() ).containsExactly( "Intro", "Middle", "End" ).inOrder();
			assertThat( back.chapters.get( 1 ).start() ).isWithin( 0.001 ).of( 1.5 );
			if ( name.endsWith( ".mp3" ) ) {
				// MP3 stores the end of each chapter: given, or the start of the next, or the length of the file for the last
				assertThat( back.chapters.get( 0 ).end() ).isWithin( 0.001 ).of( 1.5 );
				assertThat( back.chapters.get( 2 ).end() ).isWithin( 0.001 ).of( 2.75 );
			}
		}
	}

	@Test
	@DisplayName( "Tags left out are kept, an empty tag removes it, and a null cover and an empty chapter list remove those" )
	void editSemantics( @TempDir Path dir ) throws Exception {
		for ( String name : List.of( "a.mp3", "a.flac", "a.wav" ) ) {
			Path	file	= make( dir, name );
			TagSet	first	= edit( "title", "One", "artist", "Ada", "comment", "Note" );
			first.chapters = List.of( new TagSet.Chapter( 0, null, "Only" ) );
			if ( !name.endsWith( ".wav" ) ) {
				first.cover			= new TagSet.Cover( "image/png", png() );
				first.coverTouched	= true;
			}
			FileTags.write( file, first );
			FileTags.write( file, edit( "title", "Two" ) );
			TagSet tags = read( file );
			assertThat( tags.text.get( "title" ) ).isEqualTo( "Two" );
			assertThat( tags.text.get( "artist" ) ).isEqualTo( "Ada" );
			assertThat( tags.chapters ).hasSize( 1 );
			if ( !name.endsWith( ".wav" ) ) {
				assertThat( tags.cover ).isNotNull();
			}

			TagSet removal = edit( "artist", "" );
			removal.text.put( "comment", null );
			removal.chapters		= List.of();
			removal.coverTouched	= true;
			FileTags.write( file, removal );
			tags = read( file );
			assertThat( tags.text.get( "title" ) ).isEqualTo( "Two" );
			assertThat( tags.text ).doesNotContainKey( "artist" );
			assertThat( tags.text ).doesNotContainKey( "comment" );
			assertThat( tags.chapters ).isNull();
			assertThat( tags.cover ).isNull();
		}
	}

	@Test
	@DisplayName( "WAV stores its text and chapters, reports what it has no place for, and its samples are untouched" )
	void wav( @TempDir Path dir ) throws Exception {
		Path		file	= make( dir, "a.wav" );
		AudioBuffer	before	= AudioFiles.read( file );
		TagSet		set		= edit( "title", "Wäv ✓", "artist", "Ada", "albumArtist", "Ortus", "disc", "1", "year", "2026" );
		set.cover			= new TagSet.Cover( "image/png", png() );
		set.coverTouched	= true;
		set.chapters		= List.of( new TagSet.Chapter( 0, null, "A" ), new TagSet.Chapter( 1.25, null, "B" ) );
		FileTags.Outcome outcome = FileTags.write( file, set );
		assertThat( outcome.format() ).isEqualTo( "wav" );
		assertThat( outcome.ignored() ).containsExactly( "albumArtist", "disc", "cover" ).inOrder();
		assertThat( outcome.written() ).containsExactly( "title", "artist", "year", "chapters" ).inOrder();
		TagSet back = read( file );
		assertThat( back.text.get( "title" ) ).isEqualTo( "Wäv ✓" );
		assertThat( back.text.get( "year" ) ).isEqualTo( "2026" );
		assertThat( back.chapters.get( 1 ).start() ).isWithin( 0.001 ).of( 1.25 );
		AudioBuffer after = AudioFiles.read( file );
		assertThat( after.getFrames() ).isEqualTo( before.getFrames() );
		assertThat( after.getRms() ).isEqualTo( before.getRms() );
		// still a valid RIFF whose size says how long the file is
		byte[]	bytes		= Files.readAllBytes( file );
		int		riffSize	= ( bytes[ 4 ] & 0xFF ) | ( ( bytes[ 5 ] & 0xFF ) << 8 ) | ( ( bytes[ 6 ] & 0xFF ) << 16 ) | ( ( bytes[ 7 ] & 0xFF ) << 24 );
		assertThat( riffSize ).isEqualTo( bytes.length - 8 );
	}

	@Test
	@DisplayName( "A chapter that has no end ends the MP3 chapters at the length of the audio, found without decoding" )
	void lastChapterEnd( @TempDir Path dir ) throws Exception {
		Path	file	= make( dir, "long.mp3" );
		TagSet	set		= new TagSet();
		set.chapters = List.of( new TagSet.Chapter( 0, null, "All" ) );
		FileTags.write( file, set );
		double length = AudioFiles.read( file ).getDurationSeconds();
		assertThat( read( file ).chapters.get( 0 ).end() ).isWithin( 0.1 ).of( length );
	}

	@Test
	@DisplayName( "Formats with no tag support and files that are not audio are refused with a typed error" )
	void unsupported( @TempDir Path dir ) throws Exception {
		Path ogg = dir.resolve( "a.ogg" );
		Files.write( ogg, "OggS\0\2\0\0\0\0\0\0\0\0\0\0".getBytes( StandardCharsets.ISO_8859_1 ) );
		AudioException e = assertThrows( AudioException.class, () -> FileTags.read( ogg ) );
		assertThat( e.getType() ).isEqualTo( AudioException.UNSUPPORTED_FORMAT );
		assertThat( e.getMessage() ).contains( "OGG" );
		Path text = dir.resolve( "notes.txt" );
		Files.writeString( text, "just some words in a file" );
		assertThat( assertThrows( AudioException.class, () -> FileTags.write( text, edit( "title", "x" ) ) ).getType() )
		    .isEqualTo( AudioException.UNSUPPORTED_FORMAT );
		// and nothing was left behind
		try ( var files = Files.list( dir ) ) {
			assertThat( files.map( p -> p.getFileName().toString() ).filter( n -> n.startsWith( ".bxaudio" ) ).toList() ).isEmpty();
		}
	}

	@Test
	@DisplayName( "Pictures are recognised from their first bytes" )
	void imageTypes() throws Exception {
		assertThat( FileTags.imageType( png() ) ).isEqualTo( "image/png" );
		assertThat( FileTags.imageType( new byte[] { ( byte ) 0xFF, ( byte ) 0xD8, ( byte ) 0xFF, 0, 0 } ) ).isEqualTo( "image/jpeg" );
		assertThat( assertThrows( AudioException.class, () -> FileTags.imageType( "not a picture".getBytes() ) ).getType() )
		    .isEqualTo( AudioException.INVALID_ARGUMENT );
	}

}
