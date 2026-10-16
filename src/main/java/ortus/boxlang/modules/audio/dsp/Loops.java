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
 * Making audio loop without a click
 */
public final class Loops {

	private Loops() {
	}

	/**
	 * Make a sound loop seamlessly by folding its end into its start: the last {@code crossfade} seconds are faded out while the first ones are
	 * faded in over them, and removed from the end. The fade keeps the level steady: equal power for parts that have nothing in common, and a
	 * straight fade, with no 3 dB bump in the middle, for parts that are the same sound (a held pad), with the blend in between following how
	 * alike the two parts are. Played in a loop, the end then runs straight into the start. Content that
	 * continues across the join, such as a pad, a reverb tail or a delay, comes out smooth.
	 *
	 * @param audio     The sound
	 * @param crossfade Seconds to overlap, at most half the sound
	 *
	 * @return A buffer shorter by the crossfade
	 */
	public static AudioBuffer loopify( AudioBuffer audio, double crossfade ) {
		int fade = ( int ) Math.round( crossfade * audio.getSampleRate() );
		if ( fade <= 0 ) {
			return audio.copy();
		}
		if ( fade * 2 > audio.getFrames() ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "The crossfade of " + crossfade + " s is longer than half of the sound (" + audio.getDurationSeconds() + " s)" );
		}
		int			outFrames	= audio.getFrames() - fade;
		double		alike		= alike( audio, fade, outFrames );
		float[][]	out			= new float[ audio.getChannels() ][ outFrames ];
		for ( int ch = 0; ch < out.length; ch++ ) {
			float[] in = audio.channel( ch );
			System.arraycopy( in, 0, out[ ch ], 0, outFrames );
			for ( int i = 0; i < fade; i++ ) {
				double	t		= ( i + 0.5 ) / fade;
				double	fadeIn	= Math.sin( t * Math.PI / 2 );
				double	fadeOut	= Math.cos( t * Math.PI / 2 );
				// Two copies of the same sound add to 1 + sin(2t) times the power, two unrelated ones to 1, so the gain is set to make the sum
				// come out at one whatever they are
				double	level	= 1 / Math.sqrt( 1 + alike * Math.sin( t * Math.PI ) );
				// The start, rising, over the part of the end that was cut off, falling
				out[ ch ][ i ] = ( float ) ( ( in[ i ] * fadeIn + in[ outFrames + i ] * fadeOut ) * level );
			}
		}
		return new AudioBuffer( out, audio.getSampleRate() );
	}

	/**
	 * How alike the start and the part cut from the end are: 0 for nothing in common, 1 for the same sound
	 */
	private static double alike( AudioBuffer audio, int fade, int outFrames ) {
		double cross = 0, head = 0, tail = 0;
		for ( int ch = 0; ch < audio.getChannels(); ch++ ) {
			float[] in = audio.channel( ch );
			for ( int i = 0; i < fade; i++ ) {
				double	a	= in[ i ];
				double	b	= in[ outFrames + i ];
				cross	+= a * b;
				head	+= a * a;
				tail	+= b * b;
			}
		}
		if ( head <= 0 || tail <= 0 ) {
			return 0;
		}
		return Math.max( 0, Math.min( 1, cross / Math.sqrt( head * tail ) ) );
	}

}
