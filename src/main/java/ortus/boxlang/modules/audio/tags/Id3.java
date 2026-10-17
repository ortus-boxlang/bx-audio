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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ortus.boxlang.modules.audio.util.AudioException;

/**
 * ID3v2 tags of MP3 files: reading versions 2.2, 2.3 and 2.4, and writing 2.4 with UTF-8 text. Frames the module does not manage are kept when a
 * tag is edited, so other software's data is not lost.
 *
 * <p>
 * Chapters are the CHAP and CTOC frames of the ID3v2 Chapter Frame Addendum, which is what podcast players and Apple Podcasts read.
 */
public final class Id3 {

	private static final int					MAX_TAG		= 64 * 1024 * 1024;

	/**
	 * The text frame of each tag
	 */
	private static final Map<String, String>	FRAMES		= Map.of( "title", "TIT2", "artist", "TPE1", "album", "TALB", "albumArtist", "TPE2", "composer",
	    "TCOM",
	    "genre", "TCON", "year", "TDRC", "track", "TRCK", "disc", "TPOS" );

	/**
	 * Frames of ID3v2.3 that 2.4 dropped or replaced, which are not carried over
	 */
	private static final Set<String>			OBSOLETE	= Set.of( "TDAT", "TIME", "TORY", "TRDA", "TSIZ", "IPLS", "EQUA", "RVAD" );

	/**
	 * ID3v2.2 frames that have the same content as a 2.3 and 2.4 frame
	 */
	private static final Map<String, String>	V22			= Map.of( "TT2", "TIT2", "TP1", "TPE1", "TAL", "TALB", "TP2", "TPE2", "TCM", "TCOM", "TCO", "TCON",
	    "TYE", "TYER",
	    "TRK", "TRCK", "TPA", "TPOS", "COM", "COMM" );

	/**
	 * The 80 genres of ID3v1, which a tag may name by number
	 */
	private static final String[]				GENRES		= { "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz",
	    "Metal", "New Age",
	    "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack",
	    "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip",
	    "Gospel", "Noise", "AlternRock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic",
	    "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40",
	    "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave", "Psychadelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal",
	    "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock" };

	private Id3() {
	}

	/**
	 * One frame of a tag, with its data decoded of unsynchronisation and of the length indicator
	 */
	record Frame( String id, byte[] data ) {
	}

	/**
	 * A tag read from a file
	 *
	 * @param version The major version, 0 when the file has no tag
	 * @param audioAt Where the audio starts in the file
	 * @param frames  The frames in order
	 */
	record Parsed( int version, long audioAt, List<Frame> frames ) {
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Reading
	// ---------------------------------------------------------------------------------------------------------------------------

	private static int syncsafe( byte[] b, int at ) {
		return ( ( b[ at ] & 0x7F ) << 21 ) | ( ( b[ at + 1 ] & 0x7F ) << 14 ) | ( ( b[ at + 2 ] & 0x7F ) << 7 ) | ( b[ at + 3 ] & 0x7F );
	}

	private static int plain( byte[] b, int at ) {
		return ( ( b[ at ] & 0xFF ) << 24 ) | ( ( b[ at + 1 ] & 0xFF ) << 16 ) | ( ( b[ at + 2 ] & 0xFF ) << 8 ) | ( b[ at + 3 ] & 0xFF );
	}

	/**
	 * Undo unsynchronisation: every 0xFF 0x00 was written for 0xFF
	 */
	static byte[] deunsync( byte[] d ) {
		ByteArrayOutputStream out = new ByteArrayOutputStream( d.length );
		for ( int i = 0; i < d.length; i++ ) {
			out.write( d[ i ] );
			if ( d[ i ] == ( byte ) 0xFF && i + 1 < d.length && d[ i + 1 ] == 0 ) {
				i++;
			}
		}
		return out.toByteArray();
	}

