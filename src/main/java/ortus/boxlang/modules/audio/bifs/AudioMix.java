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

import java.util.ArrayList;
import java.util.List;

import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Add audio buffers together so they play at the same time" )
public class AudioMix extends BIF {

	/**
	 * Constructor
	 */
	public AudioMix() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "array", KeyDictionary.audios )
		};
	}

	/**
	 * Add audio buffers together so they play at the same time
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audios An array of audio buffers. They must share channel count and sample rate. All start at time 0, the result is as long as the
	 *                  longest.
	 *
	 * @return A new audio buffer. Scale it with audioGain() or audioNormalize() if the sum is too loud.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		List<AudioBuffer> parts = new ArrayList<>();
		for ( Object item : arguments.getAsArray( KeyDictionary.audios ) ) {
			parts.add( Audios.require( item, "audios" ) );
		}
		return Dsp.mix( parts );
	}

}
