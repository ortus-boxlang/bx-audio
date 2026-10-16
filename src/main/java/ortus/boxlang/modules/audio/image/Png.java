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
package ortus.boxlang.modules.audio.image;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * A PNG encoder with no dependency on java.awt, so it works on a JVM without the desktop module: 8 bit RGBA, unfiltered rows, deflated.
 */
public final class Png {

	private Png() {
	}

	/**
	 * @param width  Pixels across
	 * @param height Pixels down
	 * @param argb   width x height pixels, row by row, each 0xAARRGGBB
	 */
	public static byte[] encode( int width, int height, int[] argb ) {
		ByteArrayOutputStream out = new ByteArrayOutputStream( width * height / 2 + 1024 );
		out.writeBytes( new byte[] { ( byte ) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' } );
		ByteArrayOutputStream ihdr = new ByteArrayOutputStream( 13 );
		writeInt( ihdr, width );
		writeInt( ihdr, height );
		ihdr.write( 8 );
		ihdr.write( 6 );
		ihdr.write( 0 );
		ihdr.write( 0 );
		ihdr.write( 0 );
		chunk( out, "IHDR", ihdr.toByteArray() );

		Deflater				deflater	= new Deflater( 6 );
		ByteArrayOutputStream	idat		= new ByteArrayOutputStream( width * height / 4 + 256 );
		byte[]					row			= new byte[ 1 + width * 4 ];
		byte[]					buffer		= new byte[ 64 * 1024 ];
		for ( int y = 0; y < height; y++ ) {
			row[ 0 ] = 0;
			for ( int x = 0; x < width; x++ ) {
				int p = argb[ y * width + x ];
				row[ 1 + x * 4 ]		= ( byte ) ( p >> 16 );
				row[ 1 + x * 4 + 1 ]	= ( byte ) ( p >> 8 );
				row[ 1 + x * 4 + 2 ]	= ( byte ) p;
				row[ 1 + x * 4 + 3 ]	= ( byte ) ( p >>> 24 );
			}
			deflater.setInput( row );
			while ( !deflater.needsInput() ) {
				int n = deflater.deflate( buffer );
				idat.write( buffer, 0, n );
			}
		}
		deflater.finish();
		while ( !deflater.finished() ) {
			int n = deflater.deflate( buffer );
			idat.write( buffer, 0, n );
		}
		deflater.end();
		chunk( out, "IDAT", idat.toByteArray() );
		chunk( out, "IEND", new byte[ 0 ] );
		return out.toByteArray();
	}

	private static void writeInt( ByteArrayOutputStream out, int n ) {
		out.write( n >>> 24 );
		out.write( n >>> 16 );
		out.write( n >>> 8 );
		out.write( n );
	}

	private static void chunk( ByteArrayOutputStream out, String type, byte[] data ) {
		writeInt( out, data.length );
		byte[] t = type.getBytes( StandardCharsets.ISO_8859_1 );
		out.writeBytes( t );
		out.writeBytes( data );
		CRC32 crc = new CRC32();
		crc.update( t );
		crc.update( data );
		writeInt( out, ( int ) crc.getValue() );
	}

}
