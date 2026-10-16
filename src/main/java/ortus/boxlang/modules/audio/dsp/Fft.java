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

import ortus.boxlang.modules.audio.util.AudioException;

/**
 * A radix-2 fast Fourier transform, and the window and frequency helpers the analysis code needs
 */
public final class Fft {

	private final int		size;
	private final double[]	cos;
	private final double[]	sin;
	private final int[]		reverse;

	/**
	 * @param size A power of two, at least 2
	 */
	public Fft( int size ) {
		if ( size < 2 || Integer.bitCount( size ) != 1 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The FFT size must be a power of two, at least 2, but was " + size );
		}
		this.size		= size;
		this.cos		= new double[ size / 2 ];
		this.sin		= new double[ size / 2 ];
		this.reverse	= new int[ size ];
		for ( int i = 0; i < size / 2; i++ ) {
			this.cos[ i ]	= Math.cos( 2 * Math.PI * i / size );
			this.sin[ i ]	= -Math.sin( 2 * Math.PI * i / size );
		}
		int bits = Integer.numberOfTrailingZeros( size );
		for ( int i = 0; i < size; i++ ) {
			this.reverse[ i ] = Integer.reverse( i ) >>> ( 32 - bits );
		}
	}

	public int getSize() {
		return this.size;
	}

	/**
	 * Transform in place
	 *
	 * @param re The real parts, length equal to the size
	 * @param im The imaginary parts
	 */
	public void forward( double[] re, double[] im ) {
		for ( int i = 0; i < this.size; i++ ) {
			int j = this.reverse[ i ];
			if ( j > i ) {
				double t = re[ i ];
				re[ i ]	= re[ j ];
				re[ j ]	= t;
				t		= im[ i ];
				im[ i ]	= im[ j ];
				im[ j ]	= t;
			}
		}
		for ( int len = 2; len <= this.size; len <<= 1 ) {
			int	half	= len / 2;
			int	step	= this.size / len;
			for ( int start = 0; start < this.size; start += len ) {
				for ( int k = 0; k < half; k++ ) {
					double	wr	= this.cos[ k * step ];
					double	wi	= this.sin[ k * step ];
					int		a	= start + k;
					int		b	= a + half;
					double	tr	= re[ b ] * wr - im[ b ] * wi;
					double	ti	= re[ b ] * wi + im[ b ] * wr;
					re[ b ]	= re[ a ] - tr;
					im[ b ]	= im[ a ] - ti;
					re[ a ]	+= tr;
					im[ a ]	+= ti;
				}
			}
		}
	}

	/**
	 * Magnitudes of the first half of the spectrum of a real signal, scaled so a sine of amplitude 1 reads 1 at its bin
	 *
	 * @param samples The signal, shorter input is zero padded, longer is cut
	 * @param window  A window of the FFT size, or null for none
	 *
	 * @return size / 2 + 1 magnitudes
	 */
	public double[] magnitudes( float[] samples, int offset, double[] window ) {
		double[]	re	= new double[ this.size ];
		double[]	im	= new double[ this.size ];
		double		sum	= 0;
		for ( int i = 0; i < this.size; i++ ) {
			int		index	= offset + i;
			double	w		= window == null ? 1 : window[ i ];
			re[ i ]	= index >= 0 && index < samples.length ? samples[ index ] * w : 0;
			sum		+= w;
		}
		forward( re, im );
		double[] out = new double[ this.size / 2 + 1 ];
		for ( int i = 0; i < out.length; i++ ) {
			out[ i ] = 2 * Math.hypot( re[ i ], im[ i ] ) / sum;
		}
		return out;
	}

	public static double[] hann( int size ) {
		double[] w = new double[ size ];
		for ( int i = 0; i < size; i++ ) {
			w[ i ] = 0.5 - 0.5 * Math.cos( 2 * Math.PI * i / size );
		}
		return w;
	}

	public static int nextPowerOfTwo( int n ) {
		if ( n > 1 << 30 ) {
			throw new IllegalArgumentException( "No power of two above " + n + " fits in an int" );
		}
		int p = 2;
		while ( p < n ) {
			p <<= 1;
		}
		return p;
	}

}