	static Parsed parse( FileChannel channel ) throws IOException {
		ByteBuffer head = ByteBuffer.allocate( 10 );
		if ( read( channel, head, 0 ) < 10 || head.get( 0 ) != 'I' || head.get( 1 ) != 'D' || head.get( 2 ) != '3' ) {
			return new Parsed( 0, 0, new ArrayList<>() );
		}
		int version = head.get( 3 ) & 0xFF;
		if ( version < 2 || version > 4 ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "The ID3 tag is version 2." + version + ", which is not supported" );
		}
		int	flags	= head.get( 5 ) & 0xFF;
		int	size	= syncsafe( head.array(), 6 );
		if ( size > MAX_TAG ) {
			throw new AudioException( AudioException.CORRUPT_AUDIO, "The ID3 tag claims to be " + size + " bytes, which is more than is believable" );
		}
		long		audioAt	= 10L + size + ( version == 4 && ( flags & 0x10 ) != 0 ? 10 : 0 );
		ByteBuffer	body	= ByteBuffer.allocate( size );
		int			got		= read( channel, body, 10 );
		byte[]		tag		= java.util.Arrays.copyOf( body.array(), Math.max( got, 0 ) );
		if ( ( flags & 0x80 ) != 0 ) {
			tag = deunsync( tag );
		}
		int pos = 0;
		if ( ( flags & 0x40 ) != 0 && tag.length >= 4 ) {
			pos = version == 4 ? syncsafe( tag, 0 ) : 4 + plain( tag, 0 );
			if ( pos < 0 || pos > tag.length ) {
				throw new AudioException( AudioException.CORRUPT_AUDIO, "The ID3 extended header claims a size past the end of the tag" );
			}
		}
		List<Frame> frames = new ArrayList<>( parseFrames( tag, pos, tag.length, version ) );
		return new Parsed( version, audioAt, frames );
	}

	private static int read( FileChannel channel, ByteBuffer into, long position ) throws IOException {
		int total = 0;
		while ( into.hasRemaining() ) {
			int n = channel.read( into, position + total );
			if ( n < 0 ) {
				break;
			}
			total += n;
		}
		return total;
	}

	/**
	 * Frames between two offsets of a buffer. Compressed and encrypted frames are left out since they cannot be read.
	 */
	static List<Frame> parseFrames( byte[] tag, int from, int to, int version ) {
		List<Frame>	frames	= new ArrayList<>();
		int			header	= version == 2 ? 6 : 10;
		int			pos		= from;
		while ( pos + header <= to ) {
			if ( tag[ pos ] == 0 ) {
				break;
			}
			String	id;
			int		size;
			int		flags	= 0;
			if ( version == 2 ) {
				id		= new String( tag, pos, 3, StandardCharsets.ISO_8859_1 );
				size	= ( ( tag[ pos + 3 ] & 0xFF ) << 16 ) | ( ( tag[ pos + 4 ] & 0xFF ) << 8 ) | ( tag[ pos + 5 ] & 0xFF );
				id		= V22.getOrDefault( id, "v22:" + id );
			} else {
				id		= new String( tag, pos, 4, StandardCharsets.ISO_8859_1 );
				size	= version == 4 ? syncsafe( tag, pos + 4 ) : plain( tag, pos + 4 );
				flags	= ( ( tag[ pos + 8 ] & 0xFF ) << 8 ) | ( tag[ pos + 9 ] & 0xFF );
			}
			pos += header;
			if ( size < 0 || ( long ) pos + size > to ) {
				break;
			}
			byte[] data = java.util.Arrays.copyOfRange( tag, pos, pos + size );
			pos += size;
			boolean skip = false;
			if ( version == 4 ) {
				skip = ( flags & 0x0008 ) != 0 || ( flags & 0x0004 ) != 0;
				if ( !skip && ( flags & 0x0040 ) != 0 && data.length > 0 ) {
					data = java.util.Arrays.copyOfRange( data, 1, data.length );
				}
				if ( !skip && ( flags & 0x0002 ) != 0 ) {
					data = deunsync( data );
				}
				if ( !skip && ( flags & 0x0001 ) != 0 && data.length >= 4 ) {
					data = java.util.Arrays.copyOfRange( data, 4, data.length );
				}
			} else if ( version == 3 ) {
				skip = ( flags & 0x0080 ) != 0 || ( flags & 0x0040 ) != 0;
				if ( !skip && ( flags & 0x0020 ) != 0 && data.length > 0 ) {
					data = java.util.Arrays.copyOfRange( data, 1, data.length );
				}
			}
			if ( !skip ) {
				frames.add( new Frame( id, data ) );
			}
		}
		return frames;
	}

