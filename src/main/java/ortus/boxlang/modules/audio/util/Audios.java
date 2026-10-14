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

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Helpers shared by the BIFs
 */
public final class Audios {

	private Audios() {
	}

	/**
	 * Make sure an argument value is an audio buffer
	 *
	 * @param value The raw argument
	 * @param name  The argument name, used in the error message
	 */
	public static AudioBuffer require( Object value, String name ) {
		if ( value instanceof AudioBuffer audio ) {
			return audio;
		}
		throw new BoxRuntimeException(
		    "The " + name + " argument must be an audio buffer, for example from audioLoad() or audioSilence(), but was "
		        + ( value == null ? "null" : value.getClass().getSimpleName() ) );
	}

}
