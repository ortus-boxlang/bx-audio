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

import ortus.boxlang.modules.audio.dsp.Dynamics;
import ortus.boxlang.modules.audio.dsp.Mastering;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Master audio for delivery: reach a loudness target with a true-peak ceiling, optionally gluing the mix with light compression" )
public class AudioMaster extends BIF {

	private static final Key	audio		= Key.of( "audio" );
	private static final Key	target		= Key.of( "target" );
	private static final Key	truePeakDb	= Key.of( "truePeakDb" );
	private static final Key	glue		= Key.of( "glue" );

	/**
	 * Constructor
	 */
	public AudioMaster() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( false, "any", target, "streaming" ),
		    new Argument( false, "double", truePeakDb, -1.0 ),
		    new Argument( false, "boolean", glue, false )
		};
	}

	/**
	 * Master audio for delivery: reach a loudness target with a true-peak ceiling, optionally gluing the mix with light compression
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.target A preset name or a number in LUFS. streaming is -14, podcast -16, game -16, broadcast -23 (EBU R128), cinema -24. Defaults to
	 *                  streaming.
	 *
	 * @argument.truePeakDb The highest peak in dBFS. Defaults to -1.
	 *
	 * @argument.glue True to compress lightly (2:1, slow) before normalizing so the mix sounds more even. Defaults to false.
	 *
	 * @return A new audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		AudioBuffer input = Audios.require( arguments.get( audio ), "audio" );
		if ( arguments.getAsBoolean( glue ) ) {
			input = Dynamics.compress( input, -20, 2, 30, 200, 6, 0 );
		}
		return Mastering.toLoudness( input, Mastering.target( arguments.get( target ) ), arguments.getAsDouble( truePeakDb ) );
	}

}