	private static Charset charset( int encoding ) {
		return switch ( encoding ) {
			case 1 -> StandardCharsets.UTF_16;
			case 2 -> StandardCharsets.UTF_16BE;
			case 3 -> StandardCharsets.UTF_8;
			default -> StandardCharsets.ISO_8859_1;
		};
	}

	/**
	 * The index of the end of a string that starts at {@code from} in the encoding, or the end of the data when there is none
	 */
	private static int terminator( byte[] d, int from, int encoding ) {
		if ( encoding == 1 || encoding == 2 ) {
			for ( int i = from; i + 1 < d.length; i += 2 ) {
				if ( d[ i ] == 0 && d[ i + 1 ] == 0 ) {
					return i;
				}
			}
			return d.length;
		}
		for ( int i = from; i < d.length; i++ ) {
			if ( d[ i ] == 0 ) {
				return i;
			}
		}
		return d.length;
	}

	private static String decode( byte[] d, int from, int to, int encoding ) {
		if ( to <= from ) {
			return "";
		}
		String s = new String( d, from, to - from, charset( encoding ) );
		// A byte order mark that was left in, and the NULs a writer put at the end
		s = s.replace( "﻿", "" );
		int end = s.length();
		while ( end > 0 && s.charAt( end - 1 ) == 0 ) {
			end--;
		}
		return s.substring( 0, end );
	}

	/**
	 * The text of a text frame: the values after the encoding byte, several values joined by a comma
	 */
	static String text( byte[] d ) {
		if ( d.length < 2 ) {
			return "";
		}
		int				encoding	= d[ 0 ] & 0xFF;
		List<String>	values		= new ArrayList<>();
		int				pos			= 1;
		int				width		= encoding == 1 || encoding == 2 ? 2 : 1;
		while ( pos < d.length ) {
			int		end	= terminator( d, pos, encoding );
			String	v	= decode( d, pos, end, encoding );
			if ( !v.isEmpty() ) {
				values.add( v );
			}
			pos = end + width;
		}
		return String.join( ", ", values );
	}

	private static String genre( String raw ) {
		java.util.regex.Matcher	m		= java.util.regex.Pattern.compile( "^\\((\\d+)\\)(.*)$" ).matcher( raw );
		String					number	= null;
		String					rest	= raw;
		if ( m.matches() ) {
			number	= m.group( 1 );
			rest	= m.group( 2 );
		} else if ( raw.matches( "\\d+" ) ) {
			number	= raw;
			rest	= "";
		}
		if ( !rest.isBlank() ) {
			return rest;
		}
		if ( number != null ) {
			int n = Integer.parseInt( number );
			return n < GENRES.length ? GENRES[ n ] : raw;
		}
		return raw;
	}

	/**
	 * Read the tags of an MP3 file
	 */
	public static TagSet read( FileChannel channel ) throws IOException {
		Parsed	parsed	= parse( channel );
		TagSet	tags	= new TagSet();
		for ( Frame f : parsed.frames() ) {
			switch ( f.id() ) {
				case "TIT2" -> tags.text.putIfAbsent( "title", text( f.data() ) );
				case "TPE1" -> tags.text.putIfAbsent( "artist", text( f.data() ) );
				case "TALB" -> tags.text.putIfAbsent( "album", text( f.data() ) );
				case "TPE2" -> tags.text.putIfAbsent( "albumArtist", text( f.data() ) );
				case "TCOM" -> tags.text.putIfAbsent( "composer", text( f.data() ) );
				case "TCON" -> tags.text.putIfAbsent( "genre", genre( text( f.data() ) ) );
				case "TDRC", "TYER" -> tags.text.putIfAbsent( "year", text( f.data() ) );
				case "TRCK" -> tags.text.putIfAbsent( "track", text( f.data() ) );
				case "TPOS" -> tags.text.putIfAbsent( "disc", text( f.data() ) );
				case "COMM" -> {
					// Other programs keep their own data in comment frames that have a description, such as iTunNORM
					if ( commentDescription( f.data() ).isEmpty() ) {
						String c = comment( f.data() );
						if ( c != null ) {
							tags.text.putIfAbsent( "comment", c );
						}
					}
				}
				case "APIC" -> {
					Object[] picture = picture( f.data() );
					if ( picture != null && ( tags.cover == null || ( int ) picture[ 0 ] == 3 ) ) {
						tags.cover = new TagSet.Cover( ( String ) picture[ 1 ], ( byte[] ) picture[ 2 ] );
					}
				}
				default -> {
				}
			}
		}
		List<TagSet.Chapter> chapters = chapters( parsed );
		if ( !chapters.isEmpty() ) {
			tags.chapters = chapters;
		}
		return tags;
	}

