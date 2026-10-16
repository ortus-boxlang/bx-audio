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

import ortus.boxlang.modules.audio.dsp.Convolution;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Put a sound in a real space with convolution reverb" )
public class AudioConvolve extends BIF {

	private static final Key impulse = Key.of( "impulse" );

	/**
	 * Constructor
	 */
	public AudioConvolve() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.audio ),
		    new Argument( true, "any", impulse ),
		    new Argument( false, "double", KeyDictionary.mix, 0.3 )
		};
	}

	/**
	 * Plays a sound through the recorded echo of a real space: a church, a stairwell, a spring or a plate. The impulse response is a recording of a click
	 * or
	 * a sweep in that space, and the result sounds as if the sound was made there. Unlike audioReverb(), which builds a room from delay lines, this gives
	 * the sound of a particular place.
	 *
	 * <p>
	 * The impulse response can be any length up to 20 seconds and any sample rate (it is resampled). A mono sound with a stereo impulse becomes stereo.
	 * The response is scaled to unit energy, so level of the wet part is about the level of the input for noisy material.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio A mono or stereo audio buffer.
	 *
	 * @argument.impulse The impulse response: an audio buffer from audioLoad(), mono or stereo.
	 *
	 * @argument.mix Dry/wet balance, 0 is only the input and 1 is only the room. Defaults to 0.3.
	 *
	 * @return A new audio buffer as long as the sound plus the impulse response.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Convolution.convolve( Audios.require( arguments.get( KeyDictionary.audio ), "audio" ), Audios.require( arguments.get( impulse ), "impulse" ),
		    arguments.getAsDouble( KeyDictionary.mix ) );
	}

}
