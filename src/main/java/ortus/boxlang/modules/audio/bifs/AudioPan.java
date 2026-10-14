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

@BoxBIF( description = "Position an audio buffer in the stereo field" )
public class AudioPan extends BIF {

	/**
	 * Constructor
	 */
	public AudioPan() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.audio ),
		    new Argument( true, "double", KeyDictionary.pan )
		};
	}

	/**
	 * Position an audio buffer in the stereo field. The input buffer is left untouched.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio A mono or stereo audio buffer. Mono becomes stereo, stereo is treated as a balance control.
	 *
	 * @argument.pan From -1 for hard left through 0 for center to 1 for hard right, using a constant power law.
	 *
	 * @return A new audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Dsp.pan( Audios.require( arguments.get( KeyDictionary.audio ), "audio" ), arguments.getAsDouble( KeyDictionary.pan ) );
	}

}
