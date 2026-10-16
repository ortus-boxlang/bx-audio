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
package ortus.boxlang.modules.audio.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.types.AudioBuffer;

/**
 * Loudness as ITU-R BS.1770-4 and EBU R128 define it: K-weighting, 400 ms gated blocks for the integrated value, 3 s windows for the
 * loudness range, and true peak from a 4x oversampled signal.
 */
public final class Loudness {

	/**
	 * What a measurement found. Loudness values are in LUFS, ranges in LU, peaks in dBFS. Silence reads {@link #FLOOR}.
	 *
	 * @param integrated   Gated loudness of the whole program
	 * @param range        Loudness range (LRA)
	 * @param truePeak     Highest inter-sample peak
	 * @param samplePeak   Highest sample
	 * @param momentaryMax Loudest 400 ms
	 * @param shortTermMax Loudest 3 s
	 */
	public record Result( double integrated, double range, double truePeak, double samplePeak, double momentaryMax, double shortTermMax ) {
	}

	public static final double FLOOR = -120.0;

	private Loudness() {
	}

	/**
	 * Measure the loudness of a buffer. A clip shorter than one 400 ms block is measured as a single block, so sound effects and short
	 * clips have a loudness too.
	 */
	public static Result measure( AudioBuffer audio ) {
		Energy		energy		= energy( audio );
		double[]	momentary	= windows( energy, 4 );
		double[]	shortTerm	= windows( energy, 30 );
		double		integrated	= gatedIntegrated( momentary );
		return new Result( integrated, loudnessRange( shortTerm ), truePeakDb( audio ), toDb( audio.getPeak() ), maxOr( momentary ), maxOr( shortTerm ) );
	}

	/**
	 * The integrated loudness alone, which skips the true peak and the loudness range. Mastering needs only this, several times.
	 */
	public static double integrated( AudioBuffer audio ) {
		return gatedIntegrated( windows( energy( audio ), 4 ) );
	}

	/**
	 * The K-weighted energy of a buffer in 100 ms segments, summed over the channels with their weights. Only whole segments count, except
	 * for a clip shorter than a 400 ms block, which counts as one block of its own length.
	 *
	 * @param segments The mean square of each whole 100 ms segment
	 * @param hop      The length of a segment in frames
	 * @param whole    The mean square of the whole clip, used when it is shorter than a block
	 */
	private record Energy( double[] segments, int hop, double whole, int frames ) {
	}

	private static Energy energy( AudioBuffer audio ) {
		int			rate	= audio.getSampleRate();
		int			frames	= audio.getFrames();
		int			hop		= Math.max( 1, rate / 10 );
		int			count	= frames / hop;
		double[]	sums	= new double[ count ];
		double		total	= 0;
		double[]	weights	= channelWeights( audio.getChannels() );
		double[]	shelf	= shelfCoefficients( rate );
		double[]	high	= highPassCoefficients( rate );
		for ( int ch = 0; ch < audio.getChannels(); ch++ ) {
			if ( weights[ ch ] == 0 ) {
				continue;
			}
			float[]	in	= audio.channel( ch );
			double	w	= weights[ ch ];
			// Both K-weighting stages in one pass, with the energy added straight into its segment
			double	x1	= 0, x2 = 0, y1 = 0, y2 = 0, h1 = 0, h2 = 0, z1 = 0, z2 = 0;
			for ( int i = 0; i < frames; i++ ) {
				double	x	= in[ i ];
				double	y	= shelf[ 0 ] * x + shelf[ 1 ] * x1 + shelf[ 2 ] * x2 - shelf[ 3 ] * y1 - shelf[ 4 ] * y2;
				x2	= x1;
				x1	= x;
				y2	= y1;
				y1	= y;
				if ( Math.abs( y ) < Dsp.SILENCE ) {
					y = 0;
				}
				double z = high[ 0 ] * y + high[ 1 ] * h1 + high[ 2 ] * h2 - high[ 3 ] * z1 - high[ 4 ] * z2;
				if ( Math.abs( z ) < Dsp.SILENCE ) {
					z = 0;
				}
				h2	= h1;
				h1	= y;
				z2	= z1;
				z1	= z;
				double e = w * z * z;
				total += e;
				int segment = i / hop;
				if ( segment < count ) {
					sums[ segment ] += e;
				}
			}
		}
		for ( int i = 0; i < count; i++ ) {
			sums[ i ] /= hop;
		}
		return new Energy( sums, hop, frames == 0 ? 0 : total / frames, frames );
	}

