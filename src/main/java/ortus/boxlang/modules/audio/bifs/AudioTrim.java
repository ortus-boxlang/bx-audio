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
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Cut a section out of an audio buffer" )
public class AudioTrim extends BIF {

	/**
	 * Constructor
	 */
	public AudioTrim() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.audio ),
		    new Argument( false, "double", Key.start, 0.0 ),
		    new Argument( false, "double", Key.end )
		};
	}

	/**
	 * Cut a section out of an audio buffer. The input buffer is left untouched.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.start Where the section starts, in seconds. Defaults to 0.
	 *
	 * @argument.end Where the section ends, in seconds. Defaults to the end of the audio. Values past the end are clamped.
	 *
	 * @return A new audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		AudioBuffer	audio	= Audios.require( arguments.get( KeyDictionary.audio ), "audio" );
		Double		end		= arguments.getAsDouble( Key.end );
		return Dsp.trim( audio, arguments.getAsDouble( Key.start ), end == null ? audio.getDurationSeconds() : end );
	}

}
