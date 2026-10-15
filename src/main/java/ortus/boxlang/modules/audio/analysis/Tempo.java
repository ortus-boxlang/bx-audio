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
import java.util.List;

import ortus.boxlang.modules.audio.dsp.Fft;
import ortus.boxlang.modules.audio.types.AudioBuffer;

/**
 * Tempo and beat detection: an onset strength curve from the change of the spectrum, a tempo from its autocorrelation, and beat positions
 * from dynamic programming that favors beats on strong onsets spaced one beat apart.
 */
public final class Tempo {

	/**
	 * @param bpm        The tempo, 0 when the audio has no discernible pulse
	 * @param confidence 0 to 1, how much the pulse stands out
	 * @param beats      Beat times in seconds
	 */
	public record Result( double bpm, double confidence, double[] beats ) {
	}

	private static final int FFT_SIZE = 1024;

	private Tempo() {
	}

	/**
	 * @param minBpm Lowest tempo to consider
	 * @param maxBpm Highest tempo to consider
	 */
	public static Result detect( AudioBuffer audio, double minBpm, double maxBpm ) {
		int			rate	= audio.getSampleRate();
		int			hop		= Math.max( 64, rate / 100 );
		double[]	onset	= onsetStrength( Spectrum.mono( audio ), hop );
		double		fps		= ( double ) rate / hop;
		int			minLag	= ( int ) Math.max( 2, Math.floor( fps * 60 / maxBpm ) );
		int			maxLag	= ( int ) Math.ceil( fps * 60 / minBpm );
		if ( onset.length < maxLag * 2 ) {
			return new Result( 0, 0, new double[ 0 ] );
		}
		double energy = 0;
		for ( double v : onset ) {
			energy += v * v;
		}
		if ( energy < 1e-9 ) {
			return new Result( 0, 0, new double[ 0 ] );
		}
		double[] auto = new double[ maxLag + 2 ];
		for ( int lag = minLag - 1; lag <= maxLag + 1; lag++ ) {
			double sum = 0;
			for ( int i = lag; i < onset.length; i++ ) {
				sum += onset[ i ] * onset[ i - lag ];
			}
			auto[ lag ] = sum / ( onset.length - lag );
		}
		// A mild preference for tempi people tap around 120, so a pulse and its half or double do not tie
		int		bestLag		= minLag;
		double	bestScore	= Double.NEGATIVE_INFINITY;
		double	mean		= 0;
		for ( int lag = minLag; lag <= maxLag; lag++ ) {
			double	bpm		= fps * 60 / lag;
			double	prior	= Math.exp( -0.5 * Math.pow( Math.log( bpm / 120 ) / Math.log( 2 ) / 1.4, 2 ) );
			double	score	= auto[ lag ] * prior;
			mean += auto[ lag ];
			if ( score > bestScore ) {
				bestScore	= score;
				bestLag		= lag;
			}
		}
		mean /= ( maxLag - minLag + 1 );
		// A steady pulse correlates as well at twice its period as at its period, and the prior can pick the slower one. Take the faster
		// when it is about as strong.
		int half = Math.round( bestLag / 2f );
		if ( half >= minLag && auto[ half ] >= 0.85 * auto[ bestLag ] ) {
			bestLag = half;
		}
		// Parabolic interpolation around the peak for a tempo finer than the frame rate
		double lag = bestLag;
		if ( bestLag > minLag && bestLag < maxLag ) {
			double	a		= auto[ bestLag - 1 ], b = auto[ bestLag ], c = auto[ bestLag + 1 ];
			double	denom	= a - 2 * b + c;
			if ( Math.abs( denom ) > 1e-12 ) {
				lag += 0.5 * ( a - c ) / denom;
			}
		}
		double	bpm			= fps * 60 / lag;
		double	confidence	= mean <= 0 ? 0 : Math.max( 0, Math.min( 1, ( auto[ bestLag ] / mean - 1 ) / 4 ) );
		return new Result( bpm, confidence, beats( onset, lag, fps, FFT_SIZE / 2.0 / rate ) );
	}

	/**
	 * Spectral flux: the growth of the log magnitude of each frequency bin from one frame to the next, summed, then made zero-mean
	 */
	static double[] onsetStrength( float[] mono, int hop ) {
		Fft			fft		= new Fft( FFT_SIZE );
		double[]	window	= Fft.hann( FFT_SIZE );
		int			frames	= Math.max( 0, ( mono.length - FFT_SIZE ) / hop + 1 );
		double[]	onset	= new double[ frames ];
		double[]	prev	= null;
		for ( int f = 0; f < frames; f++ ) {
			double[] mags = fft.magnitudes( mono, f * hop, window );
			for ( int i = 0; i < mags.length; i++ ) {
				mags[ i ] = Math.log1p( 1000 * mags[ i ] );
			}
			if ( prev != null ) {
				double flux = 0;
				for ( int i = 1; i < mags.length / 2; i++ ) {
					flux += Math.max( 0, mags[ i ] - prev[ i ] );
				}
				onset[ f ] = flux;
			}
			prev = mags;
		}
		// Remove the slow baseline so loud passages do not look like pulses
		double[]	out		= new double[ frames ];
		int			half	= 50;
		for ( int f = 0; f < frames; f++ ) {
			int		from	= Math.max( 0, f - half ), to = Math.min( frames - 1, f + half );
			double	sum		= 0;
			for ( int j = from; j <= to; j++ ) {
				sum += onset[ j ];
			}
			out[ f ] = Math.max( 0, onset[ f ] - sum / ( to - from + 1 ) );
		}
		return out;
	}

	/**
	 * Ellis style beat tracking: each frame's score is its onset strength plus the best earlier beat, penalised by how far the gap is from
	 * one beat period
	 */
	private static double[] beats( double[] onset, double period, double fps, double offset ) {
		int			n		= onset.length;
		double[]	score	= new double[ n ];
		int[]		back	= new int[ n ];
		double		tight	= 100;
		for ( int i = 0; i < n; i++ ) {
			double	best	= 0;
			int		from	= -1;
			int		low		= ( int ) Math.max( 0, Math.floor( i - 2 * period ) );
			int		high	= ( int ) Math.floor( i - period / 2 );
			for ( int j = low; j <= high; j++ ) {
				double	penalty		= tight * Math.pow( Math.log( ( i - j ) / period ), 2 );
				double	candidate	= score[ j ] - penalty;
				if ( candidate > best ) {
					best	= candidate;
					from	= j;
				}
			}
			score[ i ]	= onset[ i ] + best;
			back[ i ]	= from;
		}
		// Start the trace at the best frame of the last stretch of beat periods
		int		end	= n - 1;
		double	top	= Double.NEGATIVE_INFINITY;
		for ( int i = Math.max( 0, ( int ) ( n - period ) ); i < n; i++ ) {
			if ( score[ i ] > top ) {
				top	= score[ i ];
				end	= i;
			}
		}
		List<Double> times = new ArrayList<>();
		for ( int i = end; i >= 0; i = back[ i ] ) {
			times.add( 0, i / fps + offset );
			if ( back[ i ] < 0 ) {
				break;
			}
		}
		return times.stream().mapToDouble( Double::doubleValue ).toArray();
	}

}
