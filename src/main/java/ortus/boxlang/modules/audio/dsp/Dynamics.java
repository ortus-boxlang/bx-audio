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
package ortus.boxlang.modules.audio.dsp;

import java.util.ArrayDeque;
import java.util.Deque;

import ortus.boxlang.modules.audio.analysis.Loudness;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Compression and limiting
 */
public final class Dynamics {

	private Dynamics() {
	}

	/**
	 * A feed-forward compressor. The level is detected on the loudest channel so the stereo image does not move, and the gain reduction is
	 * smoothed in decibels with separate attack and release times.
	 *
	 * @param audio       The input
	 * @param thresholdDb Level above which the signal is turned down
	 * @param ratio       Input change in dB for 1 dB of output change above the threshold, 1 for none, 20 and above is close to a limiter
	 * @param attackMs    How fast the gain goes down
	 * @param releaseMs   How fast the gain comes back
	 * @param kneeDb      Width of the soft knee around the threshold, 0 for a hard knee
	 * @param makeupDb    Gain added after compression.
	 */
	public static AudioBuffer compress( AudioBuffer audio, double thresholdDb, double ratio, double attackMs, double releaseMs, double kneeDb,
	    double makeupDb ) {
		if ( ratio < 1 || ratio > 100 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The ratio must be between 1 and 100 but was " + ratio );
		}
		if ( thresholdDb > 0 || thresholdDb < -100 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The threshold must be between -100 and 0 dB but was " + thresholdDb );
		}
		if ( attackMs < 0 || releaseMs < 0 || kneeDb < 0 ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "Attack, release and knee cannot be negative" );
		}
		int			rate		= audio.getSampleRate();
		double		attack		= coefficient( attackMs, rate );
		double		release		= coefficient( releaseMs, rate );
		double		makeup		= Dsp.dbToLinear( makeupDb );
		int			frames		= audio.getFrames();
		float[][]	out			= new float[ audio.getChannels() ][ frames ];
		double		reduction	= 0;
		for ( int i = 0; i < frames; i++ ) {
			double peak = 0;
			for ( int ch = 0; ch < out.length; ch++ ) {
				peak = Math.max( peak, Math.abs( audio.channel( ch )[ i ] ) );
			}
			double	level	= peak <= 1e-9 ? -180 : 20 * Math.log10( peak );
			double	target	= gainReduction( level, thresholdDb, ratio, kneeDb );
			// Reduction is positive in dB: more reduction is the attack, less is the release
			double	c		= target > reduction ? attack : release;
			reduction = c * reduction + ( 1 - c ) * target;
			double gain = Dsp.dbToLinear( -reduction ) * makeup;
			for ( int ch = 0; ch < out.length; ch++ ) {
				out[ ch ][ i ] = ( float ) ( audio.channel( ch )[ i ] * gain );
			}
		}
		return new AudioBuffer( out, rate );
	}

	/**
	 * @return Gain reduction in dB (positive) for an input level
	 */
	static double gainReduction( double level, double threshold, double ratio, double knee ) {
		double over = level - threshold;
		if ( knee > 0 && Math.abs( over ) <= knee / 2 ) {
			double x = over + knee / 2;
			return ( 1 - 1 / ratio ) * x * x / ( 2 * knee );
		}
		return over > 0 ? over * ( 1 - 1 / ratio ) : 0;
	}

	/**
	 * Compress, then set the gain so the result has the same RMS level as the input: the usual "auto make-up" that makes before and after
	 * comparable by ear
	 */
	public static AudioBuffer compressMatched( AudioBuffer audio, double thresholdDb, double ratio, double attackMs, double releaseMs, double kneeDb ) {
		AudioBuffer	out		= compress( audio, thresholdDb, ratio, attackMs, releaseMs, kneeDb, 0 );
		double		before	= audio.getRms();
		double		after	= out.getRms();
		if ( before <= 0 || after <= 0 ) {
			return out;
		}
		return Dsp.gain( out, Math.min( 24, Dsp.linearToDb( before / after ) ) );
	}

	/**
	 * A look-ahead limiter. The gain is lowered before a peak arrives, so no sample ends above the ceiling, and it recovers over the
	 * release time. The channels share one gain so the stereo image does not move.
	 *
	 * @param audio       The input
	 * @param ceilingDb   The highest sample level allowed, at most 0
	 * @param releaseMs   How fast the gain comes back after a peak
	 * @param lookAheadMs How far ahead the limiter looks, which is also how long the gain takes to go down
	 */
	public static AudioBuffer limit( AudioBuffer audio, double ceilingDb, double releaseMs, double lookAheadMs ) {
		if ( ceilingDb > 0 || ceilingDb < -60 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The ceiling must be between -60 and 0 dB but was " + ceilingDb );
		}
		if ( releaseMs < 0 || lookAheadMs < 0 || lookAheadMs > 100 ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "Release cannot be negative and the look-ahead must be between 0 and 100 ms" );
		}
		int		rate	= audio.getSampleRate();
		int		frames	= audio.getFrames();
		int		n		= Math.max( 1, ( int ) Math.round( lookAheadMs / 1000 * rate ) );
		double	ceiling	= Dsp.dbToLinear( ceilingDb );
		// Floats are enough for gains between 0 and 1, and they halve the memory of the working arrays on a long file
		float[]	need	= new float[ frames ];
		int		chs		= audio.getChannels();
		for ( int i = 0; i < frames; i++ ) {
			double peak = 0;
			for ( int ch = 0; ch < chs; ch++ ) {
				peak = Math.max( peak, Math.abs( audio.channel( ch )[ i ] ) );
			}
			need[ i ] = peak > ceiling ? ( float ) ( ceiling / peak ) : 1f;
		}
		// The lowest gain needed in the next n frames, then averaged over the n frames before, so the gain ramps down ahead of a peak and
		// never sits above what any frame in reach needs
		float[]	ahead	= slidingMinAhead( need, n );
		// The needs are not used again, so the smoothed gain takes their place
		float[]	smooth	= need;
		double	sum		= 0;
		for ( int i = 0; i < frames; i++ ) {
			sum += ahead[ i ];
			if ( i >= n ) {
				sum -= ahead[ i - n ];
			}
			int count = Math.min( i + 1, n );
			// Before the start the missing frames repeat the first value, so the opening of the file is protected like the rest
			smooth[ i ] = ( float ) ( ( sum + ( n - count ) * ( double ) ahead[ 0 ] ) / n );
		}
		ahead = null;
		double		relCoef	= coefficient( releaseMs, rate );
		float[][]	out		= new float[ audio.getChannels() ][ frames ];
		double		gain	= 1;
		for ( int i = 0; i < frames; i++ ) {
			double released = relCoef * gain + ( 1 - relCoef ) * 1.0;
			gain = Math.min( smooth[ i ], released );
			for ( int ch = 0; ch < out.length; ch++ ) {
				double v = audio.channel( ch )[ i ] * gain;
				out[ ch ][ i ] = ( float ) Math.max( -ceiling, Math.min( ceiling, v ) );
			}
		}
		return new AudioBuffer( out, rate );
	}

	/**
	 * A limiter whose ceiling holds for the true peak, the highest level the signal reaches between the samples. The sample peak limiter runs
	 * first, the true peak is measured on the result, and when it is over the ceiling the sample ceiling is lowered by the overshoot and the
	 * limiter runs again, a few times at most. Audio that goes on to lossy encoding or a converter then does not clip.
	 *
	 * @param ceilingDb The highest true peak allowed, in dBFS
	 */
	public static AudioBuffer limitTruePeak( AudioBuffer audio, double ceilingDb, double releaseMs, double lookAheadMs ) {
		double		sampleCeiling	= ceilingDb;
		AudioBuffer	out				= limit( audio, sampleCeiling, releaseMs, lookAheadMs );
		for ( int pass = 0; pass < 6; pass++ ) {
			double truePeak = Loudness.truePeakDb( out );
			if ( truePeak <= ceilingDb + 0.005 || sampleCeiling <= -60 ) {
				break;
			}
			sampleCeiling	= Math.max( -60, sampleCeiling - ( truePeak - ceilingDb ) - 0.02 );
			// the earlier pass is dropped first, so two copies of a long file are not held at once
			out				= null;
			out				= limit( audio, sampleCeiling, releaseMs, lookAheadMs );
		}
		return out;
	}

	/**
	 * @return For each index the minimum of values[i .. i + n - 1], clipped at the end
	 */
	private static float[] slidingMinAhead( float[] values, int n ) {
		float[]	out		= new float[ values.length ];
		// A ring of indexes, at most n + 1 deep, in place of a deque of boxed integers
		int[]	ring	= new int[ n + 1 ];
		int		head	= 0;
		int		size	= 0;
		for ( int i = values.length - 1; i >= 0; i-- ) {
			while ( size > 0 && values[ ring[ ( head + size - 1 ) % ring.length ] ] >= values[ i ] ) {
				size--;
			}
			ring[ ( head + size ) % ring.length ] = i;
			size++;
			while ( ring[ head ] > i + n - 1 ) {
				head = ( head + 1 ) % ring.length;
				size--;
			}
			out[ i ] = values[ ring[ head ] ];
		}
		return out;
	}

	private static double coefficient( double ms, int rate ) {
		return ms <= 0 ? 0 : Math.exp( -1.0 / ( ms / 1000.0 * rate ) );
	}

}
