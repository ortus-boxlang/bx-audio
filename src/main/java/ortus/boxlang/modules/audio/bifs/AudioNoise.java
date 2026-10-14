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
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Generate repeatable noise" )
public class AudioNoise extends BIF {

	/**
	 * Constructor
	 */
	public AudioNoise() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "double", Key.seconds ),
		    new Argument( false, "integer", KeyDictionary.sampleRate, 44100 ),
		    new Argument( false, "long", KeyDictionary.seed, 1L ),
		    new Argument( false, "string", KeyDictionary.color, "white" )
		};
	}

	/**
	 * Generate repeatable noise
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.seconds The length in seconds.
	 *
	 * @argument.sampleRate The sample rate in Hz. Defaults to 44100.
	 *
	 * @argument.seed Seed for the random generator. The same seed always gives the same noise. Defaults to 1.
	 *
	 * @argument.color white (flat), pink (softer highs), brown (rumble) or blue (hissy highs). Defaults to white.
	 *
	 * @return A mono audio buffer at an RMS level of 0.25.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Synth.noise(
		    arguments.getAsDouble( Key.seconds ),
		    arguments.getAsInteger( KeyDictionary.sampleRate ),
		    arguments.getAsLong( KeyDictionary.seed ),
		    arguments.getAsString( KeyDictionary.color )
		);
	}

}
