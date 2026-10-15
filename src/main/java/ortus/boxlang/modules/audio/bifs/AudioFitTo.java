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

import ortus.boxlang.modules.audio.dsp.Stretch;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Change the length of audio to a number of seconds without changing its pitch" )
public class AudioFitTo extends BIF {

	private static final Key	audio	= Key.of( "audio" );
	private static final Key	seconds	= Key.of( "seconds" );

	/**
	 * Constructor
	 */
	public AudioFitTo() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( true, "double", seconds )
		};
	}

	/**
	 * Change the length of audio to a number of seconds without changing its pitch
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.seconds The length to reach, in seconds. The change in speed should stay within about 25 percent for natural results.
	 *
	 * @return A new audio buffer of that length.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Stretch.fitTo( Audios.require( arguments.get( audio ), "audio" ), arguments.getAsDouble( seconds ) );
	}

}
