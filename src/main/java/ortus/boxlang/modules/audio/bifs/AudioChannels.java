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

@BoxBIF( description = "Convert an audio buffer between mono and stereo" )
public class AudioChannels extends BIF {

	/**
	 * Constructor
	 */
	public AudioChannels() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.audio ),
		    new Argument( true, "integer", KeyDictionary.channels )
		};
	}

	/**
	 * Convert an audio buffer between mono and stereo. The input buffer is left untouched.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.channels The target channel count. Mono to any count copies the signal, any count to mono averages the channels.
	 *
	 * @return A new audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Dsp.toChannels( Audios.require( arguments.get( KeyDictionary.audio ), "audio" ), arguments.getAsInteger( KeyDictionary.channels ) );
	}

}