	/**
	 * @return The loudness of each window of the given number of segments, sliding one segment at a time, as LUFS. A clip shorter than a 400 ms
	 *         block gives one window of its own length.
	 */
	private static double[] windows( Energy energy, int segmentsPerWindow ) {
		double[] segments = energy.segments();
		if ( segments.length < segmentsPerWindow ) {
			// Only the 400 ms block has a meaning for a clip this short; a longer window is not measured
			return segmentsPerWindow == 4 && energy.frames() > 0 ? new double[] { lufs( energy.whole() ) } : new double[ 0 ];
		}
		int			count	= segments.length - segmentsPerWindow + 1;
		double[]	out		= new double[ count ];
		double		sum		= 0;
		for ( int i = 0; i < segmentsPerWindow; i++ ) {
			sum += segments[ i ];
		}
		out[ 0 ] = lufs( sum / segmentsPerWindow );
		for ( int b = 1; b < count; b++ ) {
			sum			+= segments[ b + segmentsPerWindow - 1 ] - segments[ b - 1 ];
			out[ b ]	= lufs( Math.max( 0, sum ) / segmentsPerWindow );
		}
		return out;
	}

	private static double lufs( double meanSquare ) {
		return meanSquare <= 0 ? Double.NEGATIVE_INFINITY : -0.691 + 10 * Math.log10( meanSquare );
	}

	private static double gatedIntegrated( double[] blocks ) {
		double	sum		= 0;
		int		count	= 0;
		for ( double b : blocks ) {
			if ( b > -70 ) {
				sum += Math.pow( 10, ( b + 0.691 ) / 10 );
				count++;
			}
		}
		if ( count == 0 ) {
			return FLOOR;
		}
		double relative = lufs( sum / count ) - 10;
		sum		= 0;
		count	= 0;
		for ( double b : blocks ) {
			if ( b > -70 && b > relative ) {
				sum += Math.pow( 10, ( b + 0.691 ) / 10 );
				count++;
			}
		}
		return count == 0 ? FLOOR : lufs( sum / count );
	}

	/**
	 * EBU Tech 3342: the spread between the 10th and the 95th percentile of the short-term loudness, after an absolute gate of -70 LUFS and a
	 * relative gate 20 LU below the mean
	 */
	private static double loudnessRange( double[] shortTerm ) {
		List<Double>	kept	= new ArrayList<>();
		double			sum		= 0;
		for ( double b : shortTerm ) {
			if ( b > -70 ) {
				kept.add( b );
				sum += Math.pow( 10, ( b + 0.691 ) / 10 );
			}
		}
		if ( kept.isEmpty() ) {
			return 0;
		}
		double		relative	= lufs( sum / kept.size() ) - 20;
		double[]	gated		= kept.stream().filter( b -> b > relative ).mapToDouble( Double::doubleValue ).sorted().toArray();
		if ( gated.length == 0 ) {
			return 0;
		}
		return percentile( gated, 0.95 ) - percentile( gated, 0.10 );
	}

	private static double percentile( double[] sorted, double p ) {
		double	position	= p * ( sorted.length - 1 );
		int		low			= ( int ) Math.floor( position );
		int		high		= ( int ) Math.ceil( position );
		return sorted[ low ] + ( sorted[ high ] - sorted[ low ] ) * ( position - low );
	}

	private static double maxOr( double[] values ) {
		return Arrays.stream( values ).filter( v -> !Double.isInfinite( v ) ).max().orElse( FLOOR );
	}

	private static double toDb( double linear ) {
		return linear <= 0 ? FLOOR : Math.max( FLOOR, 20 * Math.log10( linear ) );
	}

	/**
	 * BS.1770 channel weights for the usual layouts, with the channels in the order WAV files use: mono is a center channel, stereo L R, quad
	 * L R Ls Rs, 5.0 L R C Ls Rs, and from six channels L R C LFE and then the surrounds (5.1: Ls Rs, 6.1: Cs Ls Rs, 7.1: Lb Rb Ls Rs). The
	 * LFE is not measured and every surround weighs 1.41 (about +1.5 dB). Channels past the eighth have no weight defined and count as 1.
	 */
	static double[] channelWeights( int channels ) {
		double[] weights = new double[ channels ];
		Arrays.fill( weights, 1.0 );
		if ( channels == 4 ) {
			weights[ 2 ]	= 1.41;
			weights[ 3 ]	= 1.41;
		} else if ( channels == 5 ) {
			weights[ 3 ]	= 1.41;
			weights[ 4 ]	= 1.41;
		} else if ( channels >= 6 ) {
			weights[ 3 ] = 0;
			for ( int ch = 4; ch < Math.min( channels, 8 ); ch++ ) {
				weights[ ch ] = 1.41;
			}
		}
		return weights;
	}

