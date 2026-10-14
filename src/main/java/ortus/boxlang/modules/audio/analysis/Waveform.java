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

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Reduces audio to a handful of values for drawing a waveform
 */
public final class Waveform {

	/**
	 * Upper bound on the number of points, to catch bad arguments early
	 */
	public static final int MAX_POINTS = 100_000;

	private Waveform() {
	}

	/**
	 * Split the audio into equal slices and report the loudest absolute sample of each slice across all channels.
	 *
	 * @param audio  The audio to summarize
	 * @param points The number of slices, between 1 and {@link #MAX_POINTS}
	 *
	 * @return One value per slice, 0 for silence. If there are fewer frames than points, the slices repeat frames rather than being empty.
	 */
	public static double[] peaks( AudioBuffer audio, int points ) {
		if ( points < 1 || points > MAX_POINTS ) {
			throw new BoxRuntimeException( "Points must be between 1 and " + MAX_POINTS + " but was " + points );
		}
		double[]	result	= new double[ points ];
		int			frames	= audio.getFrames();
		if ( frames == 0 ) {
			return result;
		}
		for ( int i = 0; i < points; i++ ) {
			int	start	= ( int ) ( ( long ) i * frames / points );
			int	end		= ( int ) ( ( long ) ( i + 1 ) * frames / points );
			if ( end <= start ) {
				end = Math.min( frames, start + 1 );
			}
			float peak = 0f;
			for ( int ch = 0; ch < audio.getChannels(); ch++ ) {
				float[] samples = audio.channel( ch );
				for ( int f = start; f < end; f++ ) {
					float abs = Math.abs( samples[ f ] );
					if ( abs > peak ) {
						peak = abs;
					}
				}
			}
			result[ i ] = peak;
		}
		return result;
	}

}