	/**
	 * The text of a comment frame whose description is empty, or the first one
	 */
	private static String comment( byte[] d ) {
		if ( d.length < 5 ) {
			return null;
		}
		int	encoding	= d[ 0 ] & 0xFF;
		int	width		= encoding == 1 || encoding == 2 ? 2 : 1;
		int	descEnd		= terminator( d, 4, encoding );
		return decode( d, Math.min( d.length, descEnd + width ), d.length, encoding );
	}

	private static String commentDescription( byte[] d ) {
		if ( d.length < 5 ) {
			return "";
		}
		int encoding = d[ 0 ] & 0xFF;
		return decode( d, 4, terminator( d, 4, encoding ), encoding );
	}

	/**
	 * @return { picture type, mime type, data } or null
	 */
	private static Object[] picture( byte[] d ) {
		if ( d.length < 4 ) {
			return null;
		}
		int	encoding	= d[ 0 ] & 0xFF;
		int	mimeEnd		= terminator( d, 1, 0 );
		if ( mimeEnd + 2 >= d.length ) {
			return null;
		}
		String	mime	= new String( d, 1, mimeEnd - 1, StandardCharsets.ISO_8859_1 );
		int		type	= d[ mimeEnd + 1 ] & 0xFF;
		int		width	= encoding == 1 || encoding == 2 ? 2 : 1;
		int		descEnd	= terminator( d, mimeEnd + 2, encoding );
		int		start	= Math.min( d.length, descEnd + width );
		if ( mime.equalsIgnoreCase( "JPG" ) ) {
			mime = "image/jpeg";
		} else if ( mime.equalsIgnoreCase( "PNG" ) ) {
			mime = "image/png";
		}
		return new Object[] { type, mime, java.util.Arrays.copyOfRange( d, start, d.length ) };
	}

