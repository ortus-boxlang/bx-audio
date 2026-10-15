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

import ortus.boxlang.modules.audio.analysis.Silence;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Struct;

@BoxBIF( description = "Find the silent stretches of audio" )
public class AudioDetectSilence extends BIF {

	private static final Key	audio		= Key.of( "audio" );
	private static final Key	thresholdDb	= Key.of( "thresholdDb" );
	private static final Key	minSeconds	= Key.of( "minSeconds" );

	/**
	 * Constructor
	 */
	public AudioDetectSilence() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( false, "double", thresholdDb, -50.0 ),
		    new Argument( false, "double", minSeconds, 0.3 )
		};
	}

	/**
	 * Find the silent stretches of audio
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.thresholdDb Level below which audio counts as silent, in dB. Defaults to -50.
	 *
	 * @argument.minSeconds The shortest silence to report. Defaults to 0.3.
	 *
	 * @return An array of structs with start, end and duration in seconds.
	 */
	public Array _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Array result = new Array();
		for ( Silence.Range range : Silence.detect( Audios.require( arguments.get( audio ), "audio" ), arguments.getAsDouble( thresholdDb ),
		    arguments.getAsDouble( minSeconds ) ) ) {
			result.add( Struct.ofNonConcurrent( "start", range.start(), "end", range.end(), "duration", range.duration() ) );
		}
		return result;
	}

}
