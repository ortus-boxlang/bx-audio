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

import ortus.boxlang.modules.audio.util.AudioException;
import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Fade an audio buffer in and out" )
public class AudioFade extends BIF {

	/**
	 * Constructor
	 */
	public AudioFade() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.audio ),
		    new Argument( false, "double", KeyDictionary.fadeIn, 0.0 ),
		    new Argument( false, "double", KeyDictionary.fadeOut, 0.0 ),
		    new Argument( false, "string", KeyDictionary.curve, "linear" )
		};
	}

	/**
	 * Fade an audio buffer in and out. The input buffer is left untouched.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.fadeIn Length of the fade in, in seconds. Defaults to 0.
	 *
	 * @argument.fadeOut Length of the fade out, in seconds. Defaults to 0.
	 *
	 * @argument.curve The fade shape: linear (default) or smooth, a raised cosine.
	 *
	 * @return A new audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String curve = arguments.getAsString( KeyDictionary.curve ).toLowerCase();
		if ( !curve.equals( "linear" ) && !curve.equals( "smooth" ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The curve must be linear or smooth but was [" + curve + "]" );
		}
		return Dsp.fade(
		    Audios.require( arguments.get( KeyDictionary.audio ), "audio" ),
		    arguments.getAsDouble( KeyDictionary.fadeIn ),
		    arguments.getAsDouble( KeyDictionary.fadeOut ),
		    curve.equals( "smooth" )
		);
	}

}