	/**
	 * @return b0, b1, b2, a1, a2 of the shelf
	 */
	static double[] shelfCoefficients( int rate ) {
		double	f0	= 1681.974450955533;
		double	g	= 3.999843853973347;
		double	q	= 0.7071752369554196;
		double	k	= Math.tan( Math.PI * f0 / rate );
		double	vh	= Math.pow( 10, g / 20 );
		double	vb	= Math.pow( vh, 0.4996667741545416 );
		double	a0	= 1 + k / q + k * k;
		return new double[] {
		    ( vh + vb * k / q + k * k ) / a0,
		    2 * ( k * k - vh ) / a0,
		    ( vh - vb * k / q + k * k ) / a0,
		    2 * ( k * k - 1 ) / a0,
		    ( 1 - k / q + k * k ) / a0 };
	}

	static double[] highPassCoefficients( int rate ) {
		double	f0	= 38.13547087602444;
		double	q	= 0.5003270373238773;
		double	k	= Math.tan( Math.PI * f0 / rate );
		double	a0	= 1 + k / q + k * k;
		return new double[] { 1, -2, 1, 2 * ( k * k - 1 ) / a0, ( 1 - k / q + k * k ) / a0 };
	}

	/**
	 * The polyphase filters for true peak: windowed sinc, 32 taps per phase, computed once per oversampling factor
	 */
	private static final Map<Integer, double[][]>	PHASES	= new ConcurrentHashMap<>();

	private static final int						TAPS	= 32;

	private static double[][] phases( int factor ) {
		return PHASES.computeIfAbsent( factor, f -> {
			double[][] phases = new double[ f ][ TAPS ];
			for ( int p = 0; p < f; p++ ) {
				double	sum		= 0;
				double	offset	= ( double ) p / f;
				for ( int t = 0; t < TAPS; t++ ) {
					double x = t - TAPS / 2 + 1 - offset;
					phases[ p ][ t ]	= sinc( x ) * ( 0.5 + 0.5 * Math.cos( Math.PI * x / ( TAPS / 2.0 + 1 ) ) );
					sum					+= phases[ p ][ t ];
				}
				for ( int t = 0; t < TAPS; t++ ) {
					phases[ p ][ t ] /= sum;
				}
			}
			return phases;
		} );
	}

	/**
	 * Highest inter-sample peak in dBFS, from a 4x oversampled signal (2x from 96 kHz up, none from 192 kHz)
	 */
	public static double truePeakDb( AudioBuffer audio ) {
		int factor = audio.getSampleRate() >= 192000 ? 1 : audio.getSampleRate() >= 96000 ? 2 : 4;
		if ( factor == 1 ) {
			return toDb( audio.getPeak() );
		}
		double[][]	phases	= phases( factor );
		// The largest value any output sample can take is the sum of the absolute taps times the largest input in reach, so a stretch whose
		// samples cannot beat the peak found so far is skipped
		double		l1		= 0;
		for ( double[] phase : phases ) {
			double sum = 0;
			for ( double tap : phase ) {
				sum += Math.abs( tap );
			}
			l1 = Math.max( l1, sum );
		}
		final int	block	= 128;
		double		peak	= audio.getPeak();
		for ( int ch = 0; ch < audio.getChannels(); ch++ ) {
			float[]	in	= audio.channel( ch );
			int		n	= in.length;
			for ( int start = 0; start < n; start += block ) {
				int		end		= Math.min( n, start + block );
				// The inputs in reach of this block's outputs
				int		from	= Math.max( 0, start - TAPS / 2 + 1 );
				int		to		= Math.min( n, end + TAPS / 2 );
				float	local	= 0;
				for ( int i = from; i < to; i++ ) {
					local = Math.max( local, Math.abs( in[ i ] ) );
				}
				if ( local * l1 <= peak ) {
					continue;
				}
				for ( int i = start; i < end; i++ ) {
					int base = i - TAPS / 2 + 1;
					for ( int p = 0; p < factor; p++ ) {
						double[]	phase	= phases[ p ];
						int			lo		= Math.max( 0, -base );
						int			hi		= Math.min( TAPS, n - base );
						double		acc		= 0;
						for ( int t = lo; t < hi; t++ ) {
							acc += in[ base + t ] * phase[ t ];
						}
						peak = Math.max( peak, Math.abs( acc ) );
					}
				}
			}
		}
		return toDb( peak );
	}

	private static double sinc( double x ) {
		return x == 0 ? 1 : Math.sin( Math.PI * x ) / ( Math.PI * x );
	}

}
