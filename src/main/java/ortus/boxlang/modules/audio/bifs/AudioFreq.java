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
package ortus.boxlang.modules.audio.bifs;

import ortus.boxlang.modules.audio.synth.Synth;
import ortus.boxlang.runtime.dynamic.casters.DoubleCaster;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Convert a note name or MIDI number to a frequency in Hz" )
public class AudioFreq extends BIF {

	/**
	 * Constructor
	 */
	public AudioFreq() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.note )
		};
	}

	/**
	 * Convert a note name or MIDI number to a frequency in Hz
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.note A note name such as C4, F#3 or Bb2 (C4 is middle C), or a MIDI note number where 69 is A4. Fractions detune, 60.1 is ten cents above
	 *                C4.
	 *
	 * @return The frequency in Hz with A4 tuned to 440.
	 */
	public Double _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Object note = arguments.get( KeyDictionary.note );
		if ( note instanceof String name ) {
			var number = DoubleCaster.attempt( name );
			return Synth.midiToHz( number.wasSuccessful() ? number.get() : Synth.noteNameToMidi( name ) );
		}
		if ( note instanceof Number number ) {
			return Synth.midiToHz( number.doubleValue() );
		}
		throw new BoxRuntimeException( "The note must be a note name like C4 or a MIDI number" );
	}

}
