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

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * FLAC, the lossless format, in plain Java. The encoder uses constant, fixed predictor (order 0 to 4) and verbatim subframes with
 * partitioned Rice coding, and picks the best stereo decorrelation per frame. That compresses typical music to 50 to 70 percent of PCM
 * without any dependency. The decoder reads any FLAC file, including the LPC subframes other encoders write.
 */
public final class FlacCodec {

	private static final int BLOCK_SIZE = 4096;

	private FlacCodec() {
	}

	public static boolean looksLikeFlac( byte[] bytes ) {
		return bytes.length >= 4 && bytes[ 0 ] == 'f' && bytes[ 1 ] == 'L' && bytes[ 2 ] == 'a' && bytes[ 3 ] == 'C';
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Encoder
	// ---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Encode audio as FLAC
	 *
	 * @param bitDepth 16 or 24
	 */
	public static byte[] encode( AudioBuffer audio, int bitDepth ) {
		if ( bitDepth != 16 && bitDepth != 24 ) {
			throw new BoxRuntimeException( "FLAC output supports 16 or 24 bits but bitDepth was " + bitDepth );
		}
		int channels = audio.getChannels();
		if ( channels > 8 ) {
			throw new BoxRuntimeException( "FLAC holds at most 8 channels but the audio has " + channels );
		}
		if ( audio.getSampleRate() > 655350 ) {
			throw new BoxRuntimeException( "FLAC holds sample rates up to 655350 Hz but the audio has " + audio.getSampleRate() );
		}
		int		frames	= audio.getFrames();
		int[][]	pcm		= new int[ channels ][ frames ];
		double	scale	= Math.pow( 2, bitDepth - 1 );
		for ( int ch = 0; ch < channels; ch++ ) {
			float[] in = audio.channel( ch );
			for ( int i = 0; i < frames; i++ ) {
				pcm[ ch ][ i ] = ( int ) Math.max( -scale, Math.min( scale - 1, Math.round( in[ i ] * scale ) ) );
			}
		}
		MessageDigest md5;
		try {
			md5 = MessageDigest.getInstance( "MD5" );
		} catch ( NoSuchAlgorithmException e ) {
			throw new BoxRuntimeException( "MD5 is not available: " + e.getMessage(), e );
		}
		int bytesPerSample = bitDepth / 8;
		for ( int i = 0; i < frames; i++ ) {
			byte[] one = new byte[ channels * bytesPerSample ];
			for ( int ch = 0; ch < channels; ch++ ) {
				for ( int b = 0; b < bytesPerSample; b++ ) {
					one[ ch * bytesPerSample + b ] = ( byte ) ( pcm[ ch ][ i ] >> ( 8 * b ) );
				}
			}
			md5.update( one );
		}
		ByteArrayOutputStream	body		= new ByteArrayOutputStream();
		int						minFrame	= Integer.MAX_VALUE, maxFrame = 0, minBlock = Integer.MAX_VALUE, maxBlock = 0;
		int						number		= 0;
		for ( int start = 0; start < frames; start += BLOCK_SIZE, number++ ) {
			int		size	= Math.min( BLOCK_SIZE, frames - start );
			byte[]	frame	= encodeFrame( pcm, start, size, bitDepth, number );
			body.write( frame, 0, frame.length );
			minFrame	= Math.min( minFrame, frame.length );
			maxFrame	= Math.max( maxFrame, frame.length );
			minBlock	= Math.min( minBlock, size );
			maxBlock	= Math.max( maxBlock, size );
		}
		if ( number == 0 ) {
			minFrame = minBlock = 0;
		}
		BitWriter head = new BitWriter();
		head.bytes( new byte[] { 'f', 'L', 'a', 'C' } );
		head.bits( 1, 1 );
		head.bits( 0, 7 );
		head.bits( 34, 24 );
		head.bits( number == 0 ? BLOCK_SIZE : minBlock == maxBlock ? minBlock : BLOCK_SIZE, 16 );
		head.bits( number == 0 ? BLOCK_SIZE : maxBlock, 16 );
		head.bits( minFrame, 24 );
		head.bits( maxFrame, 24 );
		head.bits( audio.getSampleRate(), 20 );
		head.bits( channels - 1, 3 );
		head.bits( bitDepth - 1, 5 );
		head.bits( frames, 36 );
		head.bytes( md5.digest() );
		byte[]					headBytes	= head.toByteArray();
		ByteArrayOutputStream	all			= new ByteArrayOutputStream( headBytes.length + body.size() );
		all.write( headBytes, 0, headBytes.length );
		all.write( body.toByteArray(), 0, body.size() );
		return all.toByteArray();
	}

	private static byte[] encodeFrame( int[][] pcm, int start, int size, int bps, int number ) {
		int			channels	= pcm.length;
		long[][]	signals		= new long[ channels ][ size ];
		int[]		sampleBits	= new int[ channels ];
		int			assignment	= channels - 1;
		for ( int ch = 0; ch < channels; ch++ ) {
			for ( int i = 0; i < size; i++ ) {
				signals[ ch ][ i ] = pcm[ ch ][ start + i ];
			}
			sampleBits[ ch ] = bps;
		}
		if ( channels == 2 ) {
			long[]	l	= signals[ 0 ], r = signals[ 1 ];
			long[]	mid	= new long[ size ], side = new long[ size ];
			for ( int i = 0; i < size; i++ ) {
				mid[ i ]	= ( l[ i ] + r[ i ] ) >> 1;
				side[ i ]	= l[ i ] - r[ i ];
			}
			long	cl		= cost( l ), cr = cost( r ), cm = cost( mid ), cs = cost( side );
			long	best	= cl + cr;
			if ( cl + cs < best ) {
				best		= cl + cs;
				assignment	= 8;
				signals		= new long[][] { l, side };
				sampleBits	= new int[] { bps, bps + 1 };
			}
			if ( cs + cr < best ) {
				best		= cs + cr;
				assignment	= 9;
				signals		= new long[][] { side, r };
				sampleBits	= new int[] { bps + 1, bps };
			}
			if ( cm + cs < best ) {
				assignment	= 10;
				signals		= new long[][] { mid, side };
				sampleBits	= new int[] { bps, bps + 1 };
			}
		}
		BitWriter w = new BitWriter();
		w.bits( 0xFFF8, 16 );
		int blockCode = size == BLOCK_SIZE ? 12 : size <= 256 ? 6 : 7;
		w.bits( blockCode, 4 );
		w.bits( 0, 4 );
		w.bits( assignment, 4 );
		w.bits( bps == 16 ? 4 : 6, 3 );
		w.bits( 0, 1 );
		w.utf8( number );
		if ( blockCode == 6 ) {
			w.bits( size - 1, 8 );
		} else if ( blockCode == 7 ) {
			w.bits( size - 1, 16 );
		}
		w.bits( crc8( w.toByteArrayPartial() ), 8 );
		for ( int ch = 0; ch < signals.length; ch++ ) {
			subframe( w, signals[ ch ], sampleBits[ ch ] );
		}
		w.align();
		byte[]	withoutCrc	= w.toByteArray();
		int		crc			= crc16( withoutCrc );
		w.bits( crc, 16 );
		return w.toByteArray();
	}

	/**
	 * A cheap guess at how many bits a channel needs: the sum of the absolute second order residual
	 */
	private static long cost( long[] x ) {
		long sum = 0;
		for ( int i = 2; i < x.length; i++ ) {
			sum += Math.abs( x[ i ] - 2 * x[ i - 1 ] + x[ i - 2 ] );
		}
		return sum;
	}

	private static void subframe( BitWriter w, long[] x, int bps ) {
		int		n			= x.length;
		boolean	constant	= true;
		for ( int i = 1; i < n; i++ ) {
			if ( x[ i ] != x[ 0 ] ) {
				constant = false;
				break;
			}
		}
		if ( constant && n > 0 ) {
			w.bits( 0, 1 );
			w.bits( 0, 6 );
			w.bits( 0, 1 );
			w.signed( x[ 0 ], bps );
			return;
		}
		long	bestBits	= 8L + ( long ) n * bps;
		int		bestOrder	= -1;
		long[]	bestRes		= null;
		Plan	bestPlan	= null;
		for ( int order = 0; order <= 4 && order < n; order++ ) {
			long[]	res		= residual( x, order );
			Plan	plan	= ricePlan( res, order, n );
			long	bits	= 8 + ( long ) order * bps + 2 + 4 + plan.bits();
			if ( bits < bestBits ) {
				bestBits	= bits;
				bestOrder	= order;
				bestRes		= res;
				bestPlan	= plan;
			}
		}
		if ( bestOrder < 0 ) {
			w.bits( 0, 1 );
			w.bits( 1, 6 );
			w.bits( 0, 1 );
			for ( long v : x ) {
				w.signed( v, bps );
			}
			return;
		}
		w.bits( 0, 1 );
		w.bits( 8 + bestOrder, 6 );
		w.bits( 0, 1 );
		for ( int i = 0; i < bestOrder; i++ ) {
			w.signed( x[ i ], bps );
		}
		int[]	ks		= bestPlan.ks();
		boolean	wide	= false;
		for ( int i = 1; i < ks.length; i++ ) {
			wide |= ks[ i ] > 14;
		}
		// Rice parameters above 14 need the second coding method, which has 5 bit parameters
		w.bits( wide ? 1 : 0, 2 );
		int partitionOrder = ks[ 0 ];
		w.bits( partitionOrder, 4 );
		int	parts	= 1 << partitionOrder;
		int	cursor	= 0;
		for ( int p = 0; p < parts; p++ ) {
			int	count	= ( n >> partitionOrder ) - ( p == 0 ? bestOrder : 0 );
			int	k		= ks[ 1 + p ];
			w.bits( k, wide ? 5 : 4 );
			for ( int i = 0; i < count; i++ ) {
				long u = zigzag( bestRes[ cursor + i ] );
				w.unary( u >> k );
				w.bits( u & ( ( 1L << k ) - 1 ), k );
			}
			cursor += count;
		}
	}

	private static long[] residual( long[] x, int order ) {
		long[] res = new long[ x.length - order ];
		for ( int i = order; i < x.length; i++ ) {
			long prediction = switch ( order ) {
				case 0 -> 0;
				case 1 -> x[ i - 1 ];
				case 2 -> 2 * x[ i - 1 ] - x[ i - 2 ];
				case 3 -> 3 * x[ i - 1 ] - 3 * x[ i - 2 ] + x[ i - 3 ];
				default -> 4 * x[ i - 1 ] - 6 * x[ i - 2 ] + 4 * x[ i - 3 ] - x[ i - 4 ];
			};
			res[ i - order ] = x[ i ] - prediction;
		}
		return res;
	}

	/**
	 * How a residual is Rice coded
	 *
	 * @param bits The size in bits, partition headers included
	 * @param ks   The partition order followed by the Rice parameter of each partition
	 */
	private record Plan( long bits, int[] ks ) {
	}

	/**
	 * The cheapest partitioning of a residual into Rice coded parts
	 */
	private static Plan ricePlan( long[] res, int order, int blockSize ) {
		Plan best = null;
		for ( int p = 0; p <= 8; p++ ) {
			int parts = 1 << p;
			if ( blockSize % parts != 0 || ( blockSize >> p ) <= order ) {
				break;
			}
			int[] ks = new int[ 1 + parts ];
			ks[ 0 ] = p;
			long	bits	= 0;
			int		cursor	= 0;
			for ( int part = 0; part < parts; part++ ) {
				int		count	= ( blockSize >> p ) - ( part == 0 ? order : 0 );
				double	sum		= 0;
				for ( int i = 0; i < count; i++ ) {
					sum += zigzag( res[ cursor + i ] );
				}
				int		guess		= count == 0 ? 0 : ( int ) Math.max( 0, Math.floor( Math.log( Math.max( 1, sum / count ) ) / Math.log( 2 ) ) );
				int		bestK		= 0;
				long	bestPart	= Long.MAX_VALUE;
				for ( int k = Math.max( 0, Math.min( guess, 30 ) - 1 ); k <= Math.min( 30, guess + 1 ); k++ ) {
					long partBits = 0;
					for ( int i = 0; i < count; i++ ) {
						partBits += ( zigzag( res[ cursor + i ] ) >> k ) + 1 + k;
					}
					if ( partBits < bestPart ) {
						bestPart	= partBits;
						bestK		= k;
					}
				}
				ks[ 1 + part ]	= bestK;
				bits			+= bestPart + 4;
				cursor			+= count;
			}
			if ( best == null || bits < best.bits() ) {
				best = new Plan( bits, ks );
			}
		}
		return best;
	}

	private static long zigzag( long v ) {
		return v >= 0 ? v << 1 : ( ( -v ) << 1 ) - 1;
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Decoder
	// ---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Decode a FLAC file held in memory
	 */
	public static AudioBuffer decode( byte[] bytes ) {
		if ( !looksLikeFlac( bytes ) ) {
			throw new BoxRuntimeException( "Not a FLAC file" );
		}
		BitReader	r		= new BitReader( bytes, 4 );
		int			rate	= 0, channels = 0, bps = 0;
		long		total	= 0;
		boolean		last;
		do {
			last = r.bits( 1 ) == 1;
			int	type	= ( int ) r.bits( 7 );
			int	length	= ( int ) r.bits( 24 );
			if ( type == 0 ) {
				r.bits( 16 );
				r.bits( 16 );
				r.bits( 24 );
				r.bits( 24 );
				rate		= ( int ) r.bits( 20 );
				channels	= ( int ) r.bits( 3 ) + 1;
				bps			= ( int ) r.bits( 5 ) + 1;
				total		= r.bits( 36 );
				r.skipBytes( 16 );
			} else {
				r.skipBytes( length );
			}
		} while ( !last );
		if ( rate == 0 ) {
			throw new BoxRuntimeException( "The FLAC file has no stream info" );
		}
		if ( total > Integer.MAX_VALUE ) {
			throw new BoxRuntimeException( "The FLAC file is too long to hold in memory" );
		}
		float[][]	out		= new float[ channels ][ ( int ) total ];
		int			pos		= 0;
		double		scale	= Math.pow( 2, bps - 1 );
		while ( pos < total && r.hasMore() ) {
			int	frameStart	= r.bytePosition();
			int	sync		= ( int ) r.bits( 14 );
			if ( sync != 0x3FFE ) {
				throw new BoxRuntimeException( "The FLAC file is damaged: bad frame sync at byte " + frameStart );
			}
			r.bits( 2 );
			int	blockCode	= ( int ) r.bits( 4 );
			int	rateCode	= ( int ) r.bits( 4 );
			int	assignment	= ( int ) r.bits( 4 );
			int	sizeCode	= ( int ) r.bits( 3 );
			r.bits( 1 );
			r.utf8();
			int size = switch ( blockCode ) {
				case 1 -> 192;
				case 2, 3, 4, 5 -> 576 << ( blockCode - 2 );
				case 6 -> ( int ) r.bits( 8 ) + 1;
				case 7 -> ( int ) r.bits( 16 ) + 1;
				default -> 256 << ( blockCode - 8 );
			};
			if ( rateCode == 12 ) {
				r.bits( 8 );
			} else if ( rateCode == 13 || rateCode == 14 ) {
				r.bits( 16 );
			}
			int frameBps = switch ( sizeCode ) {
				case 1 -> 8;
				case 2 -> 12;
				case 4 -> 16;
				case 5 -> 20;
				case 6 -> 24;
				default -> bps;
			};
			r.bits( 8 );
			int			frameChannels	= assignment < 8 ? assignment + 1 : 2;
			long[][]	signals			= new long[ frameChannels ][];
			for ( int ch = 0; ch < frameChannels; ch++ ) {
				int bits = frameBps + ( ( assignment == 8 && ch == 1 ) || ( assignment == 9 && ch == 0 ) || ( assignment == 10 && ch == 1 ) ? 1 : 0 );
				signals[ ch ] = readSubframe( r, size, bits );
			}
			r.align();
			r.bits( 16 );
			if ( assignment == 8 ) {
				for ( int i = 0; i < size; i++ ) {
					signals[ 1 ][ i ] = signals[ 0 ][ i ] - signals[ 1 ][ i ];
				}
			} else if ( assignment == 9 ) {
				for ( int i = 0; i < size; i++ ) {
					signals[ 0 ][ i ] = signals[ 0 ][ i ] + signals[ 1 ][ i ];
				}
			} else if ( assignment == 10 ) {
				for ( int i = 0; i < size; i++ ) {
					long side = signals[ 1 ][ i ], mid = ( signals[ 0 ][ i ] << 1 ) | ( side & 1 );
					signals[ 0 ][ i ]	= ( mid + side ) >> 1;
					signals[ 1 ][ i ]	= ( mid - side ) >> 1;
				}
			}
			for ( int ch = 0; ch < Math.min( channels, frameChannels ); ch++ ) {
				for ( int i = 0; i < size && pos + i < total; i++ ) {
					out[ ch ][ pos + i ] = ( float ) ( signals[ ch ][ i ] / scale );
				}
			}
			pos += size;
		}
		return new AudioBuffer( out, rate );
	}

	private static long[] readSubframe( BitReader r, int size, int bps ) {
		r.bits( 1 );
		int	type	= ( int ) r.bits( 6 );
		int	wasted	= 0;
		if ( r.bits( 1 ) == 1 ) {
			wasted = 1;
			while ( r.bits( 1 ) == 0 ) {
				wasted++;
			}
		}
		int		bits	= bps - wasted;
		long[]	x		= new long[ size ];
		if ( type == 0 ) {
			long v = r.signed( bits );
			java.util.Arrays.fill( x, v );
		} else if ( type == 1 ) {
			for ( int i = 0; i < size; i++ ) {
				x[ i ] = r.signed( bits );
			}
		} else if ( type >= 8 && type <= 12 ) {
			int order = type - 8;
			for ( int i = 0; i < order; i++ ) {
				x[ i ] = r.signed( bits );
			}
			long[] res = readResidual( r, size, order );
			for ( int i = order; i < size; i++ ) {
				long prediction = switch ( order ) {
					case 0 -> 0;
					case 1 -> x[ i - 1 ];
					case 2 -> 2 * x[ i - 1 ] - x[ i - 2 ];
					case 3 -> 3 * x[ i - 1 ] - 3 * x[ i - 2 ] + x[ i - 3 ];
					default -> 4 * x[ i - 1 ] - 6 * x[ i - 2 ] + 4 * x[ i - 3 ] - x[ i - 4 ];
				};
				x[ i ] = prediction + res[ i - order ];
			}
		} else if ( type >= 32 ) {
			int order = type - 31;
			for ( int i = 0; i < order; i++ ) {
				x[ i ] = r.signed( bits );
			}
			int		precision	= ( int ) r.bits( 4 ) + 1;
			int		shift		= ( int ) r.signed( 5 );
			long[]	coefs		= new long[ order ];
			for ( int i = 0; i < order; i++ ) {
				coefs[ i ] = r.signed( precision );
			}
			long[] res = readResidual( r, size, order );
			for ( int i = order; i < size; i++ ) {
				long sum = 0;
				for ( int j = 0; j < order; j++ ) {
					sum += coefs[ j ] * x[ i - 1 - j ];
				}
				x[ i ] = ( sum >> shift ) + res[ i - order ];
			}
		} else {
			throw new BoxRuntimeException( "The FLAC file uses a subframe type that is reserved (" + type + ")" );
		}
		if ( wasted > 0 ) {
			for ( int i = 0; i < size; i++ ) {
				x[ i ] <<= wasted;
			}
		}
		return x;
	}

	private static long[] readResidual( BitReader r, int size, int order ) {
		int		method		= ( int ) r.bits( 2 );
		int		paramBits	= method == 0 ? 4 : 5;
		int		escape		= method == 0 ? 15 : 31;
		int		partOrder	= ( int ) r.bits( 4 );
		int		parts		= 1 << partOrder;
		long[]	res			= new long[ size - order ];
		int		cursor		= 0;
		for ( int p = 0; p < parts; p++ ) {
			int	count	= ( size >> partOrder ) - ( p == 0 ? order : 0 );
			int	k		= ( int ) r.bits( paramBits );
			if ( k == escape ) {
				int raw = ( int ) r.bits( 5 );
				for ( int i = 0; i < count; i++ ) {
					res[ cursor + i ] = raw == 0 ? 0 : r.signed( raw );
				}
			} else {
				for ( int i = 0; i < count; i++ ) {
					long	q	= r.unary();
					long	u	= ( q << k ) | r.bits( k );
					res[ cursor + i ] = ( u & 1 ) == 0 ? u >>> 1 : - ( ( u + 1 ) >>> 1 );
				}
			}
			cursor += count;
		}
		return res;
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Checksums and bits
	// ---------------------------------------------------------------------------------------------------------------------------

	static int crc8( byte[] data ) {
		int crc = 0;
		for ( byte b : data ) {
			crc ^= b & 0xFF;
			for ( int i = 0; i < 8; i++ ) {
				crc = ( crc & 0x80 ) != 0 ? ( ( crc << 1 ) ^ 0x07 ) & 0xFF : ( crc << 1 ) & 0xFF;
			}
		}
		return crc;
	}

	static int crc16( byte[] data ) {
		int crc = 0;
		for ( byte b : data ) {
			crc ^= ( b & 0xFF ) << 8;
			for ( int i = 0; i < 8; i++ ) {
				crc = ( crc & 0x8000 ) != 0 ? ( ( crc << 1 ) ^ 0x8005 ) & 0xFFFF : ( crc << 1 ) & 0xFFFF;
			}
		}
		return crc;
	}

	private static final class BitWriter {

		private final ByteArrayOutputStream	out	= new ByteArrayOutputStream();
		private long						acc;
		private int							count;

		void bits( long value, int n ) {
			for ( int i = n - 1; i >= 0; i-- ) {
				this.acc = ( this.acc << 1 ) | ( ( value >> i ) & 1 );
				if ( ++this.count == 8 ) {
					this.out.write( ( int ) this.acc );
					this.acc	= 0;
					this.count	= 0;
				}
			}
		}

		void signed( long value, int n ) {
			bits( value & ( n >= 64 ? -1L : ( 1L << n ) - 1 ), n );
		}

		void unary( long q ) {
			for ( long i = 0; i < q; i++ ) {
				bits( 0, 1 );
			}
			bits( 1, 1 );
		}

		void utf8( long n ) {
			if ( n < 0x80 ) {
				bits( n, 8 );
			} else if ( n < 0x800 ) {
				bits( 0xC0 | ( n >> 6 ), 8 );
				bits( 0x80 | ( n & 0x3F ), 8 );
			} else if ( n < 0x10000 ) {
				bits( 0xE0 | ( n >> 12 ), 8 );
				bits( 0x80 | ( ( n >> 6 ) & 0x3F ), 8 );
				bits( 0x80 | ( n & 0x3F ), 8 );
			} else if ( n < 0x200000 ) {
				bits( 0xF0 | ( n >> 18 ), 8 );
				bits( 0x80 | ( ( n >> 12 ) & 0x3F ), 8 );
				bits( 0x80 | ( ( n >> 6 ) & 0x3F ), 8 );
				bits( 0x80 | ( n & 0x3F ), 8 );
			} else {
				bits( 0xF8 | ( n >> 24 ), 8 );
				bits( 0x80 | ( ( n >> 18 ) & 0x3F ), 8 );
				bits( 0x80 | ( ( n >> 12 ) & 0x3F ), 8 );
				bits( 0x80 | ( ( n >> 6 ) & 0x3F ), 8 );
				bits( 0x80 | ( n & 0x3F ), 8 );
			}
		}

		void bytes( byte[] data ) {
			for ( byte b : data ) {
				bits( b & 0xFF, 8 );
			}
		}

		void align() {
			while ( this.count != 0 ) {
				bits( 0, 1 );
			}
		}

		/**
		 * The whole bytes so far, for the header checksum
		 */
		byte[] toByteArrayPartial() {
			return this.out.toByteArray();
		}

		byte[] toByteArray() {
			return this.out.toByteArray();
		}
	}

	private static final class BitReader {

		private final byte[]	data;
		private int				bytePos;
		private int				bitPos;

		BitReader( byte[] data, int start ) {
			this.data		= data;
			this.bytePos	= start;
		}

		boolean hasMore() {
			return this.bytePos < this.data.length - 4;
		}

		int bytePosition() {
			return this.bytePos;
		}

		long bits( int n ) {
			long v = 0;
			for ( int i = 0; i < n; i++ ) {
				if ( this.bytePos >= this.data.length ) {
					throw new BoxRuntimeException( "The FLAC file ends in the middle of a frame" );
				}
				v = ( v << 1 ) | ( ( this.data[ this.bytePos ] >> ( 7 - this.bitPos ) ) & 1 );
				if ( ++this.bitPos == 8 ) {
					this.bitPos = 0;
					this.bytePos++;
				}
			}
			return v;
		}

		long signed( int n ) {
			if ( n == 0 ) {
				return 0;
			}
			long v = bits( n );
			return ( v & ( 1L << ( n - 1 ) ) ) != 0 ? v - ( 1L << n ) : v;
		}

		long unary() {
			long q = 0;
			while ( bits( 1 ) == 0 ) {
				q++;
			}
			return q;
		}

		void align() {
			if ( this.bitPos != 0 ) {
				this.bitPos = 0;
				this.bytePos++;
			}
		}

		void skipBytes( int n ) {
			this.bytePos += n;
		}

		long utf8() {
			int		first	= ( int ) bits( 8 );
			int		extra	= first < 0x80 ? 0 : first < 0xE0 ? 1 : first < 0xF0 ? 2 : first < 0xF8 ? 3 : first < 0xFC ? 4 : 5;
			long	v		= extra == 0 ? first : first & ( 0x3F >> extra );
			for ( int i = 0; i < extra; i++ ) {
				v = ( v << 6 ) | ( bits( 8 ) & 0x3F );
			}
			return v;
		}
	}

}
