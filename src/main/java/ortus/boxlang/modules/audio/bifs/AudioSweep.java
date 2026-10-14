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

@BoxBIF( description = "Generate a sine that glides from one pitch to another" )
public class AudioSweep extends BIF {

	/**
	 * Constructor
	 */
	public AudioSweep() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "double", KeyDictionary.startHz ),
		    new Argument( true, "double", KeyDictionary.endHz ),
		    new Argument( true, "double", Key.seconds ),
		    new Argument( false, "double", KeyDictionary.speed, 28.0 ),
		    new Argument( false, "integer", KeyDictionary.sampleRate, 44100 )
		};
	}

	/**
	 * Generate a sine that glides from one pitch to another
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.startHz The frequency at the start, in Hz.
	 *
	 * @argument.endHz The frequency it settles towards, in Hz.
	 *
	 * @argument.seconds The length in seconds.
	 *
	 * @argument.speed How fast it settles, in 1/seconds. Higher is quicker. Defaults to 28.
	 *
	 * @argument.sampleRate The sample rate in Hz. Defaults to 44100.
	 *
	 * @return A mono audio buffer. Shape it with audioEnvelope() to make a kick or a laser.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Synth.sweep(
		    arguments.getAsDouble( KeyDictionary.startHz ),
		    arguments.getAsDouble( KeyDictionary.endHz ),
		    arguments.getAsDouble( Key.seconds ),
		    arguments.getAsDouble( KeyDictionary.speed ),
		    arguments.getAsInteger( KeyDictionary.sampleRate )
		);
	}

}
