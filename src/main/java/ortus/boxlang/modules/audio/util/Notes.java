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

import ortus.boxlang.modules.audio.synth.Synth;

/**
 * Musical notes as arguments: a name such as C4, F#3 or Bb2, or a MIDI number
 */
public final class Notes {

	private Notes() {
	}

	/**
	 * @param value A note name, a MIDI number or text that holds one. Fractions detune: 60.1 is ten cents above middle C.
	 *
	 * @return The MIDI number, where 60 is middle C (C4) and 69 is A4
	 */
	public static double midi( Object value ) {
		double midi;
		if ( value instanceof Number number ) {
			midi = number.doubleValue();
		} else if ( value instanceof String name ) {
			try {
				midi = Double.parseDouble( name.trim() );
			} catch ( NumberFormatException e ) {
				return Synth.noteNameToMidi( name );
			}
		} else {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The note must be a note name like C4 or a MIDI number" );
		}
		if ( Double.isNaN( midi ) || midi < 0 || midi > 135 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "A MIDI note must be between 0 and 135 but was " + midi );
		}
		return midi;
	}

}
