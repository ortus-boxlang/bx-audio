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

@BoxBIF( description = "Add an echo to an audio buffer" )
public class AudioDelay extends BIF {

	/**
	 * Constructor
	 */
	public AudioDelay() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.audio ),
		    new Argument( false, "double", KeyDictionary.time, 0.35 ),
		    new Argument( false, "double", KeyDictionary.feedback, 0.4 ),
		    new Argument( false, "double", KeyDictionary.mix, 0.35 ),
		    new Argument( false, "boolean", KeyDictionary.pingPong, false ),
		    new Argument( false, "double", KeyDictionary.tail )
		};
	}

	/**
	 * Add an echo to an audio buffer
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.time Seconds between repeats, 0.001 to 5. Use timeline.beat( 0.75 ) for a dotted eighth. Defaults to 0.35.
	 *
	 * @argument.feedback How much of each repeat comes back, 0 for a single echo up to 0.95. Defaults to 0.4.
	 *
	 * @argument.mix Dry/wet balance, 0 is only the input and 1 is only the echoes. Defaults to 0.35.
	 *
	 * @argument.pingPong Bounce the repeats between the left and right side. Needs stereo input. Defaults to false.
	 *
	 * @argument.tail Seconds added at the end for the repeats to ring out. By default they run until 60 dB down, at most 10 seconds.
	 *
	 * @return A new, longer audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Effects.delay(
		    Audios.require( arguments.get( KeyDictionary.audio ), "audio" ),
		    arguments.getAsDouble( KeyDictionary.time ),
		    arguments.getAsDouble( KeyDictionary.feedback ),
		    arguments.getAsDouble( KeyDictionary.mix ),
		    arguments.getAsBoolean( KeyDictionary.pingPong ),
		    arguments.getAsDouble( KeyDictionary.tail )
		);
	}

}
