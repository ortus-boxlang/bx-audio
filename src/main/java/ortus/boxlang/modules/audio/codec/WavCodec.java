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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Reads and writes RIFF/WAVE files without depending on {@code javax.sound}, so it also works on a JVM without the
 * {@code java.desktop} module.
 * <p>
 * Reading supports integer PCM at 8, 16, 24 and 32 bits, IEEE float at 32 and 64 bits, and the extensible header form. Writing
 * supports 16 bit PCM, 24 bit PCM and 32 bit float.
 */
public final class WavCodec {

	private static final int	FORMAT_PCM			= 0x0001;
	private static final int	FORMAT_FLOAT		= 0x0003;
	private static final int	FORMAT_EXTENSIBLE	= 0xFFFE;

	/**
	 * Frames converted and written per block, keeps memory flat for long files
	 */
	private static final int	WRITE_BLOCK_FRAMES	= 65536;

	private WavCodec() {
	}

	/**
	 * Read a WAV file into memory
	 *
	 * @param path The file to read
	 */
	public static AudioBuffer read( Path path ) {
		try {
			return decode( Files.readAllBytes( path ) );
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Could not read audio file [" + path + "]: " + e.getMessage(), e );
		}
	}

	/**
	 * Read a WAV stream into memory. The stream is read fully but not closed.
	 *
	 * @param in The stream to read
	 */
	public static AudioBuffer read( InputStream in ) {
		try {
			return decode( in.readAllBytes() );
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Could not read audio stream: " + e.getMessage(), e );
		}
	}

	/**
	 * Decode WAV bytes
	 *
	 * @param bytes The complete file contents
	 */
	public static AudioBuffer decode( byte[] bytes ) {
		ByteBuffer buf = ByteBuffer.wrap( bytes ).order( ByteOrder.LITTLE_ENDIAN );
		if ( bytes.length < 12 || buf.getInt( 0 ) != fourCC( "RIFF" ) || buf.getInt( 8 ) != fourCC( "WAVE" ) ) {
			throw new BoxRuntimeException( "Not a WAV file: missing the RIFF/WAVE header" );
		}

		int		formatTag		= -1;
		int		channels		= 0;
		int		sampleRate		= 0;
		int		bitsPerSample	= 0;
		int		dataStart		= -1;
		long	dataLength		= 0;

		int		pos				= 12;
		while ( pos + 8 <= bytes.length ) {
			int		id		= buf.getInt( pos );
			// Chunk sizes are unsigned 32 bit
			long	size	= buf.getInt( pos + 4 ) & 0xFFFFFFFFL;
			int		body	= pos + 8;

			if ( id == fourCC( "fmt " ) ) {
				if ( size < 16 || body + 16 > bytes.length ) {
					throw new BoxRuntimeException( "Corrupt WAV file: the fmt chunk is too short" );
				}
				formatTag		= buf.getShort( body ) & 0xFFFF;
				channels		= buf.getShort( body + 2 ) & 0xFFFF;
				sampleRate		= buf.getInt( body + 4 );
				bitsPerSample	= buf.getShort( body + 14 ) & 0xFFFF;
				if ( formatTag == FORMAT_EXTENSIBLE ) {
					// cbSize, validBits, channelMask, then the sub format GUID whose first two bytes are the real format tag
					if ( size < 40 || body + 26 > bytes.length ) {
						throw new BoxRuntimeException( "Corrupt WAV file: the extensible fmt chunk is too short" );
					}
					formatTag = buf.getShort( body + 24 ) & 0xFFFF;
				}
			} else if ( id == fourCC( "data" ) && dataStart < 0 ) {
				dataStart = body;
				long	remaining	= ( long ) bytes.length - body;
				// Streaming writers leave 0 or 0xFFFFFFFF here because they do not know the length yet: the audio then runs to the end of the
				// file. A size of 0 followed by another chunk is a real empty data chunk.
				boolean	toTheEnd	= size == 0xFFFFFFFFL || size > remaining || ( size == 0 && remaining > 0 && !looksLikeChunk( buf, bytes.length, body ) );
				dataLength = toTheEnd ? remaining : size;
				if ( toTheEnd ) {
					break;
				}
			}

			// Chunks are padded to an even length
			long next = ( long ) body + size + ( size & 1 );
			if ( next > bytes.length ) {
				break;
			}
			pos = ( int ) next;
		}

		if ( formatTag < 0 ) {
			throw new BoxRuntimeException( "Corrupt WAV file: no fmt chunk found" );
		}
		if ( dataStart < 0 ) {
			throw new BoxRuntimeException( "Corrupt WAV file: no data chunk found" );
		}
		if ( channels < 1 || channels > AudioBuffer.MAX_CHANNELS ) {
			throw new BoxRuntimeException( "Unsupported WAV file: " + channels + " channels" );
		}
		boolean isFloat = formatTag == FORMAT_FLOAT;
		if ( !isFloat && formatTag != FORMAT_PCM ) {
			throw new BoxRuntimeException( "Unsupported WAV encoding (format tag " + formatTag + "). Only PCM and IEEE float are supported" );
		}
		if ( isFloat ? ( bitsPerSample != 32 && bitsPerSample != 64 )
		    : ( bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24 && bitsPerSample != 32 ) ) {
			throw new BoxRuntimeException( "Unsupported WAV bit depth: " + bitsPerSample + ( isFloat ? " bit float" : " bit PCM" ) );
		}

		int		bytesPerSample	= bitsPerSample / 8;
		long	frameSize		= ( long ) bytesPerSample * channels;
		long	frameCount		= dataLength / frameSize;
		if ( frameCount > Integer.MAX_VALUE - 16 ) {
			throw new BoxRuntimeException( "This WAV file is too long to hold in memory (" + frameCount + " frames)" );
		}
		int			frames	= ( int ) frameCount;
		float[][]	samples	= new float[ channels ][ frames ];

		int			at		= dataStart;
		for ( int frame = 0; frame < frames; frame++ ) {
			for ( int ch = 0; ch < channels; ch++ ) {
				samples[ ch ][ frame ]	= readSample( buf, at, bitsPerSample, isFloat );
				at						+= bytesPerSample;
			}
		}
		return new AudioBuffer( samples, sampleRate );
	}

