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

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Time stretching and pitch shifting with WSOLA (waveform similarity overlap-add): the audio is cut into overlapping windows, which are laid
 * out again at a different spacing, each shifted a little to the place where it lines up best with the one before. That keeps the pitch of
 * the original when the speed changes. Pitch shifting stretches by the opposite amount and then plays the result at the new speed.
 */
public final class Stretch {

	public static final double	MIN_RATE	= 0.25;
	public static final double	MAX_RATE	= 4.0;

	private Stretch() {
	}

	/**
	 * Change the speed without changing the pitch
	 *
	 * @param audio The input
	 * @param rate  Speed factor: 2 plays twice as fast and is half as long, 0.5 is twice as long. Between {@value #MIN_RATE} and {@value #MAX_RATE}.
	 */
	public static AudioBuffer stretch( AudioBuffer audio, double rate ) {
		if ( Double.isNaN( rate ) || rate < MIN_RATE || rate > MAX_RATE ) {
			throw new BoxRuntimeException( "The rate must be between " + MIN_RATE + " and " + MAX_RATE + " but was " + rate );
		}
		if ( Math.abs( rate - 1 ) < 1e-6 || audio.getFrames() == 0 ) {
			return audio.copy();
		}
		// WSOLA stays clean for moderate changes, so a big one is made of two
		if ( rate > 2 || rate < 0.5 ) {
			double half = Math.sqrt( rate );
			return wsola( wsola( audio, half ), half );
		}
		return wsola( audio, rate );
	}

	/**
	 * Change the length to a number of seconds, keeping the pitch
	 */
	public static AudioBuffer fitTo( AudioBuffer audio, double seconds ) {
		if ( Double.isNaN( seconds ) || seconds <= 0 ) {
			throw new BoxRuntimeException( "The target length must be above 0 seconds but was " + seconds );
		}
		if ( audio.getFrames() == 0 ) {
			throw new BoxRuntimeException( "Cannot fit silence of no length to a duration" );
		}
		double		rate	= audio.getDurationSeconds() / seconds;
		AudioBuffer	result	= stretch( audio, rate );
		return exactLength( result, ( int ) Math.round( seconds * audio.getSampleRate() ) );
	}

	/**
	 * Change the pitch without changing the length
	 *
	 * @param semitones Up (positive) or down; 12 is an octave. Within plus or minus 24.
	 */
	public static AudioBuffer pitch( AudioBuffer audio, double semitones ) {
		if ( Double.isNaN( semitones ) || Math.abs( semitones ) > 24 ) {
			throw new BoxRuntimeException( "The shift must be within 24 semitones either way but was " + semitones );
		}
		if ( Math.abs( semitones ) < 1e-9 || audio.getFrames() == 0 ) {
			return audio.copy();
		}
		double		factor		= Math.pow( 2, semitones / 12 );
		AudioBuffer	longer		= stretch( audio, 1 / factor );
		// Play the stretched audio factor times faster: shorter by that factor and higher by it
		AudioBuffer	relabeled	= new AudioBuffer( copyChannels( longer ), ( int ) Math.round( audio.getSampleRate() * factor ) );
		AudioBuffer	back		= Dsp.resample( relabeled, audio.getSampleRate() );
		return exactLength( back, audio.getFrames() );
	}

	/**
	 * Play the audio faster or slower, so pitch and length change together like a record or a tape, which is how game effects are varied
	 *
	 * @param factor 2 is an octave up and half as long, 0.5 an octave down and twice as long
	 */
	public static AudioBuffer varispeed( AudioBuffer audio, double factor ) {
		if ( Double.isNaN( factor ) || factor < 0.125 || factor > 8 ) {
			throw new BoxRuntimeException( "The speed factor must be between 0.125 and 8 but was " + factor );
		}
		if ( Math.abs( factor - 1 ) < 1e-9 ) {
			return audio.copy();
		}
		AudioBuffer relabeled = new AudioBuffer( copyChannels( audio ), ( int ) Math.round( audio.getSampleRate() * factor ) );
		return Dsp.resample( relabeled, audio.getSampleRate() );
	}

	private static float[][] copyChannels( AudioBuffer audio ) {
		float[][] out = new float[ audio.getChannels() ][];
		for ( int ch = 0; ch < out.length; ch++ ) {
			out[ ch ] = audio.channel( ch ).clone();
		}
		return out;
	}

	private static AudioBuffer exactLength( AudioBuffer audio, int frames ) {
		if ( audio.getFrames() == frames ) {
			return audio;
		}
		float[][] out = new float[ audio.getChannels() ][ frames ];
		for ( int ch = 0; ch < out.length; ch++ ) {
			System.arraycopy( audio.channel( ch ), 0, out[ ch ], 0, Math.min( frames, audio.getFrames() ) );
		}
		return new AudioBuffer( out, audio.getSampleRate() );
	}

