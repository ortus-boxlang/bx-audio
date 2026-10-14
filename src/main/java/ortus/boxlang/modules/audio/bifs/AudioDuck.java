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

import ortus.boxlang.modules.audio.timeline.Timeline;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Duck a timeline track while another track is loud, like music under a voice-over" )
public class AudioDuck extends BIF {

	/**
	 * Constructor
	 */
	public AudioDuck() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.timeline ),
		    new Argument( true, "string", KeyDictionary.track ),
		    new Argument( true, "string", KeyDictionary.trigger ),
		    new Argument( false, "double", KeyDictionary.amountDb, -12.0 ),
		    new Argument( false, "double", KeyDictionary.thresholdDb, -40.0 ),
		    new Argument( false, "double", KeyDictionary.attack, 0.01 ),
		    new Argument( false, "double", KeyDictionary.release, 0.3 )
		};
	}

	/**
	 * Duck a timeline track while another track is loud, like music under a voice-over
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.timeline The timeline from audioTimeline().
	 *
	 * @argument.track The track to lower, for example the music.
	 *
	 * @argument.trigger The track whose level controls the ducking, for example the voice. It must already have sound on it.
	 *
	 * @argument.amountDb How far to lower the track, a negative number of decibels. Defaults to -12.
	 *
	 * @argument.thresholdDb The trigger level above which ducking starts, relative to full scale. Defaults to -40.
	 *
	 * @argument.attack Seconds taken to lower the track. Defaults to 0.01.
	 *
	 * @argument.release Seconds taken to bring the track back. Defaults to 0.3.
	 *
	 * @return The same timeline, so calls can be chained.
	 */
	public Timeline _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Audios.requireTimeline( arguments.get( KeyDictionary.timeline ) ).duck(
		    arguments.getAsString( KeyDictionary.track ),
		    arguments.getAsString( KeyDictionary.trigger ),
		    arguments.getAsDouble( KeyDictionary.amountDb ),
		    arguments.getAsDouble( KeyDictionary.thresholdDb ),
		    arguments.getAsDouble( KeyDictionary.attack ),
		    arguments.getAsDouble( KeyDictionary.release )
		);
	}

}