	/**
	 * @return Whether a chunk header that makes sense starts at {@code at}: a four letter id and a size that fits in the file
	 */
	private static boolean looksLikeChunk( ByteBuffer buf, int length, int at ) {
		if ( at + 8 > length ) {
			return false;
		}
		for ( int i = 0; i < 4; i++ ) {
			int c = buf.get( at + i ) & 0xFF;
			if ( c < 0x20 || c > 0x7E ) {
				return false;
			}
		}
		return ( buf.getInt( at + 4 ) & 0xFFFFFFFFL ) <= length - at - 8;
	}

	/**
	 * Write a buffer to a WAV file, replacing any existing file
	 *
	 * @param audio    The audio to write
	 * @param path     The destination file
	 * @param bitDepth 16 or 24 for integer PCM, 32 for IEEE float
	 */
	public static void write( AudioBuffer audio, Path path, int bitDepth ) {
		try ( OutputStream out = Files.newOutputStream( path ) ) {
			write( audio, out, bitDepth );
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Could not write audio file [" + path + "]: " + e.getMessage(), e );
		}
	}

	/**
	 * Write a buffer as WAV to a stream. The stream is flushed but not closed.
	 *
	 * @param audio    The audio to write
	 * @param out      The destination stream
	 * @param bitDepth 16 or 24 for integer PCM, 32 for IEEE float
	 */
	public static void write( AudioBuffer audio, OutputStream out, int bitDepth ) {
		if ( bitDepth != 16 && bitDepth != 24 && bitDepth != 32 ) {
			throw new BoxRuntimeException( "Bit depth must be 16, 24 (integer PCM) or 32 (float) but was " + bitDepth );
		}
		boolean	isFloat			= bitDepth == 32;
		int		channels		= audio.getChannels();
		int		frames			= audio.getFrames();
		int		bytesPerSample	= bitDepth / 8;
		long	dataBytes		= ( long ) frames * channels * bytesPerSample;
		// Float files carry a longer fmt chunk and a fact chunk
		int		headerExtra		= isFloat ? 14 : 0;
		if ( dataBytes + 36 + headerExtra > 0xFFFFFFFFL ) {
			throw new BoxRuntimeException( "Audio is too long for a WAV file (over 4 GB)" );
		}

		try {
			BufferedOutputStream	bos		= new BufferedOutputStream( out, 1 << 16 );
			ByteBuffer				header	= ByteBuffer.allocate( 58 ).order( ByteOrder.LITTLE_ENDIAN );
			header.putInt( fourCC( "RIFF" ) ).putInt( ( int ) ( 36 + headerExtra + dataBytes ) ).putInt( fourCC( "WAVE" ) );
			header.putInt( fourCC( "fmt " ) ).putInt( isFloat ? 18 : 16 );
			header.putShort( ( short ) ( isFloat ? FORMAT_FLOAT : FORMAT_PCM ) ).putShort( ( short ) channels );
			header.putInt( audio.getSampleRate() ).putInt( audio.getSampleRate() * channels * bytesPerSample );
			header.putShort( ( short ) ( channels * bytesPerSample ) ).putShort( ( short ) bitDepth );
			if ( isFloat ) {
				header.putShort( ( short ) 0 );
				header.putInt( fourCC( "fact" ) ).putInt( 4 ).putInt( frames );
			}
			header.putInt( fourCC( "data" ) ).putInt( ( int ) dataBytes );
			bos.write( header.array(), 0, header.position() );

			Quantizer	quantizer	= isFloat ? null : new Quantizer( audio, bitDepth );
			ByteBuffer	block		= ByteBuffer.allocate( WRITE_BLOCK_FRAMES * channels * bytesPerSample ).order( ByteOrder.LITTLE_ENDIAN );
			for ( int start = 0; start < frames; start += WRITE_BLOCK_FRAMES ) {
				int end = Math.min( frames, start + WRITE_BLOCK_FRAMES );
				block.clear();
				for ( int frame = start; frame < end; frame++ ) {
					for ( int ch = 0; ch < channels; ch++ ) {
						writeSample( block, audio.channel( ch )[ frame ], bitDepth, quantizer );
					}
				}
				bos.write( block.array(), 0, block.position() );
			}
			bos.flush();
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Could not write audio: " + e.getMessage(), e );
		}
	}