	private static AudioBuffer wsola( AudioBuffer audio, double rate ) {
		int			sr			= audio.getSampleRate();
		int			frames		= audio.getFrames();
		int			size		= Math.max( 64, ( ( int ) Math.round( 0.05 * sr ) ) & ~1 );
		int			hop			= size / 2;
		double		analysisHop	= hop * rate;
		int			tolerance	= size / 4;
		int			outFrames	= ( int ) Math.round( frames / rate );
		int			channels	= audio.getChannels();
		int			padded		= frames + size + 2 * tolerance + 8;
		float[][]	in			= new float[ channels ][ padded ];
		float[]		mono		= new float[ padded ];
		for ( int ch = 0; ch < channels; ch++ ) {
			System.arraycopy( audio.channel( ch ), 0, in[ ch ], tolerance, frames );
			for ( int i = 0; i < padded; i++ ) {
				mono[ i ] += in[ ch ][ i ] / channels;
			}
		}
		// Everything below is in padded coordinates: input index i of the audio is i + tolerance
		int			decimation	= 4;
		float[]		coarse		= decimate( mono, decimation );
		double[]	window		= new double[ size ];
		for ( int i = 0; i < size; i++ ) {
			window[ i ] = 0.5 - 0.5 * Math.cos( 2 * Math.PI * i / size );
		}
		float[][]	out			= new float[ channels ][ outFrames + size ];
		int			previous	= -1;
		for ( int k = 0;; k++ ) {
			int outPos = k * hop;
			if ( outPos >= outFrames ) {
				break;
			}
			int	nominal	= ( int ) Math.round( k * analysisHop ) + tolerance;
			int	chosen;
			if ( previous < 0 ) {
				chosen = nominal;
			} else {
				// The input right after the segment used last time is what should come next if the sound is to continue unbroken
				int template = previous + hop;
				chosen = bestMatch( mono, coarse, decimation, template, nominal, tolerance, hop );
			}
			previous = chosen;
			for ( int ch = 0; ch < channels; ch++ ) {
				float[] src = in[ ch ];
				for ( int i = 0; i < size; i++ ) {
					int s = chosen + i;
					if ( s >= 0 && s < padded ) {
						out[ ch ][ outPos + i ] += ( float ) ( src[ s ] * window[ i ] );
					}
				}
			}
			if ( nominal - tolerance > frames + tolerance ) {
				break;
			}
		}
		float[][] result = new float[ channels ][ outFrames ];
		for ( int ch = 0; ch < channels; ch++ ) {
			System.arraycopy( out[ ch ], 0, result[ ch ], 0, outFrames );
		}
		return new AudioBuffer( result, sr );
	}

	private static float[] decimate( float[] in, int factor ) {
		float[] out = new float[ in.length / factor ];
		for ( int i = 0; i < out.length; i++ ) {
			float sum = 0;
			for ( int j = 0; j < factor; j++ ) {
				sum += in[ i * factor + j ];
			}
			out[ i ] = sum / factor;
		}
		return out;
	}

	/**
	 * The start in the range nominal +- tolerance whose first {@code length} samples are most like the ones that begin at the template
	 */
	private static int bestMatch( float[] mono, float[] coarse, int decimation, int template, int nominal, int tolerance, int length ) {
		int	coarseLength	= length / decimation;
		int	coarseTemplate	= template / decimation;
		int	low				= Math.max( 0, ( nominal - tolerance ) / decimation );
		int	high			= Math.min( coarse.length - coarseLength - 1, ( nominal + tolerance ) / decimation );
		if ( high < low || coarseTemplate + coarseLength >= coarse.length ) {
			return nominal;
		}
		int		best		= low;
		double	bestScore	= Double.NEGATIVE_INFINITY;
		for ( int c = low; c <= high; c++ ) {
			double score = correlation( coarse, coarse, c, coarseTemplate, coarseLength, 1 );
			if ( score > bestScore ) {
				bestScore	= score;
				best		= c;
			}
		}
		// Refine at the full rate around the coarse winner
		int	centre	= best * decimation;
		int	lo		= Math.max( 0, Math.max( nominal - tolerance, centre - decimation ) );
		int	hi		= Math.min( mono.length - length - 1, Math.min( nominal + tolerance, centre + decimation ) );
		if ( template + length >= mono.length || hi < lo ) {
			return Math.max( 0, Math.min( mono.length - length - 1, centre ) );
		}
		int result = lo;
		bestScore = Double.NEGATIVE_INFINITY;
		for ( int c = lo; c <= hi; c++ ) {
			double score = correlation( mono, mono, c, template, length, 2 );
			if ( score > bestScore ) {
				bestScore	= score;
				result		= c;
			}
		}
		return result;
	}

	/**
	 * Normalised cross correlation of two stretches of a signal
	 */
	private static double correlation( float[] a, float[] b, int startA, int startB, int length, int stride ) {
		double dot = 0, energyA = 0, energyB = 0;
		for ( int i = 0; i < length; i += stride ) {
			double	x	= a[ startA + i ];
			double	y	= b[ startB + i ];
			dot		+= x * y;
			energyA	+= x * x;
			energyB	+= y * y;
		}
		double norm = Math.sqrt( energyA * energyB );
		return norm < 1e-12 ? 0 : dot / norm;
	}

}
