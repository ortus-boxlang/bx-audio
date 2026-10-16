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

import java.util.Locale;
import java.util.Map;

import ortus.boxlang.modules.audio.analysis.Loudness;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Loudness targets and the gain-then-limit loop that reaches them
 */
public final class Mastering {

	public static final Map<String, Double> PRESETS = Map.of( "streaming", -14.0, "podcast", -16.0, "game", -16.0, "broadcast", -23.0, "cinema", -24.0 );

	private Mastering() {
	}

	/**
	 * @param value A preset name or a number of LUFS
	 */
	public static double target( Object value ) {
		if ( value instanceof Number n ) {
			return check( n.doubleValue() );
		}
		String	text	= String.valueOf( value ).trim().toLowerCase( Locale.ROOT );
		Double	preset	= PRESETS.get( text );
		if ( preset != null ) {
			return preset;
		}
		try {
			return check( Double.parseDouble( text ) );
		} catch ( NumberFormatException e ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "Unknown loudness target [" + value + "]. Use a number of LUFS or one of " + PRESETS.keySet() );
		}
	}

	private static double check( double lufs ) {
		if ( lufs > 0 || lufs < -70 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The loudness target must be between -70 and 0 LUFS but was " + lufs );
		}
		return lufs;
	}

	/**
	 * Reach an integrated loudness without any peak above the ceiling. The gain is applied and a limiter catches the peaks, which lowers the
	 * loudness a little, so the gain is corrected and the pass repeated a few times.
	 *
	 * @param truePeakDb The ceiling for the true peak in dBFS, the highest level the signal reaches between the samples.
	 */
	public static AudioBuffer toLoudness( AudioBuffer audio, double target, double truePeakDb ) {
		if ( truePeakDb > 0 || truePeakDb < -20 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The true peak ceiling must be between -20 and 0 dBFS but was " + truePeakDb );
		}
		double measured = Loudness.integrated( audio );
		if ( measured <= Loudness.FLOOR ) {
			return audio.copy();
		}
		double		correction	= target - measured;
		AudioBuffer	result		= audio;
		for ( int pass = 0; pass < 4; pass++ ) {
			AudioBuffer gained = Dsp.gain( audio, correction );
			// The true peak limiter keeps the peaks between the samples under the ceiling too
			result = Dynamics.limitTruePeak( gained, truePeakDb, 150, 5 );
			double	now		= Loudness.integrated( result );
			double	error	= target - now;
			if ( Math.abs( error ) < 0.05 ) {
				break;
			}
			correction += error;
			if ( correction + measured > 40 ) {
				break;
			}
		}
		return result;
	}

}
