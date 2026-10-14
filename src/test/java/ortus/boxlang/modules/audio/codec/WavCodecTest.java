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
package ortus.boxlang.modules.audio.codec;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class WavCodecTest {

	private static AudioBuffer sine( int channels, int frames, int sampleRate ) {
		float[][] samples = new float[ channels ][ frames ];
		for ( int ch = 0; ch < channels; ch++ ) {
			for ( int i = 0; i < frames; i++ ) {
				samples[ ch ][ i ] = ( float ) ( 0.8 * Math.sin( 2 * Math.PI * ( 220 * ( ch + 1 ) ) * i / sampleRate ) );
			}
		}
		return new AudioBuffer( samples, sampleRate );
	}

	private static byte[] encode( AudioBuffer audio, int bitDepth ) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		WavCodec.write( audio, out, bitDepth );
		return out.toByteArray();
	}

	/**
	 * Builds a WAV by hand so we can test headers the writer never produces
	 */
	private static byte[] wav( int formatTag, int channels, int sampleRate, int bits, byte[] data, byte[]... extraChunksBeforeData ) {
		int extra = 0;
		for ( byte[] chunk : extraChunksBeforeData ) {
			extra += chunk.length;
		}
		ByteBuffer b = ByteBuffer.allocate( 12 + 24 + extra + 8 + data.length ).order( ByteOrder.LITTLE_ENDIAN );
		b.put( "RIFF".getBytes() ).putInt( b.capacity() - 8 ).put( "WAVE".getBytes() );
		b.put( "fmt ".getBytes() ).putInt( 16 ).putShort( ( short ) formatTag ).putShort( ( short ) channels ).putInt( sampleRate );
		b.putInt( sampleRate * channels * bits / 8 ).putShort( ( short ) ( channels * bits / 8 ) ).putShort( ( short ) bits );
		for ( byte[] chunk : extraChunksBeforeData ) {
			b.put( chunk );
		}
		b.put( "data".getBytes() ).putInt( data.length ).put( data );
		return b.array();
	}

	@DisplayName( "Round trips keep format and samples within the bit depth precision" )
	@ParameterizedTest
	@ValueSource( ints = { 16, 24, 32 } )
	void testRoundTrip( int bitDepth ) {
		AudioBuffer	original	= sine( 2, 5000, 44100 );
		AudioBuffer	back		= WavCodec.decode( encode( original, bitDepth ) );

		assertThat( back.getChannels() ).isEqualTo( 2 );
		assertThat( back.getSampleRate() ).isEqualTo( 44100 );
		assertThat( back.getFrames() ).isEqualTo( 5000 );
		float tolerance = bitDepth == 16 ? 1f / 16000 : bitDepth == 24 ? 1f / 4000000 : 0f;
		for ( int ch = 0; ch < 2; ch++ ) {
			for ( int i = 0; i < 5000; i++ ) {
				assertThat( back.channel( ch )[ i ] ).isWithin( tolerance ).of( original.channel( ch )[ i ] );
			}
		}
	}

	@DisplayName( "Mono files and unusual sample rates survive" )
	@Test
	void testMonoOddRate() {
		AudioBuffer back = WavCodec.decode( encode( sine( 1, 300, 22050 ), 16 ) );
		assertThat( back.getChannels() ).isEqualTo( 1 );
		assertThat( back.getSampleRate() ).isEqualTo( 22050 );
		assertThat( back.getFrames() ).isEqualTo( 300 );
	}

	@DisplayName( "An empty buffer writes a valid file with no frames" )
	@Test
	void testEmpty() {
		AudioBuffer back = WavCodec.decode( encode( AudioBuffer.silence( 2, 0, 44100 ), 16 ) );
		assertThat( back.getFrames() ).isEqualTo( 0 );
		assertThat( back.getChannels() ).isEqualTo( 2 );
	}

	@DisplayName( "Writing spans more than one internal block" )
	@Test
	void testLongerThanOneBlock() {
		AudioBuffer back = WavCodec.decode( encode( sine( 2, 70000, 44100 ), 16 ) );
		assertThat( back.getFrames() ).isEqualTo( 70000 );
		assertThat( back.channel( 1 )[ 69999 ] ).isWithin( 1f / 16000 ).of( sine( 2, 70000, 44100 ).channel( 1 )[ 69999 ] );
	}

	@DisplayName( "Integer output is clipped, float output is not" )
	@Test
	void testClipping() {
		AudioBuffer	loud	= new AudioBuffer( new float[][] { { 2f, -2f, 0.5f } }, 44100 );
		AudioBuffer	pcm		= WavCodec.decode( encode( loud, 16 ) );
		assertThat( pcm.channel( 0 )[ 0 ] ).isWithin( 1e-4f ).of( 1f );
		assertThat( pcm.channel( 0 )[ 1 ] ).isWithin( 1e-4f ).of( -1f );
		AudioBuffer flt = WavCodec.decode( encode( loud, 32 ) );
		assertThat( flt.channel( 0 )[ 0 ] ).isEqualTo( 2f );
		assertThat( flt.getPeak() ).isEqualTo( 2f );
	}

	@DisplayName( "The 16 bit header has the standard 44 byte layout" )
	@Test
	void testHeaderLayout() {
		byte[]		bytes	= encode( sine( 2, 10, 44100 ), 16 );
		ByteBuffer	b		= ByteBuffer.wrap( bytes ).order( ByteOrder.LITTLE_ENDIAN );
		assertThat( bytes.length ).isEqualTo( 44 + 10 * 2 * 2 );
		assertThat( b.getInt( 4 ) ).isEqualTo( bytes.length - 8 );
		assertThat( new String( bytes, 0, 4 ) ).isEqualTo( "RIFF" );
		assertThat( new String( bytes, 36, 4 ) ).isEqualTo( "data" );
		assertThat( b.getInt( 40 ) ).isEqualTo( 40 );
	}

	@DisplayName( "8 bit unsigned PCM is read with the midpoint at 128" )
	@Test
	void testEightBit() {
		AudioBuffer audio = WavCodec.decode( wav( 1, 1, 8000, 8, new byte[] { ( byte ) 128, ( byte ) 255, 0 } ) );
		assertThat( audio.getFrames() ).isEqualTo( 3 );
		assertThat( audio.channel( 0 )[ 0 ] ).isEqualTo( 0f );
		assertThat( audio.channel( 0 )[ 1 ] ).isWithin( 0.01f ).of( 1f );
		assertThat( audio.channel( 0 )[ 2 ] ).isEqualTo( -1f );
	}

	@DisplayName( "Other chunks before the data, including odd sized ones, are skipped" )
	@Test
	void testExtraChunks() {
		// LIST chunk with 3 bytes of body, padded to 4
		byte[]		list	= ByteBuffer.allocate( 12 ).order( ByteOrder.LITTLE_ENDIAN ).put( "LIST".getBytes() ).putInt( 3 ).put( new byte[] { 1, 2, 3, 0 } )
		    .array();
		AudioBuffer	audio	= WavCodec.decode( wav( 1, 1, 8000, 16, new byte[] { 0, 64, 0, ( byte ) 192 }, list ) );
		assertThat( audio.getFrames() ).isEqualTo( 2 );
		assertThat( audio.channel( 0 )[ 0 ] ).isWithin( 1e-6f ).of( 0.5f );
		assertThat( audio.channel( 0 )[ 1 ] ).isWithin( 1e-6f ).of( -0.5f );
	}

	@DisplayName( "A data size larger than the file, as written by streaming tools, is clamped" )
	@Test
	void testStreamingDataSize() {
		byte[] bytes = encode( sine( 1, 100, 8000 ), 16 );
		ByteBuffer.wrap( bytes ).order( ByteOrder.LITTLE_ENDIAN ).putInt( 40, 0xFFFFFFFF );
		assertThat( WavCodec.decode( bytes ).getFrames() ).isEqualTo( 100 );
	}

	@DisplayName( "A trailing partial frame is ignored" )
	@Test
	void testPartialFrame() {
		AudioBuffer audio = WavCodec.decode( wav( 1, 2, 8000, 16, new byte[] { 0, 0, 0, 0, 1, 1 } ) );
		assertThat( audio.getFrames() ).isEqualTo( 1 );
	}

	@DisplayName( "Extensible headers use the real format from the sub format GUID" )
	@Test
	void testExtensible() {
		ByteBuffer b = ByteBuffer.allocate( 12 + 48 + 8 + 4 ).order( ByteOrder.LITTLE_ENDIAN );
		b.put( "RIFF".getBytes() ).putInt( b.capacity() - 8 ).put( "WAVE".getBytes() );
		b.put( "fmt ".getBytes() ).putInt( 40 ).putShort( ( short ) 0xFFFE ).putShort( ( short ) 1 ).putInt( 48000 ).putInt( 96000 );
		b.putShort( ( short ) 2 ).putShort( ( short ) 16 ).putShort( ( short ) 22 ).putShort( ( short ) 16 ).putInt( 4 );
		b.putShort( ( short ) 1 ).put( new byte[ 14 ] );
		b.put( "data".getBytes() ).putInt( 4 ).putShort( ( short ) 16384 ).putShort( ( short ) -16384 );
		AudioBuffer audio = WavCodec.decode( b.array() );
		assertThat( audio.getSampleRate() ).isEqualTo( 48000 );
		assertThat( audio.getFrames() ).isEqualTo( 2 );
		assertThat( audio.channel( 0 )[ 0 ] ).isWithin( 1e-6f ).of( 0.5f );
	}

	@DisplayName( "Bad and unsupported files fail with a clear error" )
	@Test
	void testBadFiles() {
		assertThrows( BoxRuntimeException.class, () -> WavCodec.decode( new byte[ 0 ] ) );
		assertThrows( BoxRuntimeException.class, () -> WavCodec.decode( "RIFF----WAVX".getBytes() ) );
		assertThrows( BoxRuntimeException.class, () -> WavCodec.decode( "not a wav file at all".getBytes() ) );
		// a-law (format 6) is not supported
		assertThrows( BoxRuntimeException.class, () -> WavCodec.decode( wav( 6, 1, 8000, 8, new byte[ 4 ] ) ) );
		// fmt chunk but no data chunk
		byte[] noData = encode( sine( 1, 10, 8000 ), 16 );
		noData[ 36 ] = 'X';
		assertThrows( BoxRuntimeException.class, () -> WavCodec.decode( noData ) );
		// zero channels
		assertThrows( BoxRuntimeException.class, () -> WavCodec.decode( wav( 1, 0, 8000, 16, new byte[ 4 ] ) ) );
		// 12 bit PCM
		assertThrows( BoxRuntimeException.class, () -> WavCodec.decode( wav( 1, 1, 8000, 12, new byte[ 4 ] ) ) );
	}

	@DisplayName( "Only 16, 24 and 32 bit output are allowed" )
	@Test
	void testBadBitDepth() {
		AudioBuffer audio = sine( 1, 10, 8000 );
		assertThrows( BoxRuntimeException.class, () -> encode( audio, 8 ) );
		assertThrows( BoxRuntimeException.class, () -> encode( audio, 20 ) );
	}

	@DisplayName( "Files and streams read and write" )
	@Test
	void testFileAndStream( @TempDir Path dir ) throws Exception {
		Path file = dir.resolve( "tone.wav" );
		WavCodec.write( sine( 2, 1000, 44100 ), file, 24 );
		assertThat( Files.size( file ) ).isEqualTo( 44 + 1000 * 2 * 3 );
		assertThat( WavCodec.read( file ).getFrames() ).isEqualTo( 1000 );
		assertThat( WavCodec.read( new ByteArrayInputStream( Files.readAllBytes( file ) ) ).getChannels() ).isEqualTo( 2 );
		assertThrows( BoxRuntimeException.class, () -> WavCodec.read( dir.resolve( "missing.wav" ) ) );
	}

}
