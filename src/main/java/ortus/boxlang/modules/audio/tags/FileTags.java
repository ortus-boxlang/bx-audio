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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import ortus.boxlang.modules.audio.codec.AudioFiles;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Reading and editing the tags of an audio file, in whatever format it is: MP3, FLAC or WAV, found from the content.
 */
public final class FileTags {

	private FileTags() {
	}

	/**
	 * What an edit did: the format, the tags that were stored and the tags this format has no place for
	 */
	public record Outcome( String format, List<String> written, List<String> ignored ) {
	}

	/**
	 * @return mp3, flac or wav, from the content of the file
	 *
	 * @throws AudioException for a format that has no tag support
	 */
	public static String formatOf( FileChannel channel ) throws IOException {
		ByteBuffer	head	= ByteBuffer.allocate( 12 );
		int			got		= channel.read( head, 0 );
		if ( got >= 12 ) {
			String start = new String( head.array(), 0, 12, StandardCharsets.ISO_8859_1 );
			if ( start.startsWith( "RIFF" ) && start.substring( 8 ).startsWith( "WAVE" ) ) {
				return "wav";
			}
			if ( start.startsWith( "fLaC" ) ) {
				return "flac";
			}
			if ( start.startsWith( "ID3" ) ) {
				// A FLAC file can start with an ID3 tag too
				long		at		= Id3.parse( channel ).audioAt();
				ByteBuffer	magic	= ByteBuffer.allocate( 4 );
				if ( channel.read( magic, at ) == 4 && new String( magic.array(), StandardCharsets.ISO_8859_1 ).equals( "fLaC" ) ) {
					return "flac";
				}
				return "mp3";
			}
			if ( ( head.get( 0 ) & 0xFF ) == 0xFF && ( head.get( 1 ) & 0xE0 ) == 0xE0 ) {
				return "mp3";
			}
			if ( start.startsWith( "OggS" ) || start.substring( 4 ).startsWith( "ftyp" ) ) {
				throw new AudioException( AudioException.UNSUPPORTED_FORMAT,
				    "Tags of OGG, Opus and M4A files are not supported yet. MP3, FLAC and WAV files are." );
			}
		}
		throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "Tags can be read and written for MP3, FLAC and WAV files, and this is none of them" );
	}

	/**
	 * Read the tags of a file
	 *
	 * @return the format and the tags
	 */
	public static Object[] read( Path file ) {
		try ( FileChannel channel = FileChannel.open( file, StandardOpenOption.READ ) ) {
			String	format	= formatOf( channel );
			TagSet	tags	= switch ( format ) {
								case "mp3" -> Id3.read( channel );
								case "flac" -> FlacTags.read( channel );
								default -> WavTags.read( channel );
							};
			return new Object[] { format, tags };
		} catch ( IOException e ) {
			throw new AudioException( AudioException.IO, "Could not read the tags of [" + file + "]: " + e.getMessage(), e );
		}
	}

	/**
	 * Apply an edit to a file. The file is replaced in one step, so it is never left half written.
	 */
	public static Outcome write( Path file, TagSet edit ) {
		String[] format = new String[ 1 ];
		AudioFiles.atomically( file, temp -> {
			try (
			    FileChannel source = FileChannel.open( file, StandardOpenOption.READ );
			    FileChannel target = FileChannel.open( temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING ) ) {
				format[ 0 ] = formatOf( source );
				switch ( format[ 0 ] ) {
					case "mp3" -> Id3.write( source, target, edit );
					case "flac" -> FlacTags.write( source, target, edit );
					default -> WavTags.write( source, target, edit );
				}
			} catch ( IOException e ) {
				throw new AudioException( AudioException.IO, "Could not write the tags of [" + file + "]: " + e.getMessage(), e );
			}
		} );
		List<String>	written	= new ArrayList<>();
		List<String>	ignored	= new ArrayList<>();
		boolean			wav		= format[ 0 ].equals( "wav" );
		for ( String key : TagSet.TEXT_KEYS ) {
			if ( edit.text.containsKey( key ) ) {
				( wav && ( key.equals( "albumArtist" ) || key.equals( "disc" ) ) ? ignored : written ).add( key );
			}
		}
		if ( edit.coverTouched ) {
			( wav ? ignored : written ).add( "cover" );
		}
		if ( edit.chapters != null ) {
			written.add( "chapters" );
		}
		return new Outcome( format[ 0 ], written, ignored );
	}

	/**
	 * The media type of a picture, from its first bytes
	 *
	 * @return image/png, image/jpeg, image/gif, image/webp or image/bmp
	 */
	public static String imageType( byte[] b ) {
		if ( b.length >= 8 && ( b[ 0 ] & 0xFF ) == 0x89 && b[ 1 ] == 'P' && b[ 2 ] == 'N' && b[ 3 ] == 'G' ) {
			return "image/png";
		}
		if ( b.length >= 3 && ( b[ 0 ] & 0xFF ) == 0xFF && ( b[ 1 ] & 0xFF ) == 0xD8 && ( b[ 2 ] & 0xFF ) == 0xFF ) {
			return "image/jpeg";
		}
		if ( b.length >= 6 && b[ 0 ] == 'G' && b[ 1 ] == 'I' && b[ 2 ] == 'F' ) {
			return "image/gif";
		}
		if ( b.length >= 12 && b[ 0 ] == 'R' && b[ 1 ] == 'I' && b[ 2 ] == 'F' && b[ 3 ] == 'F' && b[ 8 ] == 'W' && b[ 9 ] == 'E' && b[ 10 ] == 'B'
		    && b[ 11 ] == 'P' ) {
			return "image/webp";
		}
		if ( b.length >= 2 && b[ 0 ] == 'B' && b[ 1 ] == 'M' ) {
			return "image/bmp";
		}
		throw new AudioException( AudioException.INVALID_ARGUMENT, "The cover must be a PNG, JPEG, GIF, WebP or BMP picture" );
	}

}
