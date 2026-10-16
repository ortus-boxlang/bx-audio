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

import java.util.List;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Basic signal operations on {@link AudioBuffer}. Every operation returns a new buffer and leaves its input untouched.
 */
public final class Dsp {

	/**
	 * Half width of the resampling filter in input samples, per side. More taps are sharper and slower.
	 */
	private static final int RESAMPLE_HALF_TAPS = 32;

	private Dsp() {
	}

	/**
	 * Convert decibels to a linear gain factor
	 */
	public static double dbToLinear( double db ) {
		return Math.pow( 10.0, db / 20.0 );
	}

	/**
	 * Convert a linear gain factor to decibels, negative infinity for zero
	 */
	public static double linearToDb( double linear ) {
		return 20.0 * Math.log10( linear );
	}

	/**
	 * Apply a gain
	 *
	 * @param audio The input
	 * @param db    Gain in decibels, negative values make it quieter
	 */
	public static AudioBuffer gain( AudioBuffer audio, double db ) {
		requireFinite( db, "db" );
		return scale( audio, ( float ) dbToLinear( db ) );
	}

	/**
	 * Position a sound in the stereo field using a constant power law, so perceived loudness stays even across the field.
	 * <p>
	 * A mono buffer becomes stereo. A stereo buffer is treated as a balance control: the far side is attenuated and the near side is
	 * left alone. Other channel counts are rejected.
	 *
	 * @param audio The input, mono or stereo
	 * @param pan   -1 for hard left, 0 for center, 1 for hard right
	 */
	public static AudioBuffer pan( AudioBuffer audio, double pan ) {
		requireFinite( pan, "pan" );
		if ( pan < -1 || pan > 1 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Pan must be between -1 and 1 but was " + pan );
		}
		int frames = audio.getFrames();
		if ( audio.getChannels() == 1 ) {
			// Equal power: both sides are 0.7071 at center
			double		angle	= ( pan + 1 ) * Math.PI / 4;
			float		left	= ( float ) Math.cos( angle );
			float		right	= ( float ) Math.sin( angle );
			float[]		in		= audio.channel( 0 );
			float[][]	out		= new float[ 2 ][ frames ];
			for ( int i = 0; i < frames; i++ ) {
				out[ 0 ][ i ]	= in[ i ] * left;
				out[ 1 ][ i ]	= in[ i ] * right;
			}
			return new AudioBuffer( out, audio.getSampleRate() );
		}
		if ( audio.getChannels() == 2 ) {
			float		left	= pan > 0 ? ( float ) Math.cos( pan * Math.PI / 2 ) : 1f;
			float		right	= pan < 0 ? ( float ) Math.cos( -pan * Math.PI / 2 ) : 1f;
			float[][]	out		= new float[ 2 ][ frames ];
			for ( int i = 0; i < frames; i++ ) {
				out[ 0 ][ i ]	= audio.channel( 0 )[ i ] * left;
				out[ 1 ][ i ]	= audio.channel( 1 )[ i ] * right;
			}
			return new AudioBuffer( out, audio.getSampleRate() );
		}
		throw new AudioException( AudioException.INVALID_ARGUMENT,
		    "Pan works on mono or stereo audio but this buffer has " + audio.getChannels() + " channels" );
	}

