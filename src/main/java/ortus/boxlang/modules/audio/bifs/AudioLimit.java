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
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Stop audio from going above a ceiling with a look-ahead limiter" )
public class AudioLimit extends BIF {

	private static final Key	audio		= Key.of( "audio" );
	private static final Key	ceilingDb	= Key.of( "ceilingDb" );
	private static final Key	release		= Key.of( "release" );
	private static final Key	lookAhead	= Key.of( "lookAhead" );

	/**
	 * Constructor
	 */
	public AudioLimit() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( false, "double", ceilingDb, -1.0 ),
		    new Argument( false, "double", release, 100.0 ),
		    new Argument( false, "double", lookAhead, 5.0 )
		};
	}

	/**
	 * Stop audio from going above a ceiling with a look-ahead limiter
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.ceilingDb The highest sample level in dB, at most 0. Defaults to -1.
	 *
	 * @argument.release Milliseconds to recover after a peak. Defaults to 100.
	 *
	 * @argument.lookAhead Milliseconds the limiter looks ahead. Defaults to 5.
	 *
	 * @return A new audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Dynamics.limit( Audios.require( arguments.get( audio ), "audio" ), arguments.getAsDouble( ceilingDb ), arguments.getAsDouble( release ),
		    arguments.getAsDouble( lookAhead ) );
	}

}
