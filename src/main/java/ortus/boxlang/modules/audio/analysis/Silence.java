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

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Finding and trimming silence
 */
public final class Silence {

	private Silence() {
	}

	/**
	 * A quiet stretch
	 *
	 * @param start Seconds
	 * @param end   Seconds
	 */
	public record Range( double start, double end ) {

		public double duration() {
			return this.end - this.start;
		}
	}

	/**
	 * Find the stretches that stay below a level for at least a minimum time. The level is the peak over 10 ms windows across all channels.
	 *
	 * @param thresholdDb The level below which audio counts as silent
	 * @param minSeconds  The shortest silence worth reporting
	 */
	public static List<Range> detect( AudioBuffer audio, double thresholdDb, double minSeconds ) {
		if ( thresholdDb > 0 || thresholdDb < -120 ) {
			throw new BoxRuntimeException( "The threshold must be between -120 and 0 dB but was " + thresholdDb );
		}
		if ( minSeconds < 0 ) {
			throw new BoxRuntimeException( "The minimum length cannot be negative" );
		}
		int			rate		= audio.getSampleRate();
		int			window		= Math.max( 1, rate / 100 );
		double		threshold	= Math.pow( 10, thresholdDb / 20 );
		int			windows		= ( audio.getFrames() + window - 1 ) / window;
		List<Range>	ranges		= new ArrayList<>();
		int			runStart	= -1;
		for ( int w = 0; w <= windows; w++ ) {
			boolean quiet = w < windows;
			if ( quiet ) {
				int end = Math.min( audio.getFrames(), ( w + 1 ) * window );
				for ( int ch = 0; ch < audio.getChannels() && quiet; ch++ ) {
					float[] data = audio.channel( ch );
					for ( int i = w * window; i < end; i++ ) {
						if ( Math.abs( data[ i ] ) > threshold ) {
							quiet = false;
							break;
						}
					}
				}
			}
			if ( quiet && runStart < 0 ) {
				runStart = w;
			} else if ( !quiet && runStart >= 0 ) {
				double	from	= ( double ) runStart * window / rate;
				double	to		= Math.min( ( double ) w * window, audio.getFrames() ) / rate;
				if ( to - from >= minSeconds ) {
					ranges.add( new Range( from, to ) );
				}
				runStart = -1;
			}
		}
		return ranges;
	}

	/**
	 * Remove silence from the start and the end
	 *
	 * @param padSeconds How much silence to keep on each side
	 */
	public static AudioBuffer trim( AudioBuffer audio, double thresholdDb, double padSeconds ) {
		List<Range>	silent	= detect( audio, thresholdDb, 0 );
		double		total	= audio.getDurationSeconds();
		double		start	= 0, end = total;
		if ( !silent.isEmpty() && silent.get( 0 ).start() <= 0 ) {
			start = silent.get( 0 ).end();
		}
		if ( !silent.isEmpty() && Math.abs( silent.get( silent.size() - 1 ).end() - total ) < 1e-9 ) {
			end = silent.get( silent.size() - 1 ).start();
		}
		if ( end <= start ) {
			return AudioBuffer.silence( audio.getChannels(), 0, audio.getSampleRate() );
		}
		return ortus.boxlang.modules.audio.dsp.Dsp.trim( audio, Math.max( 0, start - padSeconds ), Math.min( total, end + padSeconds ) );
	}

}
