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
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Convolution reverb: the sound is played through the recorded echo of a real space (an impulse response). The work is done in blocks with the FFT
 * (overlap-add), so a 5 second room costs about the same as a 1 second one.
 */
public final class Convolution {

	/** The longest impulse response taken, in seconds */
	public static final double	MAX_IMPULSE_SECONDS	= 20;

	private static final int	MIN_FFT				= 4096;

	private Convolution() {
	}

	/**
	 * @param audio   The sound, mono or stereo
	 * @param impulse The impulse response, mono or stereo, at any sample rate
	 * @param mix     Dry/wet balance, 0 is only the input and 1 is only the room
	 *
	 * @return A new buffer, as long as the sound plus the impulse response. A mono sound with a stereo impulse becomes stereo, and a stereo sound
	 *         with a mono impulse stays stereo.
	 */
	public static AudioBuffer convolve( AudioBuffer audio, AudioBuffer impulse, double mix ) {
		if ( Double.isNaN( mix ) || mix < 0 || mix > 1 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The mix must be between 0 and 1 but was " + mix );
		}
		if ( audio.getChannels() > 2 || impulse.getChannels() > 2 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Convolution works on mono or stereo audio and impulse responses" );
		}
		if ( impulse.getFrames() == 0 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The impulse response is empty" );
		}
		int rate = audio.getSampleRate();
		if ( impulse.getDurationSeconds() > MAX_IMPULSE_SECONDS ) {
			throw new AudioException( AudioException.TOO_LARGE,
			    "The impulse response is " + Math.round( impulse.getDurationSeconds() ) + " seconds, the most that is used is " + ( int ) MAX_IMPULSE_SECONDS );
		}
		AudioBuffer	ir		= impulse.getSampleRate() == rate ? impulse : Dsp.resample( impulse, rate );
		int			frames	= audio.getFrames() + ir.getFrames() - 1;
		int			ch		= Math.max( audio.getChannels(), ir.getChannels() );

		// One scale for both sides so the image of the room is kept: the impulse response has unit energy, which keeps the level of noisy
		// material about where it was
		double		energy	= 0;
		for ( int c = 0; c < ir.getChannels(); c++ ) {
			double sum = 0;
			for ( float v : ir.channel( c ) ) {
				sum += ( double ) v * v;
			}
			energy = Math.max( energy, sum );
		}
		double		scale	= energy > 0 ? 1 / Math.sqrt( energy ) : 0;

		float[][]	out		= new float[ ch ][ frames ];
		float		dry		= ( float ) ( 1 - mix );
		for ( int c = 0; c < ch; c++ ) {
			float[]		in		= audio.channel( Math.min( c, audio.getChannels() - 1 ) );
			float[]		taps	= ir.channel( Math.min( c, ir.getChannels() - 1 ) );
			double[]	wet		= wet( in, taps, scale, frames );
			for ( int n = 0; n < frames; n++ ) {
				out[ c ][ n ] = ( n < in.length ? in[ n ] * dry : 0f ) + ( float ) ( wet[ n ] * mix );
			}
		}
		return new AudioBuffer( out, rate );
	}

	/**
	 * Overlap-add convolution of a signal with the taps, scaled
	 */
	private static double[] wet( float[] signal, float[] taps, double scale, int frames ) {
		int			m		= taps.length;
		int			size	= Math.max( MIN_FFT, Fft.nextPowerOfTwo( 2 * m ) );
		int			block	= size - m + 1;
		Fft			fft		= new Fft( size );
		double[]	hr		= new double[ size ];
		double[]	hi		= new double[ size ];
		for ( int i = 0; i < m; i++ ) {
			hr[ i ] = taps[ i ] * scale;
		}
		fft.forward( hr, hi );

		double[]	out	= new double[ frames ];
		double[]	xr	= new double[ size ];
		double[]	xi	= new double[ size ];
		for ( int start = 0; start < signal.length; start += block ) {
			int len = Math.min( block, signal.length - start );
			java.util.Arrays.fill( xr, 0 );
			java.util.Arrays.fill( xi, 0 );
			for ( int i = 0; i < len; i++ ) {
				xr[ i ] = signal[ start + i ];
			}
			fft.forward( xr, xi );
			for ( int k = 0; k < size; k++ ) {
				double	re	= xr[ k ] * hr[ k ] - xi[ k ] * hi[ k ];
				double	im	= xr[ k ] * hi[ k ] + xi[ k ] * hr[ k ];
				xr[ k ]	= re;
				xi[ k ]	= im;
			}
			// the inverse transform is the forward one with the parts swapped
			fft.forward( xi, xr );
			int end = Math.min( frames, start + len + m - 1 );
			for ( int n = start; n < end; n++ ) {
				out[ n ] += xr[ n - start ] / size;
			}
		}
		return out;
	}

}
