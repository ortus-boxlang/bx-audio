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

import ortus.boxlang.modules.audio.synth.Drums;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Synthesise a drum hit" )
public class AudioDrum extends BIF {

	/**
	 * Constructor
	 */
	public AudioDrum() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "string", KeyDictionary.type ),
		    new Argument( false, "integer", KeyDictionary.sampleRate, 44100 ),
		    new Argument( false, "long", KeyDictionary.seed, 1L ),
		    new Argument( false, "double", KeyDictionary.pitch, 1.0 ),
		    new Argument( false, "double", KeyDictionary.decay, 1.0 ),
		    new Argument( false, "double", KeyDictionary.level, 1.0 ),
		    new Argument( false, "double", Key.seconds, 1.0 )
		};
	}

	/**
	 * Synthesise a drum hit
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.type kick, snare, hat, openhat, clap, tom, crash or riser.
	 *
	 * @argument.sampleRate The sample rate in Hz. Defaults to 44100.
	 *
	 * @argument.seed Seed for the noise, the same seed always gives the same hit. Defaults to 1.
	 *
	 * @argument.pitch Multiplier for the tuned part of kick, snare and tom. 1 is the default tuning, 0.8 is lower.
	 *
	 * @argument.decay Multiplier for how long the hit rings. 1 is the default, 2 is twice as long.
	 *
	 * @argument.level The peak level of the result, 0 to 1. Defaults to 1.
	 *
	 * @argument.seconds The length of a riser. Ignored by the other types.
	 *
	 * @return A mono audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Drums.drum(
		    arguments.getAsString( KeyDictionary.type ),
		    arguments.getAsInteger( KeyDictionary.sampleRate ),
		    arguments.getAsLong( KeyDictionary.seed ),
		    arguments.getAsDouble( KeyDictionary.pitch ),
		    arguments.getAsDouble( KeyDictionary.decay ),
		    arguments.getAsDouble( KeyDictionary.level ),
		    arguments.getAsDouble( Key.seconds )
		);
	}

}
