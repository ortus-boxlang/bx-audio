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

import java.util.ArrayList;
import java.util.List;

import ortus.boxlang.modules.audio.timeline.Timeline;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.dynamic.casters.DoubleCaster;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Duck a timeline track at given moments, like a kick drum sidechain" )
public class AudioPump extends BIF {

	/**
	 * Constructor
	 */
	public AudioPump() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.timeline ),
		    new Argument( true, "string", KeyDictionary.track ),
		    new Argument( true, "array", KeyDictionary.times ),
		    new Argument( false, "double", KeyDictionary.amountDb, -5.0 ),
		    new Argument( false, "double", KeyDictionary.recovery, 0.18 )
		};
	}

	/**
	 * Duck a timeline track at given moments, like a kick drum sidechain
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.timeline The timeline from audioTimeline().
	 *
	 * @argument.track The track to duck.
	 *
	 * @argument.times An array of start times in seconds, for example from audioBeats().
	 *
	 * @argument.amountDb How far the gain drops at each time, a negative number of decibels. Defaults to -5.
	 *
	 * @argument.recovery Seconds the gain takes to return to normal. Defaults to 0.18.
	 *
	 * @return The same timeline, so calls can be chained.
	 */
	public Timeline _invoke( IBoxContext context, ArgumentsScope arguments ) {
		List<Double> times = new ArrayList<>();
		for ( Object item : arguments.getAsArray( KeyDictionary.times ) ) {
			times.add( DoubleCaster.cast( item ) );
		}
		return Audios.requireTimeline( arguments.get( KeyDictionary.timeline ) ).pump(
		    arguments.getAsString( KeyDictionary.track ),
		    times,
		    arguments.getAsDouble( KeyDictionary.amountDb ),
		    arguments.getAsDouble( KeyDictionary.recovery )
		);
	}

}
