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

@BoxBIF( description = "Round off peaks with a warm limiting curve" )
public class AudioSaturate extends BIF {

	/**
	 * Constructor
	 */
	public AudioSaturate() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.audio ),
		    new Argument( false, "double", KeyDictionary.drive, 2.2 ),
		    new Argument( false, "double", KeyDictionary.level, 0.9 )
		};
	}

	/**
	 * Round off peaks with a warm limiting curve
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer. It is scaled to full scale first, so the result does not depend on its input level.
	 *
	 * @argument.drive How hard to push into the curve, above 0. About 2 is gentle and 5 is heavy. Defaults to 2.2.
	 *
	 * @argument.level The final peak level from 0 to 1. Defaults to 0.9.
	 *
	 * @return A new audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Dsp.saturate(
		    Audios.require( arguments.get( KeyDictionary.audio ), "audio" ),
		    arguments.getAsDouble( KeyDictionary.drive ),
		    arguments.getAsDouble( KeyDictionary.level )
		);
	}

}
