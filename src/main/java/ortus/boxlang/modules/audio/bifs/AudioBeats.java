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

import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "List moments on the beat grid of a timeline, in seconds" )
public class AudioBeats extends BIF {

	/**
	 * Constructor
	 */
	public AudioBeats() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.timeline ),
		    new Argument( false, "double", Key.start, 0.0 ),
		    new Argument( true, "double", Key.end ),
		    new Argument( false, "double", KeyDictionary.step, 1.0 )
		};
	}

	/**
	 * List moments on the beat grid of a timeline, in seconds
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.timeline The timeline from audioTimeline().
	 *
	 * @argument.start The first beat. Defaults to 0.
	 *
	 * @argument.end Where to stop, in beats. The end itself is not included.
	 *
	 * @argument.step Beats between moments, for example 0.5 for eighth notes in 4/4. Defaults to 1.
	 *
	 * @return An array of times in seconds, ready for audioAdd( at= ) or audioPump( times= ).
	 */
	public Array _invoke( IBoxContext context, ArgumentsScope arguments ) {
		var		timeline	= Audios.requireTimeline( arguments.get( KeyDictionary.timeline ) );
		double	start		= arguments.getAsDouble( Key.start );
		double	end			= arguments.getAsDouble( Key.end );
		double	step		= arguments.getAsDouble( KeyDictionary.step );
		if ( step <= 0 || Double.isNaN( step ) || Double.isNaN( start ) || Double.isNaN( end ) || start < 0 ) {
			throw new BoxRuntimeException( "Beats needs a start of zero or more, an end, and a step above zero" );
		}
		if ( ( end - start ) / step > 1_000_000 ) {
			throw new BoxRuntimeException( "That would be more than a million moments, check the end and step" );
		}
		Array	times	= new Array();
		// Count in whole steps so floating point drift cannot add or drop the last moment
		long	count	= ( long ) Math.ceil( ( end - start ) / step - 1e-9 );
		for ( long i = 0; i < count; i++ ) {
			times.add( timeline.beat( start + i * step ) );
		}
		return times;
	}

}
