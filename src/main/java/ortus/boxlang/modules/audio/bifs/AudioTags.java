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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import ortus.boxlang.modules.audio.tags.FileTags;
import ortus.boxlang.modules.audio.tags.TagSet;
import ortus.boxlang.modules.audio.util.AudioException;
import ortus.boxlang.modules.audio.util.Numbers;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.util.FileSystemUtil;

@BoxBIF( description = "Read or write the tags, cover art and chapters of an MP3, FLAC or WAV file" )
public class AudioTags extends BIF {

	private static final Key	tags			= Key.of( "tags" );
	private static final long	MAX_COVER		= 16L * 1024 * 1024;
	private static final int	MAX_CHAPTERS	= 255;

	/**
	 * Constructor
	 */
	public AudioTags() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "string", Key.path ),
		    new Argument( false, "struct", tags )
		};
	}

	/**
	 * Reads the tags of an audio file, or changes them. Without the tags argument the tags are read. With it, the tags you give are written and the
	 * ones you leave out are kept: a text tag set to an empty string is removed, a cover set to an empty string is removed, and an empty array of
	 * chapters removes the chapters. The file is replaced in one step, and the audio in it is not touched.
	 *
	 * <p>
	 * MP3 files get an ID3v2.4 tag, FLAC files Vorbis comments and picture blocks, WAV files an INFO list and cue points. A WAV file has no place for
	 * the album artist, the disc or a cover, and those are reported as ignored. OGG, Opus and M4A files are not supported yet.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.path The audio file.
	 *
	 * @argument.tags The tags to write: title, artist, album, albumArtist, composer, genre, year, track (such as "3" or "3/12"), disc, comment, cover and
	 *                chapters. The cover is a path to a PNG, JPEG, GIF, WebP or BMP picture, binary data, or a struct with data or path. The chapters are
	 *                an
	 *                array of structs with start (seconds), title and optionally end; time and label are accepted for start and title, so the markers
	 *                you give audioMarkers() work.
	 *
	 * @return When reading: a struct with format (mp3, flac or wav), title, artist, album, albumArtist, composer, genre, year, track, disc and comment
	 *         (empty strings when the file has none), cover (a struct with mimeType, size and data, or null) and chapters (an array of structs with
	 *         start, end and title, where end is null when the chapter lasts until the next one). When writing: a struct with path, format, written
	 *         (the tags that were stored) and ignored (the tags this format has no place for).
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Path path = FileSystemUtil.expandPath( context, arguments.getAsString( Key.path ) ).absolutePath();
		if ( !Files.isRegularFile( path ) ) {
			throw new AudioException( AudioException.FILE_NOT_FOUND, "The audio file [" + path + "] does not exist" );
		}
		if ( arguments.get( tags ) instanceof IStruct edit ) {
			FileTags.Outcome outcome = FileTags.write( path, toTagSet( context, edit ) );
			return Struct.ofNonConcurrent( "path", path.toString(), "format", outcome.format(), "written",
			    Array.fromList( new ArrayList<Object>( outcome.written() ) ),
			    "ignored", Array.fromList( new ArrayList<Object>( outcome.ignored() ) ) );
		}
		Object[]	read	= FileTags.read( path );
		TagSet		found	= ( TagSet ) read[ 1 ];
		IStruct		result	= new Struct( IStruct.TYPES.LINKED );
		result.put( Key.of( "format" ), read[ 0 ] );
		for ( String key : TagSet.TEXT_KEYS ) {
			result.put( Key.of( key ), found.text.getOrDefault( key, "" ) );
		}
		result.put( Key.of( "cover" ), found.cover == null ? null
		    : Struct.ofNonConcurrent( "mimeType", found.cover.mimeType(), "size", found.cover.data().length, "data", found.cover.data() ) );
		Array chapters = new Array();
		if ( found.chapters != null ) {
			for ( TagSet.Chapter c : found.chapters ) {
				chapters.add( Struct.ofNonConcurrent( "start", c.start(), "end", c.end(), "title", c.title() ) );
			}
		}
		result.put( Key.of( "chapters" ), chapters );
		return result;
	}

	private static TagSet toTagSet( IBoxContext context, IStruct edit ) {
		TagSet set = new TagSet();
		for ( Key key : edit.keySet() ) {
			String	name	= key.getName();
			Object	value	= edit.get( key );
			if ( name.equalsIgnoreCase( "cover" ) ) {
				set.coverTouched	= true;
				set.cover			= cover( context, value );
			} else if ( name.equalsIgnoreCase( "chapters" ) ) {
				set.chapters = chapters( value );
			} else {
				String known = TagSet.TEXT_KEYS.stream().filter( k -> k.equalsIgnoreCase( name ) ).findFirst().orElse( null );
				if ( known == null ) {
					throw new AudioException( AudioException.INVALID_ARGUMENT,
					    "Unknown tag [" + name + "]. Use " + String.join( ", ", TagSet.TEXT_KEYS ) + ", cover or chapters" );
				}
				set.text.put( known, value == null ? null : String.valueOf( value ) );
			}
		}
		return set;
	}

	private static TagSet.Cover cover( IBoxContext context, Object value ) {
		if ( value == null || value instanceof String s && s.isBlank() ) {
			return null;
		}
		byte[] data;
		if ( value instanceof byte[] bytes ) {
			data = bytes;
		} else if ( value instanceof String pathString ) {
			data = readPicture( FileSystemUtil.expandPath( context, pathString ).absolutePath() );
		} else if ( value instanceof IStruct s ) {
			Object	inner	= s.get( Key.of( "data" ) );
			Object	file	= s.get( Key.of( "path" ) );
			if ( inner instanceof byte[] bytes ) {
				data = bytes;
			} else if ( file != null ) {
				data = readPicture( FileSystemUtil.expandPath( context, String.valueOf( file ) ).absolutePath() );
			} else {
				throw new AudioException( AudioException.INVALID_ARGUMENT, "A cover struct needs data (binary) or path" );
			}
		} else {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The cover must be a path, binary data or a struct with data or path" );
		}
		if ( data.length > MAX_COVER ) {
			throw new AudioException( AudioException.TOO_LARGE, "The cover is " + data.length + " bytes, which is more than the 16 MB a tag can hold" );
		}
		return new TagSet.Cover( FileTags.imageType( data ), data );
	}

	private static byte[] readPicture( Path file ) {
		if ( !Files.isRegularFile( file ) ) {
			throw new AudioException( AudioException.FILE_NOT_FOUND, "The cover picture [" + file + "] does not exist" );
		}
		try {
			if ( Files.size( file ) > MAX_COVER ) {
				throw new AudioException( AudioException.TOO_LARGE, "The cover [" + file + "] is larger than the 16 MB a tag can hold" );
			}
			return Files.readAllBytes( file );
		} catch ( IOException e ) {
			throw new AudioException( AudioException.IO, "Could not read the cover [" + file + "]: " + e.getMessage(), e );
		}
	}

	private static List<TagSet.Chapter> chapters( Object value ) {
		if ( value == null ) {
			return new ArrayList<>();
		}
		if ( ! ( value instanceof Array list ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "chapters must be an array of structs such as { start: 0, title: \"Intro\" }" );
		}
		if ( list.size() > MAX_CHAPTERS ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "A file can have at most " + MAX_CHAPTERS + " chapters but " + list.size() + " were given" );
		}
		List<TagSet.Chapter> result = new ArrayList<>();
		for ( Object entry : list ) {
			if ( ! ( entry instanceof IStruct s ) ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT, "Each chapter must be a struct such as { start: 0, title: \"Intro\" }" );
			}
			Object start = s.containsKey( Key.of( "start" ) ) ? s.get( Key.of( "start" ) ) : s.get( Key.of( "time" ) );
			if ( start == null ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT, "Each chapter needs a start in seconds" );
			}
			double	from	= Numbers.toDouble( start, "the chapter start" );
			Object	endRaw	= s.get( Key.of( "end" ) );
			Double	end		= endRaw == null ? null : Numbers.toDouble( endRaw, "the chapter end" );
			if ( from < 0 || end != null && end < from ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT,
				    "A chapter starts at zero or later and ends after it starts, this one starts at " + from );
			}
			Object title = s.containsKey( Key.of( "title" ) ) ? s.get( Key.of( "title" ) ) : s.get( Key.of( "label" ) );
			result.add( new TagSet.Chapter( from, end, title == null ? "" : String.valueOf( title ) ) );
		}
		result.sort( Comparator.comparingDouble( TagSet.Chapter::start ) );
		return result;
	}

}
