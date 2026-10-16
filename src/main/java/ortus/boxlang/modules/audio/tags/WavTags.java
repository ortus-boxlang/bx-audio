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
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Tags of WAV files: the INFO list for the text and cue points with labels for the chapters. A WAV file has no standard place for a cover or for the
 * album artist and the disc, so those are not stored.
 */
public final class WavTags {

	/**
	 * The INFO chunk of each tag
	 */
	private static final Map<String, String> INFO = Map.of( "title", "INAM", "artist", "IART", "album", "IPRD", "year", "ICRD", "track", "ITRK", "genre",
	    "IGNR",
	    "comment", "ICMT", "composer", "IMUS" );

	private WavTags() {
	}

	/**
	 * One chunk: where it is in the file, its id and its size. LIST chunks also have their form type.
	 */
	private record Chunk( long at, String id, long size, String form ) {
	}

	private static String id( ByteBuffer b, int at ) {
		return new String( b.array(), at, 4, StandardCharsets.ISO_8859_1 );
	}

	private static List<Chunk> chunks( FileChannel channel ) throws IOException {
		ByteBuffer head = ByteBuffer.allocate( 12 ).order( ByteOrder.LITTLE_ENDIAN );
		if ( channel.read( head, 0 ) < 12 || !id( head, 0 ).equals( "RIFF" ) || !id( head, 8 ).equals( "WAVE" ) ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "Tags can be read and written for WAV files that are RIFF, this one is not" );
		}
		List<Chunk>	result	= new ArrayList<>();
		long		pos		= 12;
		long		length	= channel.size();
		while ( pos + 8 <= length ) {
			ByteBuffer h = ByteBuffer.allocate( 8 ).order( ByteOrder.LITTLE_ENDIAN );
			channel.read( h, pos );
			String	id		= id( h, 0 );
			long	size	= h.getInt( 4 ) & 0xFFFFFFFFL;
			// A data chunk whose size was never filled in (a streamed file) lasts to the end
			if ( pos + 8 + size > length || size == 0xFFFFFFFFL ) {
				size = length - pos - 8;
			}
			String form = null;
			if ( id.equals( "LIST" ) && size >= 4 ) {
				ByteBuffer f = ByteBuffer.allocate( 4 );
				channel.read( f, pos + 8 );
				form = new String( f.array(), StandardCharsets.ISO_8859_1 );
			}
			result.add( new Chunk( pos, id, size, form ) );
			pos += 8 + size + ( size & 1 );
		}
		return result;
	}

	private static byte[] body( FileChannel channel, Chunk c ) throws IOException {
		if ( c.size() > 64 * 1024 * 1024 ) {
			throw new AudioException( AudioException.CORRUPT_AUDIO,
			    "The WAV chunk " + c.id() + " is " + c.size() + " bytes, which is more than is believable" );
		}
		ByteBuffer	b	= ByteBuffer.allocate( ( int ) c.size() );
		int			got	= 0;
		while ( b.hasRemaining() ) {
			int n = channel.read( b, c.at() + 8 + got );
			if ( n < 0 ) {
				break;
			}
			got += n;
		}
		return b.array();
	}

	private static int le32( byte[] b, int at ) {
		return ( b[ at ] & 0xFF ) | ( ( b[ at + 1 ] & 0xFF ) << 8 ) | ( ( b[ at + 2 ] & 0xFF ) << 16 ) | ( ( b[ at + 3 ] & 0xFF ) << 24 );
	}

	/**
	 * The entries of an INFO list, in order, with an id such as INAM and its text
	 */
	private static Map<String, String> info( byte[] list ) {
		Map<String, String>	entries	= new LinkedHashMap<>();
		int					pos		= 4;
		while ( pos + 8 <= list.length ) {
			String	id		= new String( list, pos, 4, StandardCharsets.ISO_8859_1 );
			int		size	= le32( list, pos + 4 );
			pos += 8;
			if ( size < 0 || pos + size > list.length ) {
				break;
			}
			int end = pos + size;
			while ( end > pos && list[ end - 1 ] == 0 ) {
				end--;
			}
			entries.put( id, new String( list, pos, end - pos, StandardCharsets.UTF_8 ) );
			pos += size + ( size & 1 );
		}
		return entries;
	}

	/**
	 * Read the tags of a WAV file
	 */
	public static TagSet read( FileChannel channel ) throws IOException {
		TagSet				tags	= new TagSet();
		int					rate	= 0;
		List<long[]>		points	= new ArrayList<>();
		Map<Long, String>	labels	= new LinkedHashMap<>();
		for ( Chunk c : chunks( channel ) ) {
			if ( c.id().equals( "fmt " ) && c.size() >= 8 ) {
				rate = le32( body( channel, c ), 4 );
			} else if ( c.id().equals( "LIST" ) && "INFO".equals( c.form() ) ) {
				Map<String, String> entries = info( body( channel, c ) );
				for ( var e : INFO.entrySet() ) {
					if ( entries.containsKey( e.getValue() ) ) {
						tags.text.put( e.getKey(), entries.get( e.getValue() ) );
					}
				}
			} else if ( c.id().equals( "cue " ) && c.size() >= 4 ) {
				byte[]	b		= body( channel, c );
				int		count	= le32( b, 0 );
				for ( int i = 0; i < count && 4 + ( i + 1 ) * 24 <= b.length; i++ ) {
					int at = 4 + i * 24;
					points.add( new long[] { le32( b, at ) & 0xFFFFFFFFL, le32( b, at + 20 ) & 0xFFFFFFFFL } );
				}
			} else if ( c.id().equals( "LIST" ) && "adtl".equals( c.form() ) ) {
				byte[]	b	= body( channel, c );
				int		pos	= 4;
				while ( pos + 8 <= b.length ) {
					String	id		= new String( b, pos, 4, StandardCharsets.ISO_8859_1 );
					int		size	= le32( b, pos + 4 );
					pos += 8;
					if ( size < 0 || pos + size > b.length ) {
						break;
					}
					if ( id.equals( "labl" ) && size >= 4 ) {
						int end = pos + size;
						while ( end > pos + 4 && b[ end - 1 ] == 0 ) {
							end--;
						}
						labels.put( le32( b, pos ) & 0xFFFFFFFFL, new String( b, pos + 4, end - pos - 4, StandardCharsets.UTF_8 ) );
					}
					pos += size + ( size & 1 );
				}
			}
		}
		if ( !points.isEmpty() && rate > 0 ) {
			List<TagSet.Chapter> chapters = new ArrayList<>();
			for ( long[] p : points ) {
				chapters.add( new TagSet.Chapter( p[ 1 ] / ( double ) rate, null, labels.getOrDefault( p[ 0 ], "" ) ) );
			}
			chapters.sort( java.util.Comparator.comparingDouble( TagSet.Chapter::start ) );
			tags.chapters = chapters;
		}
		return tags;
	}

	private static void le32( ByteArrayOutputStream out, long n ) {
		out.write( ( int ) n );
		out.write( ( int ) ( n >> 8 ) );
		out.write( ( int ) ( n >> 16 ) );
		out.write( ( int ) ( n >> 24 ) );
	}

	private static void chunk( ByteArrayOutputStream out, String id, byte[] body ) {
		out.writeBytes( id.getBytes( StandardCharsets.ISO_8859_1 ) );
		le32( out, body.length );
		out.writeBytes( body );
		if ( ( body.length & 1 ) != 0 ) {
			out.write( 0 );
		}
	}

	/**
	 * Write a copy of a WAV file with an edit applied to its tags. The audio is copied untouched.
	 */
	public static void write( FileChannel source, FileChannel target, TagSet edit ) throws IOException {
		List<Chunk>			chunks		= chunks( source );
		int					rate		= 0;
		Map<String, String>	entries		= new LinkedHashMap<>();
		boolean				textTouched	= !edit.text.isEmpty();
		for ( Chunk c : chunks ) {
			if ( c.id().equals( "fmt " ) && c.size() >= 8 ) {
				rate = le32( body( source, c ), 4 );
			} else if ( c.id().equals( "LIST" ) && "INFO".equals( c.form() ) ) {
				entries.putAll( info( body( source, c ) ) );
			}
		}
		if ( edit.chapters != null && !edit.chapters.isEmpty() && rate <= 0 ) {
			throw new AudioException( AudioException.CORRUPT_AUDIO, "The WAV file has no format chunk, so the chapter times cannot be turned into samples" );
		}
		for ( var e : edit.text.entrySet() ) {
			String chunkId = INFO.get( e.getKey() );
			if ( chunkId == null ) {
				continue;
			}
			if ( e.getValue() == null || e.getValue().isEmpty() ) {
				entries.remove( chunkId );
			} else {
				entries.put( chunkId, e.getValue() );
			}
		}

		ByteArrayOutputStream tail = new ByteArrayOutputStream();
		if ( !entries.isEmpty() ) {
			ByteArrayOutputStream list = new ByteArrayOutputStream();
			list.writeBytes( "INFO".getBytes( StandardCharsets.ISO_8859_1 ) );
			for ( var e : entries.entrySet() ) {
				byte[]	text	= e.getValue().getBytes( StandardCharsets.UTF_8 );
				byte[]	body	= java.util.Arrays.copyOf( text, text.length + 1 );
				chunk( list, e.getKey(), body );
			}
			chunk( tail, "LIST", list.toByteArray() );
		}
		if ( edit.chapters != null && !edit.chapters.isEmpty() ) {
			ByteArrayOutputStream cue = new ByteArrayOutputStream();
			le32( cue, edit.chapters.size() );
			ByteArrayOutputStream adtl = new ByteArrayOutputStream();
			adtl.writeBytes( "adtl".getBytes( StandardCharsets.ISO_8859_1 ) );
			for ( int i = 0; i < edit.chapters.size(); i++ ) {
				long sample = Math.round( edit.chapters.get( i ).start() * rate );
				le32( cue, i + 1 );
				le32( cue, sample );
				cue.writeBytes( "data".getBytes( StandardCharsets.ISO_8859_1 ) );
				le32( cue, 0 );
				le32( cue, 0 );
				le32( cue, sample );
				String					title	= edit.chapters.get( i ).title() == null ? "" : edit.chapters.get( i ).title();
				byte[]					text	= title.getBytes( StandardCharsets.UTF_8 );
				ByteArrayOutputStream	label	= new ByteArrayOutputStream();
				le32( label, i + 1 );
				label.writeBytes( text );
				label.write( 0 );
				chunk( adtl, "labl", label.toByteArray() );
			}
			chunk( tail, "cue ", cue.toByteArray() );
			chunk( tail, "LIST", adtl.toByteArray() );
		}

		// The header is written again at the end, when the size is known
		ByteBuffer riff = ByteBuffer.allocate( 12 ).order( ByteOrder.LITTLE_ENDIAN );
		riff.put( "RIFF".getBytes( StandardCharsets.ISO_8859_1 ) ).putInt( 0 ).put( "WAVE".getBytes( StandardCharsets.ISO_8859_1 ) ).flip();
		target.write( riff );
		for ( Chunk c : chunks ) {
			boolean	info	= c.id().equals( "LIST" ) && "INFO".equals( c.form() );
			boolean	adtl	= c.id().equals( "LIST" ) && "adtl".equals( c.form() );
			if ( info && textTouched || ( c.id().equals( "cue " ) || adtl ) && edit.chapters != null ) {
				continue;
			}
			if ( info && !textTouched ) {
				// kept as it is, with the entries we read, so nothing about it changes
				continue;
			}
			ByteBuffer h = ByteBuffer.allocate( 8 ).order( ByteOrder.LITTLE_ENDIAN );
			h.put( c.id().getBytes( StandardCharsets.ISO_8859_1 ) ).putInt( ( int ) c.size() ).flip();
			target.write( h );
			long	position	= c.at() + 8;
			long	end			= position + c.size();
			while ( position < end ) {
				position += source.transferTo( position, end - position, target );
			}
			if ( ( c.size() & 1 ) != 0 ) {
				target.write( ByteBuffer.wrap( new byte[] { 0 } ) );
			}
		}
		target.write( ByteBuffer.wrap( tail.toByteArray() ) );
		long total = target.size();
		if ( total - 8 > 0xFFFFFFFFL ) {
			throw new AudioException( AudioException.TOO_LARGE, "The WAV file would be larger than 4 GB" );
		}
		ByteBuffer size = ByteBuffer.allocate( 4 ).order( ByteOrder.LITTLE_ENDIAN );
		size.putInt( ( int ) ( total - 8 ) ).flip();
		target.write( size, 4 );
	}

}
