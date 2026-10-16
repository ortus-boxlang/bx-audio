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

import java.util.Random;

import ortus.boxlang.modules.audio.types.AudioBuffer;

/**
 * Turns float samples into integers of a given bit depth. Rounding a quiet signal to 16 bits makes distortion that follows the music (a
 * fade or a reverb tail turns into a gritty staircase), so when the audio is not already made of 16 bit values it is dithered: a little
 * triangular noise, one least significant bit wide, is added before rounding, which turns the distortion into a steady hiss far below
 * the music. Audio that already sits exactly on the 16 bit grid, such as a 16 bit file read back, is written as it is, so a round trip
 * changes nothing. The noise comes from a generator with a fixed seed, so the same audio gives the same bytes every time.
 */
final class Quantizer {

	private final double	scale;
	private final long		min;
	private final long		max;
	private final boolean	dither;
	private final Random	random	= new Random( 0x5EED1L );

	/**
	 * @param audio The audio about to be written
	 * @param bits  The target bit depth, 16 or 24. Dither is used for 16 bits only.
	 */
	Quantizer( AudioBuffer audio, int bits ) {
		this.scale	= Math.pow( 2, bits - 1 );
		this.max	= ( long ) this.scale - 1;
		this.min	= -( long ) this.scale;
		this.dither	= bits <= 16 && !onGrid( audio, this.scale );
	}

	/**
	 * @return Whether every sample is already a multiple of one least significant bit, within a rounding error
	 */
	private static boolean onGrid( AudioBuffer audio, double scale ) {
		for ( int ch = 0; ch < audio.getChannels(); ch++ ) {
			float[] in = audio.channel( ch );
			for ( int i = 0; i < in.length; i++ ) {
				double x = in[ i ] * scale;
				if ( Math.abs( x - Math.rint( x ) ) > 1e-3 ) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * @return The next sample as a signed integer. Call it for the samples in the order they are written, so the dither is repeatable.
	 */
	int next( float sample ) {
		double x = Math.max( -1.0, Math.min( 1.0, sample ) ) * this.scale;
		if ( this.dither ) {
			x += this.random.nextDouble() - this.random.nextDouble();
		}
		return ( int ) Math.max( this.min, Math.min( this.max, Math.round( x ) ) );
	}

}
