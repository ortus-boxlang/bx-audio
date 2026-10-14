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

@BoxBIF( description = "Thicken and widen an audio buffer with a chorus" )
public class AudioChorus extends BIF {

	/**
	 * Constructor
	 */
	public AudioChorus() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.audio ),
		    new Argument( false, "double", KeyDictionary.rate, 0.8 ),
		    new Argument( false, "double", KeyDictionary.depth, 0.003 ),
		    new Argument( false, "double", KeyDictionary.mix, 0.5 )
		};
	}

	/**
	 * Thicken and widen an audio buffer with a chorus
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer. On stereo input the two sides swing a quarter cycle apart for width.
	 *
	 * @argument.rate How fast the delay swings, in Hz, 0.01 to 20. Defaults to 0.8.
	 *
	 * @argument.depth How far the delay swings, in seconds, 0 to 0.01. Defaults to 0.003.
	 *
	 * @argument.mix Dry/wet balance, 0 is only the input. Defaults to 0.5.
	 *
	 * @return A new audio buffer of the same length.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Effects.chorus(
		    Audios.require( arguments.get( KeyDictionary.audio ), "audio" ),
		    arguments.getAsDouble( KeyDictionary.rate ),
		    arguments.getAsDouble( KeyDictionary.depth ),
		    arguments.getAsDouble( KeyDictionary.mix )
		);
	}

}