	/**
	 * Fade in at the start and out at the end
	 *
	 * @param audio       The input
	 * @param fadeInSecs  Length of the fade in, 0 for none
	 * @param fadeOutSecs Length of the fade out, 0 for none
	 * @param smooth      true for a raised cosine curve, false for a straight line
	 */
	public static AudioBuffer fade( AudioBuffer audio, double fadeInSecs, double fadeOutSecs, boolean smooth ) {
		requireFinite( fadeInSecs, "fadeIn" );
		requireFinite( fadeOutSecs, "fadeOut" );
		if ( fadeInSecs < 0 || fadeOutSecs < 0 ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "Fade lengths cannot be negative" );
		}
		int			frames		= audio.getFrames();
		int			inFrames	= ( int ) Math.min( frames, Math.round( fadeInSecs * audio.getSampleRate() ) );
		int			outFrames	= ( int ) Math.min( frames, Math.round( fadeOutSecs * audio.getSampleRate() ) );
		AudioBuffer	out			= audio.copy();
		for ( int ch = 0; ch < out.getChannels(); ch++ ) {
			float[] data = out.channel( ch );
			for ( int i = 0; i < inFrames; i++ ) {
				data[ i ] *= ramp( ( double ) i / inFrames, smooth );
			}
			for ( int i = 0; i < outFrames; i++ ) {
				// The last frame of the buffer gets a gain of 0
				data[ frames - 1 - i ] *= ramp( ( double ) i / outFrames, smooth );
			}
		}
		return out;
	}

	/**
	 * Scale the audio so its loudest sample sits at a target level. Silence is returned unchanged.
	 *
	 * @param audio    The input
	 * @param targetDb Target peak in decibels relative to full scale, normally 0 or slightly below
	 */
	public static AudioBuffer normalize( AudioBuffer audio, double targetDb ) {
		requireFinite( targetDb, "targetDb" );
		float peak = audio.getPeak();
		// A peak this small is silence for every practical purpose, and scaling it up would overflow
		if ( peak < 1e-12f ) {
			return audio.copy();
		}
		return scale( audio, ( float ) ( dbToLinear( targetDb ) / peak ) );
	}

	/**
	 * Cut out a section
	 *
	 * @param audio     The input
	 * @param startSecs Start in seconds, clamped to the audio
	 * @param endSecs   End in seconds, clamped to the audio
	 */
	public static AudioBuffer trim( AudioBuffer audio, double startSecs, double endSecs ) {
		requireFinite( startSecs, "start" );
		requireFinite( endSecs, "end" );
		if ( startSecs < 0 || endSecs < startSecs ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Trim needs 0 <= start <= end but got start " + startSecs + " and end " + endSecs );
		}
		int			from	= ( int ) Math.min( audio.getFrames(), Math.round( startSecs * audio.getSampleRate() ) );
		int			to		= ( int ) Math.min( audio.getFrames(), Math.round( endSecs * audio.getSampleRate() ) );
		float[][]	out		= new float[ audio.getChannels() ][];
		for ( int ch = 0; ch < out.length; ch++ ) {
			out[ ch ] = java.util.Arrays.copyOfRange( audio.channel( ch ), from, to );
		}
		return new AudioBuffer( out, audio.getSampleRate() );
	}

	/**
	 * Join buffers end to end. They must share channel count and sample rate, convert them first if they do not.
	 */
	public static AudioBuffer concat( List<AudioBuffer> parts ) {
		if ( parts == null || parts.isEmpty() ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Concat needs at least one audio buffer" );
		}
		AudioBuffer	first	= parts.get( 0 );
		long		total	= 0;
		for ( AudioBuffer part : parts ) {
			if ( part.getChannels() != first.getChannels() || part.getSampleRate() != first.getSampleRate() ) {
				throw new AudioException( AudioException.IO, "Cannot join audio with different formats: " + first + " and " + part
				    + ". Use audioChannels() and audioResample() to match them first" );
			}
			total += part.getFrames();
		}
		if ( total > Integer.MAX_VALUE ) {
			throw new AudioException( AudioException.TOO_LARGE, "The joined audio is too long to hold in memory" );
		}
		float[][] out = new float[ first.getChannels() ][ ( int ) total ];
		for ( int ch = 0; ch < out.length; ch++ ) {
			int at = 0;
			for ( AudioBuffer part : parts ) {
				System.arraycopy( part.channel( ch ), 0, out[ ch ], at, part.getFrames() );
				at += part.getFrames();
			}
		}
		return new AudioBuffer( out, first.getSampleRate() );
	}

	/**
	 * Add buffers together sample by sample, all starting at time 0. The result is as long as the longest input. Inputs must share
	 * channel count and sample rate.
	 */
	public static AudioBuffer mix( List<AudioBuffer> parts ) {
		if ( parts == null || parts.isEmpty() ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Mix needs at least one audio buffer" );
		}
		AudioBuffer	first	= parts.get( 0 );
		int			frames	= 0;
		for ( AudioBuffer part : parts ) {
			if ( part.getChannels() != first.getChannels() || part.getSampleRate() != first.getSampleRate() ) {
				throw new AudioException( AudioException.IO, "Cannot mix audio with different formats: " + first + " and " + part
				    + ". Use audioChannels() and audioResample() to match them first" );
			}
			frames = Math.max( frames, part.getFrames() );
		}
		float[][] out = new float[ first.getChannels() ][ frames ];
		for ( AudioBuffer part : parts ) {
			for ( int ch = 0; ch < out.length; ch++ ) {
				float[]	in	= part.channel( ch );
				float[]	dst	= out[ ch ];
				for ( int i = 0; i < in.length; i++ ) {
					dst[ i ] += in[ i ];
				}
			}
		}
		return new AudioBuffer( out, first.getSampleRate() );
	}

	/**
	 * Play the audio backwards
	 */
	public static AudioBuffer reverse( AudioBuffer audio ) {
		int			frames	= audio.getFrames();
		float[][]	out		= new float[ audio.getChannels() ][ frames ];
		for ( int ch = 0; ch < out.length; ch++ ) {
			float[] in = audio.channel( ch );
			for ( int i = 0; i < frames; i++ ) {
				out[ ch ][ i ] = in[ frames - 1 - i ];
			}
		}
		return new AudioBuffer( out, audio.getSampleRate() );
	}

	/**
	 * Warm limiting: scale the loudest sample to full scale, push the signal through a tanh curve so peaks round off instead of
	 * clipping, and set the final peak level. A higher drive rounds off more of the signal.
	 *
	 * @param audio The input
	 * @param drive How hard to push into the curve, more than 0. Around 2 is gentle, 5 is heavy.
	 * @param level The final peak level as a linear amount from 0 to 1
	 */
	public static AudioBuffer saturate( AudioBuffer audio, double drive, double level ) {
		requireFinite( drive, "drive" );
		requireFinite( level, "level" );
		if ( drive <= 0 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Drive must be more than 0 but was " + drive );
		}
		if ( level < 0 || level > 1 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Level must be between 0 and 1 but was " + level );
		}
		float peak = audio.getPeak();
		if ( peak == 0f ) {
			return audio.copy();
		}
		double		norm	= Math.tanh( drive );
		float[][]	out		= new float[ audio.getChannels() ][];
		for ( int ch = 0; ch < out.length; ch++ ) {
			float[] in = audio.channel( ch );
			out[ ch ] = new float[ in.length ];
			for ( int i = 0; i < in.length; i++ ) {
				out[ ch ][ i ] = ( float ) ( Math.tanh( in[ i ] / peak * drive ) / norm * level );
			}
		}
		return new AudioBuffer( out, audio.getSampleRate() );
	}

	/**
	 * Filter types for {@link #filter}
	 */
	public static final java.util.List<String> FILTER_TYPES = java.util.List.of( "lowpass", "highpass", "bandpass", "notch" );

	/**
	 * Run the audio through a second order filter (the RBJ "audio EQ cookbook" biquads).
	 *
	 * @param audio  The input
	 * @param type   lowpass, highpass, bandpass (constant peak gain) or notch
	 * @param cutoff The corner or center frequency in Hz, above 0 and below half the sample rate
	 * @param q      Resonance. 0.7071 is flat, higher peaks at the cutoff, for bandpass and notch it sets the narrowness.
	 */
	public static AudioBuffer filter( AudioBuffer audio, String type, double cutoff, double q ) {
		String kind = type == null ? "" : type.toLowerCase( java.util.Locale.ROOT );
		if ( !FILTER_TYPES.contains( kind ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Unknown filter type [" + type + "]. Use one of " + FILTER_TYPES );
		}
		requireFinite( cutoff, "cutoff" );
		requireFinite( q, "q" );
		if ( cutoff <= 0 || cutoff >= audio.getSampleRate() / 2.0 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "The cutoff must be above 0 and below " + audio.getSampleRate() / 2.0 + " Hz but was " + cutoff );
		}
		if ( q <= 0 || q > 100 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Q must be above 0 and at most 100 but was " + q );
		}
		double	w0		= 2 * Math.PI * cutoff / audio.getSampleRate();
		double	cos		= Math.cos( w0 );
		double	alpha	= Math.sin( w0 ) / ( 2 * q );
		double	b0, b1, b2;
		switch ( kind ) {
			case "lowpass" -> {
				b0	= ( 1 - cos ) / 2;
				b1	= 1 - cos;
				b2	= ( 1 - cos ) / 2;
			}
			case "highpass" -> {
				b0	= ( 1 + cos ) / 2;
				b1	= - ( 1 + cos );
				b2	= ( 1 + cos ) / 2;
			}
			case "bandpass" -> {
				b0	= alpha;
				b1	= 0;
				b2	= -alpha;
			}
			default -> {
				b0	= 1;
				b1	= -2 * cos;
				b2	= 1;
			}
		}
		double	a0	= 1 + alpha;
		double	a1	= -2 * cos / a0;
		double	a2	= ( 1 - alpha ) / a0;
		b0	/= a0;
		b1	/= a0;
		b2	/= a0;
		float[][] out = new float[ audio.getChannels() ][ audio.getFrames() ];
		for ( int ch = 0; ch < out.length; ch++ ) {
			float[]	in	= audio.channel( ch );
			double	x1	= 0, x2 = 0, y1 = 0, y2 = 0;
			for ( int i = 0; i < in.length; i++ ) {
				double	x	= in[ i ];
				double	y	= b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
				x2				= x1;
				x1				= x;
				y2				= y1;
				y1				= y;
				out[ ch ][ i ]	= ( float ) y;
			}
		}
		return new AudioBuffer( out, audio.getSampleRate() );
	}

	/**
	 * Convert the channel count. Mono to any count copies the signal, any count to mono averages the channels. Other conversions are
	 * rejected because there is no single right answer.
	 */
	public static AudioBuffer toChannels( AudioBuffer audio, int channels ) {
		if ( channels < 1 || channels > AudioBuffer.MAX_CHANNELS ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Channels must be between 1 and " + AudioBuffer.MAX_CHANNELS + " but was " + channels );
		}
		if ( channels == audio.getChannels() ) {
			return audio.copy();
		}
		int frames = audio.getFrames();
		if ( audio.getChannels() == 1 ) {
			float[][] out = new float[ channels ][];
			for ( int ch = 0; ch < channels; ch++ ) {
				out[ ch ] = audio.channel( 0 ).clone();
			}
			return new AudioBuffer( out, audio.getSampleRate() );
		}
		if ( channels == 1 ) {
			float[]	mono	= new float[ frames ];
			float	scale	= 1f / audio.getChannels();
			for ( int ch = 0; ch < audio.getChannels(); ch++ ) {
				float[] in = audio.channel( ch );
				for ( int i = 0; i < frames; i++ ) {
					mono[ i ] += in[ i ] * scale;
				}
			}
			return new AudioBuffer( new float[][] { mono }, audio.getSampleRate() );
		}
		throw new AudioException( AudioException.IO, "Cannot convert " + audio.getChannels() + " channels to " + channels + ", only to or from mono" );
	}

	/**
	 * Change the sample rate with a windowed sinc filter. When lowering the rate the filter also removes content that would alias.
	 */
	public static AudioBuffer resample( AudioBuffer audio, int targetRate ) {
		if ( targetRate < AudioBuffer.MIN_SAMPLE_RATE || targetRate > AudioBuffer.MAX_SAMPLE_RATE ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "Sample rate must be between " + AudioBuffer.MIN_SAMPLE_RATE + " and " + AudioBuffer.MAX_SAMPLE_RATE
			        + " Hz but was " + targetRate );
		}
		if ( targetRate == audio.getSampleRate() ) {
			return audio.copy();
		}
		int		inFrames	= audio.getFrames();
		double	ratio		= ( double ) targetRate / audio.getSampleRate();
		long	outLong		= Math.round( inFrames * ratio );
		if ( outLong > Integer.MAX_VALUE ) {
			throw new AudioException( AudioException.TOO_LARGE, "The resampled audio is too long to hold in memory" );
		}
		int			outFrames	= ( int ) outLong;
		// Lowering the rate: stretch the filter so its cutoff sits at the new Nyquist
		double		cutoff		= Math.min( 1.0, ratio );
		double		halfWidth	= RESAMPLE_HALF_TAPS / cutoff;
		float[][]	out			= new float[ audio.getChannels() ][ outFrames ];

		// The position of output frame o in the input is o * q / p with p and q the rate ratio in lowest terms, so the fraction repeats every
		// p frames. The filter weights for each of the p fractions are computed once instead of for every output frame.
		long		g			= gcd( targetRate, audio.getSampleRate() );
		long		p			= targetRate / g;
		long		q			= audio.getSampleRate() / g;
		int			taps		= ( int ) ( 2 * halfWidth ) + 3;
		if ( p * taps > 4_000_000L ) {
			resampleDirect( audio, out, ratio, cutoff, halfWidth );
			return new AudioBuffer( out, targetRate );
		}
		int			phases		= ( int ) p;
		double[][]	weights		= new double[ phases ][];
		int[]		firstOffset	= new int[ phases ];
		double[]	sums		= new double[ phases ];
		for ( int phase = 0; phase < phases; phase++ ) {
			double		frac	= ( double ) phase / phases;
			int			first	= ( int ) Math.ceil( frac - halfWidth );
			int			last	= ( int ) Math.floor( frac + halfWidth );
			int			n		= last - first + 1;
			double[]	w		= new double[ n ];
			double		sum		= 0;
			for ( int k = 0; k < n; k++ ) {
				double x = ( first + k ) - frac;
				w[ k ]	= cutoff * sinc( x * cutoff ) * window( x / halfWidth );
				sum		+= w[ k ];
			}
			for ( int k = 0; k < n; k++ ) {
				w[ k ] /= sum;
			}
			weights[ phase ]		= w;
			firstOffset[ phase ]	= first;
			sums[ phase ]			= sum;
		}
		for ( int ch = 0; ch < out.length; ch++ ) {
			float[]	in		= audio.channel( ch );
			float[]	result	= out[ ch ];
			for ( int o = 0; o < outFrames; o++ ) {
				long		position	= ( long ) o * q;
				int			base		= ( int ) ( position / p );
				int			phase		= ( int ) ( position % p );
				double[]	w			= weights[ phase ];
				int			first		= base + firstOffset[ phase ];
				int			n			= w.length;
				double		acc			= 0;
				if ( first >= 0 && first + n <= inFrames ) {
					for ( int k = 0; k < n; k++ ) {
						acc += in[ first + k ] * w[ k ];
					}
				} else {
					// Near the ends some taps fall outside the data: use the ones inside, scaled so a constant signal keeps its level
					double inside = 0;
					for ( int k = 0; k < n; k++ ) {
						int idx = first + k;
						if ( idx >= 0 && idx < inFrames ) {
							acc		+= in[ idx ] * w[ k ];
							inside	+= w[ k ];
						}
					}
					acc = inside != 0 ? acc / inside : 0;
				}
				result[ o ] = ( float ) acc;
			}
		}
		return new AudioBuffer( out, targetRate );
	}

	private static long gcd( long a, long b ) {
		while ( b != 0 ) {
			long t = a % b;
			a	= b;
			b	= t;
		}
		return a;
	}

	/**
	 * The resampler without a table of weights, for rate ratios whose fractions repeat only after a very long time. It computes the weights
	 * for every output frame.
	 */
	private static void resampleDirect( AudioBuffer audio, float[][] out, double ratio, double cutoff, double halfWidth ) {
		int			inFrames	= audio.getFrames();
		int			outFrames	= out[ 0 ].length;
		double[]	w			= new double[ ( int ) ( 2 * halfWidth ) + 3 ];
		for ( int o = 0; o < outFrames; o++ ) {
			double	center	= o / ratio;
			int		first	= ( int ) Math.ceil( center - halfWidth );
			int		last	= ( int ) Math.floor( center + halfWidth );
			double	sum		= 0;
			int		n		= last - first + 1;
			for ( int k = 0; k < n; k++ ) {
				double x = ( first + k ) - center;
				w[ k ]	= cutoff * sinc( x * cutoff ) * window( x / halfWidth );
				sum		+= w[ k ];
			}
			double inside = 0;
			for ( int k = 0; k < n; k++ ) {
				int idx = first + k;
				if ( idx >= 0 && idx < inFrames ) {
					inside += w[ k ];
				}
			}
			double norm = inside != 0 ? sum / inside : 1;
			for ( int ch = 0; ch < out.length; ch++ ) {
				float[]	in	= audio.channel( ch );
				double	acc	= 0;
				for ( int k = 0; k < n; k++ ) {
					int idx = first + k;
					if ( idx >= 0 && idx < inFrames ) {
						acc += in[ idx ] * w[ k ];
					}
				}
				out[ ch ][ o ] = ( float ) ( acc * norm / sum );
			}
		}
	}

	private static AudioBuffer scale( AudioBuffer audio, float factor ) {
		float[][] out = new float[ audio.getChannels() ][];
		for ( int ch = 0; ch < out.length; ch++ ) {
			float[] in = audio.channel( ch );
			out[ ch ] = new float[ in.length ];
			for ( int i = 0; i < in.length; i++ ) {
				out[ ch ][ i ] = in[ i ] * factor;
			}
		}
		return new AudioBuffer( out, audio.getSampleRate() );
	}

	/**
	 * Gain for a fade at position t from 0 (silent end) to just under 1
	 */
	private static float ramp( double t, boolean smooth ) {
		return ( float ) ( smooth ? 0.5 - 0.5 * Math.cos( Math.PI * t ) : t );
	}

	private static double sinc( double x ) {
		if ( x == 0 ) {
			return 1;
		}
		double px = Math.PI * x;
		return Math.sin( px ) / px;
	}

	/**
	 * Hann window over -1 to 1
	 */
	private static double window( double x ) {
		return Math.abs( x ) >= 1 ? 0 : 0.5 + 0.5 * Math.cos( Math.PI * x );
	}

	private static void requireFinite( double value, String name ) {
		if ( Double.isNaN( value ) || Double.isInfinite( value ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The " + name + " argument must be a finite number but was " + value );
		}
	}

}