	private static float readSample( ByteBuffer buf, int at, int bitsPerSample, boolean isFloat ) {
		if ( isFloat ) {
			return bitsPerSample == 32 ? buf.getFloat( at ) : ( float ) buf.getDouble( at );
		}
		return switch ( bitsPerSample ) {
			// 8 bit WAV is unsigned with the midpoint at 128
			case 8 -> ( ( buf.get( at ) & 0xFF ) - 128 ) / 128f;
			case 16 -> buf.getShort( at ) / 32768f;
			case 24 -> {
				int v = ( buf.get( at ) & 0xFF ) | ( ( buf.get( at + 1 ) & 0xFF ) << 8 ) | ( buf.get( at + 2 ) << 16 );
				yield v / 8388608f;
			}
			default -> buf.getInt( at ) / 2147483648f;
		};
	}

	private static void writeSample( ByteBuffer block, float sample, int bitDepth, Quantizer quantizer ) {
		if ( bitDepth == 32 ) {
			block.putFloat( sample );
			return;
		}
		int v = quantizer.next( sample );
		if ( bitDepth == 16 ) {
			block.putShort( ( short ) v );
		} else {
			block.put( ( byte ) v ).put( ( byte ) ( v >> 8 ) ).put( ( byte ) ( v >> 16 ) );
		}
	}

	private static int fourCC( String id ) {
		return ( id.charAt( 0 ) ) | ( id.charAt( 1 ) << 8 ) | ( id.charAt( 2 ) << 16 ) | ( id.charAt( 3 ) << 24 );
	}

}
