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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class KokoroVoicesTest {

	/**
	 * A scratch folder, removed after each test
	 */
	@TempDir
	Path dir;

	/**
	 * A NumPy file of float32 values, the way {@code numpy.save} writes a version 1 file: the header is padded so the data starts on a multiple of 64
	 *
	 * @param rows   How many rows the array has
	 * @param values The values, {@link KokoroVoices#WIDTH} per row
	 *
	 * @return The bytes of the file
	 */
	private static byte[] npy( int rows, float[] values ) {
		String	header	= "{'descr': '<f4', 'fortran_order': False, 'shape': (" + rows + ", 1, 256), }";
		int		pad		= 64 - ( 10 + header.length() + 1 ) % 64;
		header = header + " ".repeat( pad ) + "\n";
		ByteBuffer out = ByteBuffer.allocate( 10 + header.length() + values.length * 4 ).order( ByteOrder.LITTLE_ENDIAN );
		out.put( new byte[] { ( byte ) 0x93, 'N', 'U', 'M', 'P', 'Y', 1, 0 } );
		out.putShort( ( short ) header.length() );
		out.put( header.getBytes( StandardCharsets.ISO_8859_1 ) );
		for ( float v : values ) {
			out.putFloat( v );
		}
		return out.array();
	}

	/**
	 * Values that count up, so each row can be told from the others
	 *
	 * @param rows How many rows
	 *
	 * @return {@code rows} rows of {@link KokoroVoices#WIDTH} values, each the number of its position
	 */
	private static float[] counting( int rows ) {
		float[] values = new float[ rows * KokoroVoices.WIDTH ];
		for ( int i = 0; i < values.length; i++ ) {
			values[ i ] = i / KokoroVoices.WIDTH;
		}
		return values;
	}

	/**
	 * A voice is read from the pack, and a text of n tokens gets row n - 1
	 */
	@DisplayName( "A voice is read from the pack, and a text of n tokens gets row n - 1" )
	@Test
	void testRead() throws IOException {
		Path pack = this.dir.resolve( "voices.bin" );
		try ( ZipOutputStream zip = new ZipOutputStream( Files.newOutputStream( pack ) ) ) {
			zip.putNextEntry( new ZipEntry( "af_test.npy" ) );
			zip.write( npy( 5, counting( 5 ) ) );
			zip.closeEntry();
		}
		KokoroVoices.Styles styles = KokoroVoices.read( pack, "af_test" );
		assertThat( styles.rows() ).isEqualTo( 5 );
		assertThat( styles.forTokens( 1 )[ 0 ] ).isEqualTo( 0f );
		assertThat( styles.forTokens( 3 )[ 255 ] ).isEqualTo( 2f );
		assertThat( styles.forTokens( 3 ) ).hasLength( 256 );
		// longer than the table: the last row, and never before the first
		assertThat( styles.forTokens( 400 )[ 0 ] ).isEqualTo( 4f );
		assertThat( styles.forTokens( 0 )[ 0 ] ).isEqualTo( 0f );
		assertThat( assertThrows( BoxRuntimeException.class, () -> KokoroVoices.read( pack, "af_missing" ) ).getMessage() ).contains( "af_missing" );
	}

	/**
	 * A file that is not float32 in C order, or not a NumPy file, is refused
	 */
	@DisplayName( "A file that is not float32 in C order, or not a NumPy file, is refused" )
	@Test
	void testRefused() {
		assertThat( assertThrows( BoxRuntimeException.class, () -> KokoroVoices.parseNpy( "not numpy at all".getBytes(), "x" ) ).getMessage() )
		    .contains( "not a NumPy array" );
		byte[]	good	= npy( 2, counting( 2 ) );
		String	text	= new String( good, StandardCharsets.ISO_8859_1 ).replace( "<f4", "<f8" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> KokoroVoices.parseNpy( text.getBytes( StandardCharsets.ISO_8859_1 ), "x" ) ).getMessage() )
		    .contains( "float32" );
	}

}
