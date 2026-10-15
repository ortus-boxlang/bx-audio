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
	 * Measure the loudness of a buffer
	 */
	public static Result measure( AudioBuffer audio ) {
		int			rate	= audio.getSampleRate();
		int			frames	= audio.getFrames();
		double[]	weights	= channelWeights( audio.getChannels() );
		// Sum of the weighted squares of the K-weighted channels, with a running total to take block means from
		double[]	prefix	= new double[ frames + 1 ];
		for ( int ch = 0; ch < audio.getChannels(); ch++ ) {
			if ( weights[ ch ] == 0 ) {
				continue;
			}
			double[]	filtered	= kWeight( audio.channel( ch ), rate );
			double		w			= weights[ ch ];
			for ( int i = 0; i < frames; i++ ) {
				prefix[ i + 1 ] += w * filtered[ i ] * filtered[ i ];
			}
		}
		for ( int i = 0; i < frames; i++ ) {
			prefix[ i + 1 ] += prefix[ i ];
		}
		double[]	momentary	= windows( prefix, rate, 0.4 );
		double[]	shortTerm	= windows( prefix, rate, 3.0 );
		double		integrated	= gatedIntegrated( momentary );
		return new Result( integrated, loudnessRange( shortTerm ), truePeakDb( audio ), toDb( audio.getPeak() ), maxOr( momentary ), maxOr( shortTerm ) );
	}

	/**
	 * @return The loudness of each window of the given length that fits, hopping 100 ms, as LUFS
	 */
	private static double[] windows( double[] prefix, int rate, double seconds ) {
		int	length	= ( int ) Math.round( seconds * rate );
		int	hop		= Math.max( 1, rate / 10 );
		int	frames	= prefix.length - 1;
		if ( frames < length ) {
			return new double[ 0 ];
		}
		int			count	= ( frames - length ) / hop + 1;
		double[]	out		= new double[ count ];
		for ( int b = 0; b < count; b++ ) {
			int start = b * hop;
			out[ b ] = lufs( ( prefix[ start + length ] - prefix[ start ] ) / length );
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
	 * BS.1770 channel weights for the usual layouts: mono is a center channel, stereo L R, 5.1 is L R C LFE Ls Rs where the LFE is not
	 * measured and the surrounds weigh 1.41
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
			weights[ 3 ]	= 0;
			weights[ 4 ]	= 1.41;
			weights[ 5 ]	= 1.41;
		}
		return weights;
	}

	/**
	 * The two K-weighting stages of BS.1770: a high shelf for the head and a high pass at about 38 Hz. The coefficients are computed for the
	 * sample rate from the analog prototypes, which gives the standard values at 48 kHz.
	 */
	static double[] kWeight( float[] in, int rate ) {
		double[]	shelf	= shelfCoefficients( rate );
		double[]	high	= highPassCoefficients( rate );
		double[]	out		= new double[ in.length ];
		double		x1		= 0, x2 = 0, y1 = 0, y2 = 0;
		for ( int i = 0; i < in.length; i++ ) {
			double	x	= in[ i ];
			double	y	= shelf[ 0 ] * x + shelf[ 1 ] * x1 + shelf[ 2 ] * x2 - shelf[ 3 ] * y1 - shelf[ 4 ] * y2;
			x2			= x1;
			x1			= x;
			y2			= y1;
			y1			= y;
			out[ i ]	= y;
		}
		x1 = x2 = y1 = y2 = 0;
		for ( int i = 0; i < in.length; i++ ) {
			double	x	= out[ i ];
			double	y	= high[ 0 ] * x + high[ 1 ] * x1 + high[ 2 ] * x2 - high[ 3 ] * y1 - high[ 4 ] * y2;
			x2			= x1;
			x1			= x;
			y2			= y1;
			y1			= y;
			out[ i ]	= y;
		}
		return out;
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
	 * Highest inter-sample peak in dBFS, from a 4x oversampled signal (2x from 96 kHz up, none from 192 kHz)
	 */
	public static double truePeakDb( AudioBuffer audio ) {
		int factor = audio.getSampleRate() >= 192000 ? 1 : audio.getSampleRate() >= 96000 ? 2 : 4;
		if ( factor == 1 ) {
			return toDb( audio.getPeak() );
		}
		// Polyphase windowed sinc, 32 taps per phase
		int			taps	= 32;
		double[][]	phases	= new double[ factor ][ taps ];
		for ( int p = 0; p < factor; p++ ) {
			double	sum		= 0;
			double	offset	= ( double ) p / factor;
			for ( int t = 0; t < taps; t++ ) {
				double x = t - taps / 2 + 1 - offset;
				phases[ p ][ t ]	= sinc( x ) * ( 0.5 + 0.5 * Math.cos( Math.PI * x / ( taps / 2.0 + 1 ) ) );
				sum					+= phases[ p ][ t ];
			}
			for ( int t = 0; t < taps; t++ ) {
				phases[ p ][ t ] /= sum;
			}
		}
		double peak = 0;
		for ( int ch = 0; ch < audio.getChannels(); ch++ ) {
			float[] in = audio.channel( ch );
			for ( int i = 0; i < in.length; i++ ) {
				for ( int p = 0; p < factor; p++ ) {
					double acc = 0;
					for ( int t = 0; t < taps; t++ ) {
						int idx = i + t - taps / 2 + 1;
						if ( idx >= 0 && idx < in.length ) {
							acc += in[ idx ] * phases[ p ][ t ];
						}
					}
					peak = Math.max( peak, Math.abs( acc ) );
				}
			}
		}
		return toDb( Math.max( peak, audio.getPeak() ) );
	}

	private static double sinc( double x ) {
		return x == 0 ? 1 : Math.sin( Math.PI * x ) / ( Math.PI * x );
	}

}
