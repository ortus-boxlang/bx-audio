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
package ortus.boxlang.modules.audio.tts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Reads the style vectors of Kokoro voices from the voices pack, {@code voices-v1.0.bin}. The pack is a NumPy {@code .npz}: a zip with one
 * {@code <voice>.npy} per voice, each a float32 array of 510 rows by 256 values, one row per phoneme count.
 */
public final class KokoroVoices {

	/**
	 * The values in one style vector
	 */
	public static final int			WIDTH	= 256;

	private static final Pattern	SHAPE	= Pattern.compile( "'shape':\\s*\\(([^)]*)\\)" );

	private KokoroVoices() {
	}

	/**
	 * The styles of one voice
	 *
	 * @param rows   How many rows there are, one per phoneme count
	 * @param values The rows one after the other, {@link #WIDTH} values each
	 */
	public record Styles( int rows, float[] values ) {

		/**
		 * The style for a text of that many tokens: row {@code n - 1}, or the last row when the text is longer than the table
		 */
		public float[] forTokens( int tokens ) {
			int		row		= Math.max( 1, Math.min( tokens, this.rows ) ) - 1;
			float[]	style	= new float[ WIDTH ];
			System.arraycopy( this.values, row * WIDTH, style, 0, WIDTH );
			return style;
		}
	}

	/**
	 * Read the styles of a voice from the pack
	 *
	 * @param pack  The {@code voices-v1.0.bin} file
	 * @param voice The voice id, for example {@code af_heart}
	 */
	public static Styles read( Path pack, String voice ) {
		try ( ZipFile zip = new ZipFile( pack.toFile() ) ) {
			ZipEntry entry = zip.getEntry( voice + ".npy" );
			if ( entry == null ) {
				throw new AudioException( AudioException.VOICE_NOT_FOUND, "The voices pack has no voice [" + voice + "]" );
			}
			try ( InputStream in = zip.getInputStream( entry ) ) {
				return parseNpy( in.readAllBytes(), voice );
			}
		} catch ( IOException e ) {
			throw new AudioException( AudioException.IO, "Could not read the voice [" + voice + "] from " + pack + ": " + e.getMessage(), e );
		}
	}

	/**
	 * Read a NumPy {@code .npy} file of little endian float32 values, version 1 or 2
	 */
	public static Styles parseNpy( byte[] bytes, String name ) {
		if ( bytes.length < 12 || ( bytes[ 0 ] & 0xff ) != 0x93 || bytes[ 1 ] != 'N' || bytes[ 2 ] != 'U' ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "The voice [" + name + "] is not a NumPy array" );
		}
		ByteBuffer	buffer		= ByteBuffer.wrap( bytes ).order( ByteOrder.LITTLE_ENDIAN );
		int			major		= bytes[ 6 ];
		int			headerSize	= major == 1 ? buffer.getShort( 8 ) & 0xffff : buffer.getInt( 8 );
		int			start		= major == 1 ? 10 : 12;
		String		header		= new String( bytes, start, headerSize, StandardCharsets.ISO_8859_1 );
		if ( !header.contains( "<f4" ) || header.contains( "True" ) ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "The voice [" + name + "] is not little endian float32 in C order: " + header );
		}
		Matcher shape = SHAPE.matcher( header );
		if ( !shape.find() ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "The voice [" + name + "] has no shape: " + header );
		}
		String[]	dimensions	= shape.group( 1 ).trim().split( "\\s*,\\s*" );
		long		count		= 1;
		for ( String dimension : dimensions ) {
			count *= Long.parseLong( dimension );
		}
		if ( count % WIDTH != 0 || ( bytes.length - start - headerSize ) / 4 < count ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "The voice [" + name + "] does not hold rows of " + WIDTH + " values: " + header );
		}
		float[] values = new float[ ( int ) count ];
		buffer.position( start + headerSize );
		buffer.asFloatBuffer().get( values );
		return new Styles( ( int ) ( count / WIDTH ), values );
	}

}
