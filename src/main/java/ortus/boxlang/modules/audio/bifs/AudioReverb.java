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

import ortus.boxlang.modules.audio.dsp.Effects;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Add reverb to an audio buffer" )
public class AudioReverb extends BIF {

	/**
	 * Constructor
	 */
	public AudioReverb() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.audio ),
		    new Argument( false, "double", KeyDictionary.room, 0.5 ),
		    new Argument( false, "double", KeyDictionary.damp, 0.5 ),
		    new Argument( false, "double", KeyDictionary.mix, 0.3 ),
		    new Argument( false, "double", KeyDictionary.tail )
		};
	}

	/**
	 * Add reverb to an audio buffer
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio A mono or stereo audio buffer. A stereo input gets a stereo-spread tail, mono stays mono.
	 *
	 * @argument.room The size of the room from 0 (small) to 1 (very large). Defaults to 0.5.
	 *
	 * @argument.damp High frequency absorption from 0 (bright) to 1 (dark and soft). Defaults to 0.5.
	 *
	 * @argument.mix Dry/wet balance, 0 is only the input and 1 is only the reverb. Defaults to 0.3. On sustained sounds a large room builds up level, so
	 *               lower the mix for big rooms.
	 *
	 * @argument.tail Seconds added at the end for the reverb to die away. By default it is picked from the room size, 0.5 to 6.5 seconds.
	 *
	 * @return A new, longer audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Effects.reverb(
		    Audios.require( arguments.get( KeyDictionary.audio ), "audio" ),
		    arguments.getAsDouble( KeyDictionary.room ),
		    arguments.getAsDouble( KeyDictionary.damp ),
		    arguments.getAsDouble( KeyDictionary.mix ),
		    arguments.getAsDouble( KeyDictionary.tail )
		);
	}

}
