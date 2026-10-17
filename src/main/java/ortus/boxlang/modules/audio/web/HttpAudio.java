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
package ortus.boxlang.modules.audio.web;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import ortus.boxlang.modules.audio.util.AudioException;

/**
 * An HTTP response for audio: the status, headers and body to send for a request, with the parts that browsers need to play and seek audio.
 * Range requests answer 206 with just the bytes asked for, a repeated request with a matching ETag answers 304, and HEAD answers the headers only.
 *
 * <p>
 * It does not send anything. The caller copies the status, headers and body into whatever web server runs the code.
 */
public final class HttpAudio {

	/** The most a single range answer carries, 16 MB */
	private static final long				MAX_SLICE	= 16L * 1024 * 1024;
	/** The largest file sent whole, 1 GB, because the body is held in memory */
	private static final long				MAX_WHOLE	= 1024L * 1024 * 1024;

	private static final DateTimeFormatter	HTTP_DATE	= DateTimeFormatter.ofPattern( "EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US );

	private HttpAudio() {
	}

	/**
	 * What is sent: the status line number and text, the headers in a stable order and the body (empty for 304, 416 and HEAD)
	 */
	public record Result( int status, String statusText, Map<String, String> headers, byte[] body ) {
	}

	/**
	 * Settings of the response that do not come from the request
	 *
	 * @param contentType   The media type
	 * @param maxAgeSeconds Greater than 0: cacheable for that long. 0: must be revalidated (an ETag makes that cheap). Less than 0: not stored.
	 * @param filename      Offered to a browser that saves the file, may be empty
	 * @param download      True to ask the browser to save the file instead of playing it
	 */
	public record Options( String contentType, long maxAgeSeconds, String filename, boolean download ) {
	}

	/**
	 * Something that can be served: a whole file in memory, or a file on disk of which only the asked slice is read
	 */
	public interface Content {

		long length();

		/**
		 * @return The bytes from first to last, both inclusive
		 */
		byte[] read( long first, long last );

		String etag();

		/**
		 * @return When it last changed, or null when that is not known
		 */
		Instant lastModified();
	}

	/**
	 * Content held in memory. The ETag is a strong one: a hash of the bytes.
	 */
	public static Content of( byte[] bytes ) {
		String tag;
		try {
			byte[]			hash	= MessageDigest.getInstance( "SHA-256" ).digest( bytes );
			StringBuilder	hex		= new StringBuilder( 32 );
			for ( int i = 0; i < 16; i++ ) {
				hex.append( String.format( "%02x", hash[ i ] ) );
			}
			tag = "\"" + hex + "\"";
		} catch ( NoSuchAlgorithmException e ) {
			throw new AudioException( AudioException.INTERNAL, "This JVM has no SHA-256 digest", e );
		}
		final String etag = tag;
		return new Content() {

			@Override
			public long length() {
				return bytes.length;
			}

			@Override
			public byte[] read( long first, long last ) {
				return java.util.Arrays.copyOfRange( bytes, ( int ) first, ( int ) last + 1 );
			}

			@Override
			public String etag() {
				return etag;
			}

			@Override
			public Instant lastModified() {
				return null;
			}
		};
	}

	/**
	 * Content on disk. Only the bytes that are asked for are read, so a seek in a long file costs the slice and not the file. The ETag is a weak
	 * one made of the size and the modification time, so the file is never read just to answer 304.
	 */
	public static Content of( Path file ) {
		long	size;
		long	modified;
		try {
			size		= Files.size( file );
			modified	= Files.getLastModifiedTime( file ).toMillis();
		} catch ( IOException e ) {
			throw new AudioException( AudioException.IO, "Could not read [" + file + "]: " + e.getMessage(), e );
		}
		final String etag = "W/\"" + Long.toHexString( size ) + "-" + Long.toHexString( modified ) + "\"";
		return new Content() {

			@Override
			public long length() {
				return size;
			}

			@Override
			public byte[] read( long first, long last ) {
				try ( FileChannel channel = FileChannel.open( file, StandardOpenOption.READ ) ) {
					ByteBuffer buffer = ByteBuffer.allocate( ( int ) ( last - first + 1 ) );
					while ( buffer.hasRemaining() ) {
						int n = channel.read( buffer, first + buffer.position() );
						if ( n < 0 ) {
							break;
						}
					}
					return buffer.array();
				} catch ( IOException e ) {
					throw new AudioException( AudioException.IO, "Could not read [" + file + "]: " + e.getMessage(), e );
				}
			}

			@Override
			public String etag() {
				return etag;
			}

			@Override
			public Instant lastModified() {
				return Instant.ofEpochMilli( modified / 1000 * 1000 );
			}
		};
	}

	/**
	 * Answer a request.
	 *
	 * @param method         The HTTP method, GET and HEAD are served
	 * @param requestHeaders The request headers, with lower case names
	 */
	public static Result respond( String method, Map<String, String> requestHeaders, Content content, Options options ) {
		String				verb	= method == null || method.isBlank() ? "GET" : method.trim().toUpperCase( Locale.ROOT );
		Map<String, String>	headers	= new LinkedHashMap<>();
		if ( !verb.equals( "GET" ) && !verb.equals( "HEAD" ) ) {
			headers.put( "Allow", "GET, HEAD" );
			headers.put( "Content-Length", "0" );
			return new Result( 405, "Method Not Allowed", headers, new byte[ 0 ] );
		}
		long	length	= content.length();
		String	etag	= content.etag();
		headers.put( "Content-Type", options.contentType() );
		headers.put( "Accept-Ranges", "bytes" );
		headers.put( "ETag", etag );
		headers.put( "Cache-Control", cacheControl( options.maxAgeSeconds() ) );
		if ( content.lastModified() != null ) {
			headers.put( "Last-Modified", HTTP_DATE.format( ZonedDateTime.ofInstant( content.lastModified(), ZoneOffset.UTC ) ) );
		}
		String disposition = disposition( options );
		if ( disposition != null ) {
			headers.put( "Content-Disposition", disposition );
		}

		// Unchanged since the client last got it: say so and send nothing
		String	noneMatch	= requestHeaders.get( "if-none-match" );
		boolean	notModified	= false;
		if ( noneMatch != null ) {
			notModified = matchesAny( noneMatch, etag, false );
		} else if ( requestHeaders.get( "if-modified-since" ) != null && content.lastModified() != null ) {
			Instant since = parseDate( requestHeaders.get( "if-modified-since" ) );
			notModified = since != null && !content.lastModified().isAfter( since );
		}
		if ( notModified ) {
			headers.remove( "Content-Type" );
			return new Result( 304, "Not Modified", headers, new byte[ 0 ] );
		}

		// A range is honoured when there is one, there is only one, and If-Range (if present) says the file is still the same
		String	range	= requestHeaders.get( "range" );
		long[]	bounds	= null;
		if ( range != null && range.trim().toLowerCase( Locale.ROOT ).startsWith( "bytes=" ) && ifRangeAllows( requestHeaders.get( "if-range" ), content ) ) {
			String spec = range.trim().substring( 6 ).trim();
			if ( !spec.contains( "," ) ) {
				bounds = parseRange( spec, length );
				if ( bounds == null ) {
					headers.clear();
					headers.put( "Content-Range", "bytes */" + length );
					headers.put( "Content-Length", "0" );
					return new Result( 416, "Range Not Satisfiable", headers, new byte[ 0 ] );
				}
			}
		}
		if ( bounds != null ) {
			// A shorter slice than asked for is a legal answer, and it keeps an open ended request from loading the rest of a big file
			bounds[ 1 ] = Math.min( bounds[ 1 ], bounds[ 0 ] + MAX_SLICE - 1 );
			headers.put( "Content-Range", "bytes " + bounds[ 0 ] + "-" + bounds[ 1 ] + "/" + length );
			headers.put( "Content-Length", String.valueOf( bounds[ 1 ] - bounds[ 0 ] + 1 ) );
			byte[] body = verb.equals( "HEAD" ) ? new byte[ 0 ] : content.read( bounds[ 0 ], bounds[ 1 ] );
			return new Result( 206, "Partial Content", headers, body );
		}
		if ( length > MAX_WHOLE && !verb.equals( "HEAD" ) ) {
			throw new AudioException( AudioException.TOO_LARGE,
			    "The file is " + length + " bytes, which is too large to send in one piece. Ask for it with Range requests." );
		}
		headers.put( "Content-Length", String.valueOf( length ) );
		byte[] body = verb.equals( "HEAD" ) || length == 0 ? new byte[ 0 ] : content.read( 0, length - 1 );
		return new Result( 200, "OK", headers, body );
	}

	/**
	 * Parse the part of a Range header after {@code bytes=}: {@code 0-99}, {@code 100-} or {@code -500}
	 *
	 * @return The first and last byte, inclusive, or null when the range cannot be satisfied or makes no sense
	 */
	static long[] parseRange( String spec, long length ) {
		int dash = spec.indexOf( '-' );
		if ( dash < 0 || length == 0 ) {
			return null;
		}
		String	from	= spec.substring( 0, dash ).trim();
		String	to		= spec.substring( dash + 1 ).trim();
		try {
			if ( from.isEmpty() ) {
				// The last N bytes
				long n = Long.parseLong( to );
				if ( n <= 0 ) {
					return null;
				}
				return new long[] { Math.max( 0, length - n ), length - 1 };
			}
			long	first	= Long.parseLong( from );
			long	last	= to.isEmpty() ? length - 1 : Math.min( Long.parseLong( to ), length - 1 );
			if ( first < 0 || first >= length || last < first ) {
				return null;
			}
			return new long[] { first, last };
		} catch ( NumberFormatException e ) {
			return null;
		}
	}

	/**
	 * If-Range holds an ETag or a date. Only an exact match of a strong ETag, or the exact modification date, lets the range through.
	 */
	private static boolean ifRangeAllows( String ifRange, Content content ) {
		if ( ifRange == null || ifRange.isBlank() ) {
			return true;
		}
		String value = ifRange.trim();
		if ( value.startsWith( "\"" ) || value.startsWith( "W/" ) ) {
			return matchesAny( value, content.etag(), true );
		}
		Instant date = parseDate( value );
		return date != null && content.lastModified() != null && content.lastModified().equals( date );
	}

	/**
	 * Does a list of entity tags (or *) contain the tag. Weak comparison ignores the W/ prefix, strong comparison needs two strong tags.
	 */
	static boolean matchesAny( String header, String etag, boolean strong ) {
		String list = header.trim();
		if ( list.equals( "*" ) ) {
			return true;
		}
		boolean	etagWeak	= etag.startsWith( "W/" );
		String	bare		= etagWeak ? etag.substring( 2 ) : etag;
		for ( String candidate : list.split( "," ) ) {
			String	c		= candidate.trim();
			boolean	weak	= c.startsWith( "W/" );
			String	cb		= weak ? c.substring( 2 ) : c;
			if ( cb.equals( bare ) && ( !strong || ( !weak && !etagWeak ) ) ) {
				return true;
			}
		}
		return false;
	}

	private static Instant parseDate( String value ) {
		try {
			return ZonedDateTime.parse( value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME ).toInstant();
		} catch ( DateTimeParseException e ) {
			return null;
		}
	}

	private static String cacheControl( long maxAgeSeconds ) {
		if ( maxAgeSeconds > 0 ) {
			return "public, max-age=" + maxAgeSeconds;
		}
		return maxAgeSeconds == 0 ? "no-cache" : "no-store";
	}

	/**
	 * Content-Disposition for a file name: plain ASCII in quotes, and the real name as filename* when it has other characters
	 */
	private static String disposition( Options options ) {
		String name = options.filename() == null ? "" : options.filename().replaceAll( "[\\p{Cntrl}\"\\\\/]", "" ).trim();
		if ( name.isEmpty() ) {
			return options.download() ? "attachment" : null;
		}
		String	kind	= options.download() ? "attachment" : "inline";
		String	ascii	= name.replaceAll( "[^\\x20-\\x7E]", "_" );
		String	result	= kind + "; filename=\"" + ascii + "\"";
		if ( !ascii.equals( name ) ) {
			StringBuilder encoded = new StringBuilder();
			for ( byte b : name.getBytes( StandardCharsets.UTF_8 ) ) {
				int c = b & 0xFF;
				if ( ( c >= 'a' && c <= 'z' ) || ( c >= 'A' && c <= 'Z' ) || ( c >= '0' && c <= '9' ) || c == '.' || c == '-' || c == '_' ) {
					encoded.append( ( char ) c );
				} else {
					encoded.append( String.format( "%%%02X", c ) );
				}
			}
			result += "; filename*=UTF-8''" + encoded;
		}
		return result;
	}

	/**
	 * Guess the format of encoded audio from its first bytes
	 *
	 * @return wav, mp3, flac, ogg or m4a, or null when it is not recognised
	 */
	public static String sniff( byte[] b ) {
		if ( b.length < 12 ) {
			return null;
		}
		String head = new String( b, 0, 12, StandardCharsets.ISO_8859_1 );
		if ( head.startsWith( "RIFF" ) && head.substring( 8 ).startsWith( "WAVE" ) ) {
			return "wav";
		}
		if ( head.startsWith( "fLaC" ) ) {
			return "flac";
		}
		if ( head.startsWith( "OggS" ) ) {
			return "ogg";
		}
		if ( head.substring( 4 ).startsWith( "ftyp" ) ) {
			return "m4a";
		}
		if ( head.startsWith( "ID3" ) || ( ( b[ 0 ] & 0xFF ) == 0xFF && ( b[ 1 ] & 0xE0 ) == 0xE0 ) ) {
			return "mp3";
		}
		return null;
	}

}