	private static List<TagSet.Chapter> chapters( Parsed parsed ) {
		List<TagSet.Chapter> result = new ArrayList<>();
		for ( Frame f : parsed.frames() ) {
			if ( !f.id().equals( "CHAP" ) ) {
				continue;
			}
			byte[]	d		= f.data();
			int		idEnd	= terminator( d, 0, 0 );
			int		at		= idEnd + 1;
			if ( at + 16 > d.length ) {
				continue;
			}
			double	start	= ( plain( d, at ) & 0xFFFFFFFFL ) / 1000.0;
			double	end		= ( plain( d, at + 4 ) & 0xFFFFFFFFL ) / 1000.0;
			String	title	= "";
			for ( Frame sub : parseFrames( d, at + 16, d.length, parsed.version() == 3 ? 3 : 4 ) ) {
				if ( sub.id().equals( "TIT2" ) ) {
					title = text( sub.data() );
					break;
				}
			}
			result.add( new TagSet.Chapter( start, end > start ? end : null, title ) );
		}
		result.sort( java.util.Comparator.comparingDouble( TagSet.Chapter::start ) );
		return result;
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Writing
	// ---------------------------------------------------------------------------------------------------------------------------

	private static byte[] textFrame( String id, String value ) {
		byte[]	utf8	= value.getBytes( StandardCharsets.UTF_8 );
		byte[]	data	= new byte[ utf8.length + 1 ];
		data[ 0 ] = 3;
		System.arraycopy( utf8, 0, data, 1, utf8.length );
		return frame( id, data );
	}

	private static byte[] frame( String id, byte[] data ) {
		ByteArrayOutputStream out = new ByteArrayOutputStream( data.length + 10 );
		out.writeBytes( id.getBytes( StandardCharsets.ISO_8859_1 ) );
		out.writeBytes( syncsafeBytes( data.length ) );
		out.write( 0 );
		out.write( 0 );
		out.writeBytes( data );
		return out.toByteArray();
	}

	private static byte[] syncsafeBytes( int n ) {
		if ( n < 0 || n >= 1 << 28 ) {
			throw new AudioException( AudioException.TOO_LARGE, "The ID3 tag would be larger than 256 MB" );
		}
		return new byte[] { ( byte ) ( ( n >> 21 ) & 0x7F ), ( byte ) ( ( n >> 14 ) & 0x7F ), ( byte ) ( ( n >> 7 ) & 0x7F ), ( byte ) ( n & 0x7F ) };
	}

	private static byte[] be32( long n ) {
		return new byte[] { ( byte ) ( n >> 24 ), ( byte ) ( n >> 16 ), ( byte ) ( n >> 8 ), ( byte ) n };
	}

	private static byte[] commentFrame( String value ) {
		byte[]					utf8	= value.getBytes( StandardCharsets.UTF_8 );
		ByteArrayOutputStream	data	= new ByteArrayOutputStream();
		data.write( 3 );
		data.writeBytes( "eng".getBytes( StandardCharsets.ISO_8859_1 ) );
		data.write( 0 );
		data.writeBytes( utf8 );
		return frame( "COMM", data.toByteArray() );
	}

	private static byte[] pictureFrame( TagSet.Cover cover ) {
		ByteArrayOutputStream data = new ByteArrayOutputStream( cover.data().length + 32 );
		data.write( 0 );
		data.writeBytes( cover.mimeType().getBytes( StandardCharsets.ISO_8859_1 ) );
		data.write( 0 );
		data.write( 3 );
		data.write( 0 );
		data.writeBytes( cover.data() );
		return frame( "APIC", data.toByteArray() );
	}

	private static List<byte[]> chapterFrames( List<TagSet.Chapter> chapters, double totalSeconds ) {
		List<byte[]>	frames	= new ArrayList<>();
		List<String>	ids		= new ArrayList<>();
		for ( int i = 0; i < chapters.size(); i++ ) {
			TagSet.Chapter	c	= chapters.get( i );
			double			end	= c.end() != null ? c.end() : ( i + 1 < chapters.size() ? chapters.get( i + 1 ).start() : totalSeconds );
			if ( end < c.start() ) {
				end = c.start();
			}
			String id = "chp" + i;
			ids.add( id );
			ByteArrayOutputStream data = new ByteArrayOutputStream();
			data.writeBytes( id.getBytes( StandardCharsets.ISO_8859_1 ) );
			data.write( 0 );
			data.writeBytes( be32( Math.round( c.start() * 1000 ) ) );
			data.writeBytes( be32( Math.round( end * 1000 ) ) );
			data.writeBytes( be32( 0xFFFFFFFFL ) );
			data.writeBytes( be32( 0xFFFFFFFFL ) );
			if ( c.title() != null && !c.title().isEmpty() ) {
				data.writeBytes( textFrame( "TIT2", c.title() ) );
			}
			frames.add( frame( "CHAP", data.toByteArray() ) );
		}
		ByteArrayOutputStream toc = new ByteArrayOutputStream();
		toc.writeBytes( "toc".getBytes( StandardCharsets.ISO_8859_1 ) );
		toc.write( 0 );
		toc.write( 0x03 );
		toc.write( ids.size() );
		for ( String id : ids ) {
			toc.writeBytes( id.getBytes( StandardCharsets.ISO_8859_1 ) );
			toc.write( 0 );
		}
		frames.add( 0, frame( "CTOC", toc.toByteArray() ) );
		return frames;
	}

	/**
	 * The data of a CHAP or CTOC frame of a version 2.3 tag with its sub frames written the way version 2.4 does, since their sizes are not coded the
	 * same
	 */
	private static byte[] subframesAs24( String id, byte[] d ) {
		int	idEnd	= terminator( d, 0, 0 );
		int	head;
		if ( id.equals( "CHAP" ) ) {
			head = idEnd + 1 + 16;
		} else {
			if ( idEnd + 3 > d.length ) {
				return d;
			}
			head = idEnd + 3;
			for ( int i = 0; i < ( d[ idEnd + 2 ] & 0xFF ) && head < d.length; i++ ) {
				head = terminator( d, head, 0 ) + 1;
			}
		}
		if ( head > d.length ) {
			return d;
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream( d.length + 16 );
		out.write( d, 0, head );
		for ( Frame sub : parseFrames( d, head, d.length, 3 ) ) {
			out.writeBytes( frame( sub.id(), sub.data() ) );
		}
		return out.toByteArray();
	}

	/**
	 * Write a copy of an MP3 file with an edit applied to its tag
	 *
	 * @param source The file
	 * @param target Where the copy goes
	 * @param edit   The tags to set or remove, the rest is kept
	 */
	public static void write( FileChannel source, FileChannel target, TagSet edit ) throws IOException {
		Parsed					parsed	= parse( source );
		List<byte[]>			out		= new ArrayList<>();

		java.util.Set<String>	removed	= new java.util.HashSet<>();
		for ( Map.Entry<String, String> e : edit.text.entrySet() ) {
			if ( e.getKey().equals( "comment" ) ) {
				removed.add( "COMM" );
			} else if ( e.getKey().equals( "year" ) ) {
				removed.add( "TDRC" );
				removed.add( "TYER" );
			} else {
				removed.add( FRAMES.get( e.getKey() ) );
			}
		}
		boolean hasYear = parsed.frames().stream().anyMatch( f -> f.id().equals( "TDRC" ) );
		for ( Frame f : parsed.frames() ) {
			String id = f.id();
			if ( id.startsWith( "v22:" ) || OBSOLETE.contains( id ) || ( id.equals( "CHAP" ) || id.equals( "CTOC" ) ) && edit.chapters != null
			    || id.equals( "APIC" ) && edit.coverTouched
			    || removed.contains( id ) && ! ( id.equals( "COMM" ) && !commentDescription( f.data() ).isEmpty() ) ) {
				continue;
			}
			if ( id.equals( "TYER" ) ) {
				if ( hasYear ) {
					continue;
				}
				id = "TDRC";
			}
			out.add( frame( id, parsed.version() == 3 && ( id.equals( "CHAP" ) || id.equals( "CTOC" ) ) ? subframesAs24( id, f.data() ) : f.data() ) );
		}
		for ( Map.Entry<String, String> e : edit.text.entrySet() ) {
			if ( e.getValue() == null || e.getValue().isEmpty() ) {
				continue;
			}
			out.add( e.getKey().equals( "comment" ) ? commentFrame( e.getValue() ) : textFrame( FRAMES.get( e.getKey() ), e.getValue() ) );
		}
		if ( edit.coverTouched && edit.cover != null ) {
			out.add( pictureFrame( edit.cover ) );
		}
		if ( edit.chapters != null && !edit.chapters.isEmpty() ) {
			boolean needsTotal = edit.chapters.get( edit.chapters.size() - 1 ).end() == null;
			out.addAll( chapterFrames( edit.chapters, needsTotal ? Mp3Duration.of( source, parsed.audioAt() ) : 0 ) );
		}

		int size = 0;
		for ( byte[] f : out ) {
			size += f.length;
		}
		ByteArrayOutputStream tag = new ByteArrayOutputStream( size + 10 );
		if ( size > 0 ) {
			tag.writeBytes( new byte[] { 'I', 'D', '3', 4, 0, 0 } );
			tag.writeBytes( syncsafeBytes( size ) );
			for ( byte[] f : out ) {
				tag.writeBytes( f );
			}
		}
		target.write( ByteBuffer.wrap( tag.toByteArray() ) );
		long	position	= parsed.audioAt();
		long	length		= source.size();
		while ( position < length ) {
			position += source.transferTo( position, length - position, target );
		}
	}

}
