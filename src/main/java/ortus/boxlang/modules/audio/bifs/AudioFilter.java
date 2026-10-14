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

import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Filter an audio buffer with a low-pass, high-pass, band-pass or notch filter" )
public class AudioFilter extends BIF {

	/**
	 * Constructor
	 */
	public AudioFilter() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.audio ),
		    new Argument( true, "string", KeyDictionary.type ),
		    new Argument( true, "double", KeyDictionary.cutoff ),
		    new Argument( false, "double", KeyDictionary.q, 0.7071 )
		};
	}

	/**
	 * Filter an audio buffer with a low-pass, high-pass, band-pass or notch filter
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.type lowpass, highpass, bandpass or notch.
	 *
	 * @argument.cutoff The corner or center frequency in Hz.
	 *
	 *                  @argument.q Resonance. 0.7071 is flat, higher peaks at the cutoff. For bandpass and notch it sets how narrow they are. Defaults to
	 *                  0.7071.
	 *
	 * @return A new audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Dsp.filter(
		    Audios.require( arguments.get( KeyDictionary.audio ), "audio" ),
		    arguments.getAsString( KeyDictionary.type ),
		    arguments.getAsDouble( KeyDictionary.cutoff ),
		    arguments.getAsDouble( KeyDictionary.q )
		);
	}

}
