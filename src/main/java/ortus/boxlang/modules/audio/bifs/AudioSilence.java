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

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Create an audio buffer of silence" )
public class AudioSilence extends BIF {

	/**
	 * Constructor
	 */
	public AudioSilence() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "double", Key.seconds ),
		    new Argument( false, "integer", KeyDictionary.channels, 2 ),
		    new Argument( false, "integer", KeyDictionary.sampleRate, 44100 )
		};
	}

	/**
	 * Creates an audio buffer filled with silence. Useful as a blank canvas to mix sounds into.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.seconds The length in seconds, rounded to the nearest frame.
	 *
	 * @argument.channels The number of channels, 1 for mono, 2 for stereo. Defaults to 2.
	 *
	 * @argument.sampleRate The sample rate in Hz. Defaults to 44100.
	 *
	 * @return The new audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return AudioBuffer.silenceSeconds(
		    arguments.getAsDouble( Key.seconds ),
		    arguments.getAsInteger( KeyDictionary.channels ),
		    arguments.getAsInteger( KeyDictionary.sampleRate )
		);
	}

}
