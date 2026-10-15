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
package ortus.boxlang.modules.audio.util;

import java.util.Locale;

/**
 * Time as text in the forms the caption and edit formats use
 */
public final class Timecode {

	private Timecode() {
	}

	/**
	 * @return 00:01:02,345 as SRT writes it, or 00:01:02.345 when {@code comma} is false as WebVTT does
	 */
	public static String clock( double seconds, boolean comma ) {
		long ms = Math.round( Math.max( 0, seconds ) * 1000 );
		return String.format( Locale.ROOT, "%02d:%02d:%02d%s%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, comma ? "," : ".", ms % 1000 );
	}

	/**
	 * @return 00:01:02:15 frame based timecode as EDL files write it, at a whole frame rate
	 */
	public static String frames( double seconds, int fps ) {
		long total = Math.round( Math.max( 0, seconds ) * fps );
		return String.format( Locale.ROOT, "%02d:%02d:%02d:%02d", total / ( 3600L * fps ), total / ( 60L * fps ) % 60, total / fps % 60, total % fps );
	}

	/**
	 * @return 1:02 or 1:02:03, the way chapter lists are written
	 */
	public static String short_( double seconds ) {
		long s = Math.round( Math.max( 0, seconds ) );
		return s >= 3600 ? String.format( Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60 )
		    : String.format( Locale.ROOT, "%d:%02d", s / 60, s % 60 );
	}

}
