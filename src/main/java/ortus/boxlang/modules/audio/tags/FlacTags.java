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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Tags of FLAC files: a Vorbis comment block for the text and the chapters, and picture blocks for the cover. Chapters use the CHAPTERnnn and
 * CHAPTERnnnNAME comments, which is what Vorbis based formats and most players that read chapters from FLAC understand.
 */
public final class FlacTags {

	private static final int		STREAMINFO		= 0;
	private static final int		PADDING			= 1;
	private static final int		VORBIS_COMMENT	= 4;
	private static final int		PICTURE			= 6;
	private static final int		MAX_BLOCK		= 0xFFFFFF;

	private static final Pattern	CHAPTER_TIME	= Pattern.compile( "^CHAPTER(\\d+)$", Pattern.CASE_INSENSITIVE );
	private static final Pattern	CHAPTER_NAME	= Pattern.compile( "^CHAPTER(\\d+)NAME$", Pattern.CASE_INSENSITIVE );

	private FlacTags() {
	}

	private record Block( int type, byte[] body ) {
	}

	/**
	 * The metadata of a file: where the FLAC stream starts, the blocks and where the audio frames start
	 */
	private record Stream( long start, List<Block> blocks, long audioAt ) {
	}

	private static Stream scan( FileChannel channel ) throws IOException {
		Id3.Parsed	id3		= Id3.parse( channel );
		long		start	= id3.audioAt();
		ByteBuffer	magic	= ByteBuffer.allocate( 4 );
		if ( channel.read( magic, start ) < 4 || magic.get( 0 ) != 'f' || magic.get( 1 ) != 'L' || magic.get( 2 ) != 'a' || magic.get( 3 ) != 'C' ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "Not a FLAC file" );
		}
		List<Block>	blocks	= new ArrayList<>();
		long		pos		= start + 4;
		boolean		last	= false;
		while ( !last ) {
			ByteBuffer header = ByteBuffer.allocate( 4 );
			if ( channel.read( header, pos ) < 4 ) {
				throw new AudioException( AudioException.CORRUPT_AUDIO, "The FLAC file ends in the middle of its metadata" );
			}
			last = ( header.get( 0 ) & 0x80 ) != 0;
			int			type	= header.get( 0 ) & 0x7F;
			int			length	= ( ( header.get( 1 ) & 0xFF ) << 16 ) | ( ( header.get( 2 ) & 0xFF ) << 8 ) | ( header.get( 3 ) & 0xFF );
			ByteBuffer	body	= ByteBuffer.allocate( length );
			int			got		= 0;
			while ( body.hasRemaining() ) {
				int n = channel.read( body, pos + 4 + got );
				if ( n < 0 ) {
					throw new AudioException( AudioException.CORRUPT_AUDIO, "The FLAC file ends in the middle of its metadata" );
				}
				got += n;
			}
			blocks.add( new Block( type, body.array() ) );
			pos += 4 + length;
		}
		return new Stream( start, blocks, pos );
	}

	private static int le32( byte[] b, int at ) {
		return ( b[ at ] & 0xFF ) | ( ( b[ at + 1 ] & 0xFF ) << 8 ) | ( ( b[ at + 2 ] & 0xFF ) << 16 ) | ( ( b[ at + 3 ] & 0xFF ) << 24 );
	}

	private static int be32( byte[] b, int at ) {
		return ( ( b[ at ] & 0xFF ) << 24 ) | ( ( b[ at + 1 ] & 0xFF ) << 16 ) | ( ( b[ at + 2 ] & 0xFF ) << 8 ) | ( b[ at + 3 ] & 0xFF );
	}

	/**
	 * The vendor string and the "KEY=value" comments of a Vorbis comment block
	 */
	private record Comments( String vendor, List<String> entries ) {
	}

	private static Comments comments( byte[] body ) {
		if ( body.length < 8 ) {
			return new Comments( "", new ArrayList<>() );
		}
		int vendorLength = le32( body, 0 );
		if ( vendorLength < 0 || 4L + vendorLength + 4 > body.length ) {
			return new Comments( "", new ArrayList<>() );
		}
		String			vendor	= new String( body, 4, vendorLength, StandardCharsets.UTF_8 );
		int				count	= le32( body, 4 + vendorLength );
		int				pos		= 8 + vendorLength;
		List<String>	entries	= new ArrayList<>();
		for ( int i = 0; i < count && pos + 4 <= body.length; i++ ) {
			int length = le32( body, pos );
			pos += 4;
			if ( length < 0 || ( long ) pos + length > body.length ) {
				break;
			}
			entries.add( new String( body, pos, length, StandardCharsets.UTF_8 ) );
			pos += length;
		}
		return new Comments( vendor, entries );
	}

	private static byte[] le32Bytes( int n ) {
		return new byte[] { ( byte ) n, ( byte ) ( n >> 8 ), ( byte ) ( n >> 16 ), ( byte ) ( n >> 24 ) };
	}

	private static byte[] be32Bytes( int n ) {
		return new byte[] { ( byte ) ( n >> 24 ), ( byte ) ( n >> 16 ), ( byte ) ( n >> 8 ), ( byte ) n };
	}

	private static byte[] commentBlock( String vendor, List<String> entries ) {
		ByteArrayOutputStream	out	= new ByteArrayOutputStream();
		byte[]					v	= vendor.getBytes( StandardCharsets.UTF_8 );
		out.writeBytes( le32Bytes( v.length ) );
		out.writeBytes( v );
		out.writeBytes( le32Bytes( entries.size() ) );
		for ( String e : entries ) {
			byte[] b = e.getBytes( StandardCharsets.UTF_8 );
			out.writeBytes( le32Bytes( b.length ) );
			out.writeBytes( b );
		}
		return out.toByteArray();
	}

	private static String clock( double seconds ) {
		long ms = Math.round( seconds * 1000 );
		return String.format( Locale.ROOT, "%02d:%02d:%02d.%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000 );
	}

	private static double parseClock( String text ) {
		String[]	parts	= text.trim().split( ":" );
		double		seconds	= 0;
		for ( String part : parts ) {
			seconds = seconds * 60 + Double.parseDouble( part );
		}
		return seconds;
	}

	/**
	 * Read the tags of a FLAC file
	 */
	public static TagSet read( FileChannel channel ) throws IOException {
		Stream	stream	= scan( channel );
		TagSet	tags	= new TagSet();
		for ( Block block : stream.blocks() ) {
			if ( block.type() == VORBIS_COMMENT ) {
				fromComments( comments( block.body() ), tags );
			} else if ( block.type() == PICTURE ) {
				TagSet.Cover c = picture( block.body() );
				if ( c != null && ( tags.cover == null || ( be32( block.body(), 0 ) == 3 ) ) ) {
					tags.cover = c;
				}
			}
		}
		return tags;
	}

	private static void fromComments( Comments comments, TagSet tags ) {
		java.util.Map<String, List<String>>	byKey	= new java.util.LinkedHashMap<>();
		java.util.Map<Integer, Double>		times	= new java.util.TreeMap<>();
		java.util.Map<Integer, String>		names	= new java.util.HashMap<>();
		for ( String entry : comments.entries() ) {
			int eq = entry.indexOf( '=' );
			if ( eq <= 0 ) {
				continue;
			}
			String	key		= entry.substring( 0, eq ).toUpperCase( Locale.ROOT );
			String	value	= entry.substring( eq + 1 );
			Matcher	t		= CHAPTER_TIME.matcher( key );
			Matcher	n		= CHAPTER_NAME.matcher( key );
			if ( n.matches() ) {
				try {
					names.put( Integer.parseInt( n.group( 1 ) ), value );
				} catch ( NumberFormatException e ) {
					// a chapter number that is not a number is left out
				}
			} else if ( t.matches() ) {
				try {
					times.put( Integer.parseInt( t.group( 1 ) ), parseClock( value ) );
				} catch ( NumberFormatException e ) {
					// a chapter time that is not a time is left out
				}
			} else {
				byKey.computeIfAbsent( key, k -> new ArrayList<>() ).add( value );
			}
		}
		put( tags, "title", byKey, "TITLE" );
		put( tags, "artist", byKey, "ARTIST" );
		put( tags, "album", byKey, "ALBUM" );
		put( tags, "albumArtist", byKey, "ALBUMARTIST", "ALBUM ARTIST" );
		put( tags, "composer", byKey, "COMPOSER" );
		put( tags, "genre", byKey, "GENRE" );
		put( tags, "year", byKey, "DATE", "YEAR" );
		put( tags, "comment", byKey, "COMMENT", "DESCRIPTION" );
		String	track		= first( byKey, "TRACKNUMBER" );
		String	trackTotal	= first( byKey, "TRACKTOTAL", "TOTALTRACKS" );
		if ( track != null ) {
			tags.text.put( "track", trackTotal != null && !track.contains( "/" ) ? track + "/" + trackTotal : track );
		}
		String	disc		= first( byKey, "DISCNUMBER" );
		String	discTotal	= first( byKey, "DISCTOTAL", "TOTALDISCS" );
		if ( disc != null ) {
			tags.text.put( "disc", discTotal != null && !disc.contains( "/" ) ? disc + "/" + discTotal : disc );
		}
		if ( !times.isEmpty() ) {
			List<TagSet.Chapter> chapters = new ArrayList<>();
			for ( var e : times.entrySet() ) {
				chapters.add( new TagSet.Chapter( e.getValue(), null, names.getOrDefault( e.getKey(), "" ) ) );
			}
			chapters.sort( java.util.Comparator.comparingDouble( TagSet.Chapter::start ) );
			tags.chapters = chapters;
		}
	}

	private static String first( java.util.Map<String, List<String>> byKey, String... keys ) {
		for ( String k : keys ) {
			List<String> values = byKey.get( k );
			if ( values != null && !values.isEmpty() ) {
				return String.join( ", ", values );
			}
		}
		return null;
	}

	private static void put( TagSet tags, String name, java.util.Map<String, List<String>> byKey, String... keys ) {
		String v = first( byKey, keys );
		if ( v != null ) {
			tags.text.put( name, v );
		}
	}

	private static TagSet.Cover picture( byte[] b ) {
		if ( b.length < 32 ) {
			return null;
		}
		int	mimeLength	= be32( b, 4 );
		int	at			= 8;
		if ( mimeLength < 0 || ( long ) at + mimeLength + 4 > b.length ) {
			return null;
		}
		String mime = new String( b, at, mimeLength, StandardCharsets.ISO_8859_1 );
		at += mimeLength;
		int descLength = be32( b, at );
		if ( descLength < 0 || ( long ) at + 4 + descLength + 16 + 4 > b.length ) {
			return null;
		}
		at += 4 + descLength + 16;
		if ( at + 4 > b.length ) {
			return null;
		}
		int dataLength = be32( b, at );
		at += 4;
		if ( dataLength < 0 || at + dataLength > b.length ) {
			return null;
		}
		return new TagSet.Cover( mime, java.util.Arrays.copyOfRange( b, at, at + dataLength ) );
	}

	private static byte[] pictureBlock( TagSet.Cover cover ) {
		ByteArrayOutputStream out = new ByteArrayOutputStream( cover.data().length + 64 );
		out.writeBytes( be32Bytes( 3 ) );
		byte[] mime = cover.mimeType().getBytes( StandardCharsets.ISO_8859_1 );
		out.writeBytes( be32Bytes( mime.length ) );
		out.writeBytes( mime );
		out.writeBytes( be32Bytes( 0 ) );
		for ( int i = 0; i < 4; i++ ) {
			out.writeBytes( be32Bytes( 0 ) );
		}
		out.writeBytes( be32Bytes( cover.data().length ) );
		out.writeBytes( cover.data() );
		return out.toByteArray();
	}

	private static List<String> managed( String tag ) {
		return switch ( tag ) {
			case "title" -> List.of( "TITLE" );
			case "artist" -> List.of( "ARTIST" );
			case "album" -> List.of( "ALBUM" );
			case "albumArtist" -> List.of( "ALBUMARTIST", "ALBUM ARTIST" );
			case "composer" -> List.of( "COMPOSER" );
			case "genre" -> List.of( "GENRE" );
			case "year" -> List.of( "DATE", "YEAR" );
			case "track" -> List.of( "TRACKNUMBER", "TRACKTOTAL", "TOTALTRACKS" );
			case "disc" -> List.of( "DISCNUMBER", "DISCTOTAL", "TOTALDISCS" );
			default -> List.of( "COMMENT", "DESCRIPTION" );
		};
	}

	/**
	 * Write a copy of a FLAC file with an edit applied to its tags
	 */
	public static void write( FileChannel source, FileChannel target, TagSet edit ) throws IOException {
		Stream		stream	= scan( source );
		Comments	old		= new Comments( "bx-audio", new ArrayList<>() );
		for ( Block block : stream.blocks() ) {
			if ( block.type() == VORBIS_COMMENT ) {
				old = comments( block.body() );
				break;
			}
		}
		java.util.Set<String> drop = new java.util.HashSet<>();
		for ( String tag : edit.text.keySet() ) {
			drop.addAll( managed( tag ) );
		}
		List<String> entries = new ArrayList<>();
		for ( String entry : old.entries() ) {
			int		eq	= entry.indexOf( '=' );
			String	key	= ( eq < 0 ? entry : entry.substring( 0, eq ) ).toUpperCase( Locale.ROOT );
			if ( drop.contains( key ) || edit.chapters != null && ( CHAPTER_TIME.matcher( key ).matches() || CHAPTER_NAME.matcher( key ).matches() ) ) {
				continue;
			}
			entries.add( entry );
		}
		for ( var e : edit.text.entrySet() ) {
			String v = e.getValue();
			if ( v == null || v.isEmpty() ) {
				continue;
			}
			switch ( e.getKey() ) {
				case "title" -> entries.add( "TITLE=" + v );
				case "artist" -> entries.add( "ARTIST=" + v );
				case "album" -> entries.add( "ALBUM=" + v );
				case "albumArtist" -> entries.add( "ALBUMARTIST=" + v );
				case "composer" -> entries.add( "COMPOSER=" + v );
				case "genre" -> entries.add( "GENRE=" + v );
				case "year" -> entries.add( "DATE=" + v );
				case "comment" -> entries.add( "COMMENT=" + v );
				case "track", "disc" -> {
					String	name	= e.getKey().equals( "track" ) ? "TRACK" : "DISC";
					int		slash	= v.indexOf( '/' );
					entries.add( name + "NUMBER=" + ( slash < 0 ? v : v.substring( 0, slash ).trim() ) );
					if ( slash >= 0 && !v.substring( slash + 1 ).isBlank() ) {
						entries.add( name + "TOTAL=" + v.substring( slash + 1 ).trim() );
					}
				}
				default -> {
				}
			}
		}
		if ( edit.chapters != null ) {
			int number = 1;
			for ( TagSet.Chapter c : edit.chapters ) {
				String id = String.format( "%03d", number++ );
				entries.add( "CHAPTER" + id + "=" + clock( c.start() ) );
				entries.add( "CHAPTER" + id + "NAME=" + ( c.title() == null ? "" : c.title() ) );
			}
		}

		List<Block> blocks = new ArrayList<>();
		for ( Block block : stream.blocks() ) {
			if ( block.type() == VORBIS_COMMENT || block.type() == PADDING || block.type() == PICTURE && edit.coverTouched ) {
				continue;
			}
			blocks.add( block );
			if ( block.type() == STREAMINFO ) {
				blocks.add( new Block( VORBIS_COMMENT, commentBlock( old.vendor(), entries ) ) );
			}
		}
		if ( edit.coverTouched && edit.cover != null ) {
			blocks.add( new Block( PICTURE, pictureBlock( edit.cover ) ) );
		}

		ByteBuffer prefix = ByteBuffer.allocate( ( int ) stream.start() );
		source.read( prefix, 0 );
		target.write( ByteBuffer.wrap( prefix.array() ) );
		ByteArrayOutputStream header = new ByteArrayOutputStream();
		header.writeBytes( "fLaC".getBytes( StandardCharsets.ISO_8859_1 ) );
		for ( int i = 0; i < blocks.size(); i++ ) {
			Block block = blocks.get( i );
			if ( block.body().length > MAX_BLOCK ) {
				throw new AudioException( AudioException.TOO_LARGE,
				    "A FLAC metadata block cannot be larger than 16 MB, this one is " + block.body().length + " bytes" );
			}
			header.write( ( i == blocks.size() - 1 ? 0x80 : 0 ) | block.type() );
			header.write( block.body().length >> 16 );
			header.write( block.body().length >> 8 );
			header.write( block.body().length );
			header.writeBytes( block.body() );
		}
		target.write( ByteBuffer.wrap( header.toByteArray() ) );
		long	position	= stream.audioAt();
		long	length		= source.size();
		while ( position < length ) {
			position += source.transferTo( position, length - position, target );
		}
	}

}
