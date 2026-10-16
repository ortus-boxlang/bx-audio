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
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

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
	 * Where WAV bytes come from: a byte array already in memory, or a file read a block at a time so a large file is not held twice
	 */
	private interface Source {

		long length() throws IOException;

		/**
		 * Fill the buffer from the given position, as far as the data goes
		 *
		 * @return How many bytes were read, 0 at the end
		 */
		int read( long position, ByteBuffer into ) throws IOException;
	}

	/**
	 * What the header of a WAV file says
	 */
	public record Info( int formatTag, int channels, int sampleRate, int bitsPerSample, long dataStart, long frames ) {

		public boolean isFloat() {
			return this.formatTag == FORMAT_FLOAT;
		}
	}

	/**
	 * Read a WAV file into memory. The file is read in blocks, so memory holds the samples once.
	 *
	 * @param path The file to read
	 */
	public static AudioBuffer read( Path path ) {
		try ( FileChannel channel = FileChannel.open( path, StandardOpenOption.READ ) ) {
			return decode( new Source() {

				@Override
				public long length() throws IOException {
					return channel.size();
				}

				@Override
				public int read( long position, ByteBuffer into ) throws IOException {
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
			} );
		} catch ( IOException e ) {
			throw new AudioException( AudioException.IO, "Could not read audio file [" + path + "]: " + e.getMessage(), e );
		}
	}

	/**
	 * What the header of a WAV file says, without reading the samples: the length of a file of any size takes the time to read a few bytes.
	 *
	 * @return The format, or null when the file is not a WAV file
	 */
	public static Info info( Path path ) {
		try ( FileChannel channel = FileChannel.open( path, StandardOpenOption.READ ) ) {
			ByteBuffer first = ByteBuffer.allocate( 12 ).order( ByteOrder.LITTLE_ENDIAN );
			if ( channel.read( first, 0 ) < 12 || first.getInt( 0 ) != fourCC( "RIFF" ) || first.getInt( 8 ) != fourCC( "WAVE" ) ) {
				return null;
			}
			return layout( new Source() {

				@Override
				public long length() throws IOException {
					return channel.size();
				}

				@Override
				public int read( long position, ByteBuffer into ) throws IOException {
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
			} );
		} catch ( IOException e ) {
			throw new AudioException( AudioException.IO, "Could not read audio file [" + path + "]: " + e.getMessage(), e );
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
			throw new AudioException( AudioException.IO, "Could not read audio stream: " + e.getMessage(), e );
		}
	}

	/**
	 * Decode WAV bytes
	 *
	 * @param bytes The complete file contents
	 */
	public static AudioBuffer decode( byte[] bytes ) {
		return decode( new Source() {

			@Override
			public long length() {
				return bytes.length;
			}

			@Override
			public int read( long position, ByteBuffer into ) {
				if ( position >= bytes.length ) {
					return 0;
				}
				int n = ( int ) Math.min( into.remaining(), bytes.length - position );
				into.put( bytes, ( int ) position, n );
				return n;
			}
		} );
	}

	private static ByteBuffer bytesAt( Source source, long position, int count ) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate( count ).order( ByteOrder.LITTLE_ENDIAN );
		source.read( position, buffer );
		buffer.flip();
		return buffer;
	}

	/**
	 * Walk the chunks to the format and the audio data
	 */
	private static Info layout( Source source ) throws IOException {
		long		length	= source.length();
		ByteBuffer	head	= bytesAt( source, 0, 12 );
		if ( length < 12 || head.remaining() < 12 || head.getInt( 0 ) != fourCC( "RIFF" ) || head.getInt( 8 ) != fourCC( "WAVE" ) ) {
			throw new AudioException( AudioException.CORRUPT_AUDIO, "Not a WAV file: missing the RIFF/WAVE header" );
		}

		int		formatTag		= -1;
		int		channels		= 0;
		int		sampleRate		= 0;
		int		bitsPerSample	= 0;
		long	dataStart		= -1;
		long	dataLength		= 0;

		long	pos				= 12;
		while ( pos + 8 <= length ) {
			ByteBuffer	header	= bytesAt( source, pos, 8 );
			int			id		= header.getInt( 0 );
			// Chunk sizes are unsigned 32 bit
			long		size	= header.getInt( 4 ) & 0xFFFFFFFFL;
			long		body	= pos + 8;

			if ( id == fourCC( "fmt " ) ) {
				if ( size < 16 || body + 16 > length ) {
					throw new AudioException( AudioException.CORRUPT_AUDIO, "Corrupt WAV file: the fmt chunk is too short" );
				}
				ByteBuffer fmt = bytesAt( source, body, ( int ) Math.min( size, 40 ) );
				formatTag		= fmt.getShort( 0 ) & 0xFFFF;
				channels		= fmt.getShort( 2 ) & 0xFFFF;
				sampleRate		= fmt.getInt( 4 );
				bitsPerSample	= fmt.getShort( 14 ) & 0xFFFF;
				if ( formatTag == FORMAT_EXTENSIBLE ) {
					// cbSize, validBits, channelMask, then the sub format GUID whose first two bytes are the real format tag
					if ( size < 40 || fmt.limit() < 26 ) {
						throw new AudioException( AudioException.CORRUPT_AUDIO, "Corrupt WAV file: the extensible fmt chunk is too short" );
					}
					formatTag = fmt.getShort( 24 ) & 0xFFFF;
				}
			} else if ( id == fourCC( "data" ) && dataStart < 0 ) {
				dataStart = body;
				long	remaining	= length - body;
				// Streaming writers leave 0 or 0xFFFFFFFF here because they do not know the length yet: the audio then runs to the end of the
				// file. A size of 0 followed by another chunk is a real empty data chunk.
				boolean	toTheEnd	= size == 0xFFFFFFFFL || size > remaining || ( size == 0 && remaining > 0 && !looksLikeChunk( source, length, body ) );
				dataLength = toTheEnd ? remaining : size;
				if ( toTheEnd ) {
					break;
				}
			}

			// Chunks are padded to an even length
			long next = body + size + ( size & 1 );
			if ( next > length ) {
				break;
			}
			pos = next;
		}

		if ( formatTag < 0 ) {
			throw new AudioException( AudioException.CORRUPT_AUDIO, "Corrupt WAV file: no fmt chunk found" );
		}
		if ( dataStart < 0 ) {
			throw new AudioException( AudioException.CORRUPT_AUDIO, "Corrupt WAV file: no data chunk found" );
		}
		if ( channels < 1 || channels > AudioBuffer.MAX_CHANNELS ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "Unsupported WAV file: " + channels + " channels" );
		}
		boolean isFloat = formatTag == FORMAT_FLOAT;
		if ( !isFloat && formatTag != FORMAT_PCM ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT,
			    "Unsupported WAV encoding (format tag " + formatTag + "). Only PCM and IEEE float are supported" );
		}
		if ( isFloat ? ( bitsPerSample != 32 && bitsPerSample != 64 )
		    : ( bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24 && bitsPerSample != 32 ) ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT,
			    "Unsupported WAV bit depth: " + bitsPerSample + ( isFloat ? " bit float" : " bit PCM" ) );
		}
		long frameSize = ( long ) ( bitsPerSample / 8 ) * channels;
		return new Info( formatTag, channels, sampleRate, bitsPerSample, dataStart, dataLength / frameSize );
	}

	private static AudioBuffer decode( Source source ) {
		try {
			Info info = layout( source );
			if ( info.frames() > Integer.MAX_VALUE - 16 ) {
				throw new AudioException( AudioException.TOO_LARGE, "This WAV file is too long to hold in memory (" + info.frames() + " frames)" );
			}
			int			channels		= info.channels();
			int			frames			= ( int ) info.frames();
			int			bytesPerSample	= info.bitsPerSample() / 8;
			float[][]	samples;
			try {
				samples = new float[ channels ][ frames ];
			} catch ( OutOfMemoryError e ) {
				throw new AudioException( AudioException.TOO_LARGE, "This WAV file is too long to hold in memory (" + frames + " frames)" );
			}
			int			blockFrames	= 16384;
			ByteBuffer	block		= ByteBuffer.allocate( blockFrames * channels * bytesPerSample ).order( ByteOrder.LITTLE_ENDIAN );
			for ( int done = 0; done < frames; done += blockFrames ) {
				int count = Math.min( blockFrames, frames - done );
				block.clear();
				block.limit( count * channels * bytesPerSample );
				if ( source.read( info.dataStart() + ( long ) done * channels * bytesPerSample, block ) < block.limit() ) {
					throw new AudioException( AudioException.CORRUPT_AUDIO, "Corrupt WAV file: the data ends before its length" );
				}
				int at = 0;
				for ( int frame = 0; frame < count; frame++ ) {
					for ( int ch = 0; ch < channels; ch++ ) {
						samples[ ch ][ done + frame ]	= readSample( block, at, info.bitsPerSample(), info.isFloat() );
						at								+= bytesPerSample;
					}
				}
			}
			return new AudioBuffer( samples, info.sampleRate() );
		} catch ( IOException e ) {
			throw new AudioException( AudioException.IO, "Could not read the audio: " + e.getMessage(), e );
		}
	}

	/**
	 * @return Whether a chunk header that makes sense starts at {@code at}: a four letter id and a size that fits in the file
	 */
	private static boolean looksLikeChunk( Source source, long length, long at ) throws IOException {
		if ( at + 8 > length ) {
			return false;
		}
		ByteBuffer buf = bytesAt( source, at, 8 );
		for ( int i = 0; i < 4; i++ ) {
			int c = buf.get( i ) & 0xFF;
			if ( c < 0x20 || c > 0x7E ) {
				return false;
			}
		}
		return ( buf.getInt( 4 ) & 0xFFFFFFFFL ) <= length - at - 8;
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
			throw new AudioException( AudioException.IO, "Could not write audio file [" + path + "]: " + e.getMessage(), e );
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
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Bit depth must be 16, 24 (integer PCM) or 32 (float) but was " + bitDepth );
		}
		boolean	isFloat			= bitDepth == 32;
		int		channels		= audio.getChannels();
		int		frames			= audio.getFrames();
		int		bytesPerSample	= bitDepth / 8;
		long	dataBytes		= ( long ) frames * channels * bytesPerSample;
		// Float files carry a longer fmt chunk and a fact chunk
		int		headerExtra		= isFloat ? 14 : 0;
		if ( dataBytes + 36 + headerExtra > 0xFFFFFFFFL ) {
			throw new AudioException( AudioException.TOO_LARGE, "Audio is too long for a WAV file (over 4 GB)" );
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
			throw new AudioException( AudioException.IO, "Could not write audio: " + e.getMessage(), e );
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
