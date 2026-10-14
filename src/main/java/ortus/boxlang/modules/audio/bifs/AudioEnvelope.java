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
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Shape the volume of a sound with an attack and an exponential decay" )
public class AudioEnvelope extends BIF {

	/**
	 * Constructor
	 */
	public AudioEnvelope() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.audio ),
		    new Argument( false, "double", KeyDictionary.attack, 0.005 ),
		    new Argument( false, "double", KeyDictionary.decay, 0.0 )
		};
	}

	/**
	 * Shape the volume of a sound with an attack and an exponential decay
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.attack Seconds to rise from silence to full volume in a straight line. 0 starts at full volume. Defaults to 0.005.
	 *
	 * @argument.decay The decay time constant in seconds, the level falls to 37% after one decay time. 0 means no decay. Defaults to 0.
	 *
	 * @return A new audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Synth.envelope(
		    Audios.require( arguments.get( KeyDictionary.audio ), "audio" ),
		    arguments.getAsDouble( KeyDictionary.attack ),
		    arguments.getAsDouble( KeyDictionary.decay )
		);
	}

}
