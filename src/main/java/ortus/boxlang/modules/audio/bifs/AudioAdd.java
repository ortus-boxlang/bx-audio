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

@BoxBIF( description = "Place a sound on a timeline track" )
public class AudioAdd extends BIF {

	/**
	 * Constructor
	 */
	public AudioAdd() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.timeline ),
		    new Argument( true, "any", KeyDictionary.sound ),
		    new Argument( false, "double", KeyDictionary.at, 0.0 ),
		    new Argument( false, "string", KeyDictionary.track, Timeline.DEFAULT_TRACK ),
		    new Argument( false, "double", KeyDictionary.gainDb, 0.0 ),
		    new Argument( false, "double", KeyDictionary.pan, 0.0 )
		};
	}

	/**
	 * Place a sound on a timeline track
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.timeline The timeline from audioTimeline().
	 *
	 * @argument.sound The audio buffer to place. A different sample rate is converted automatically, mono is panned in a stereo timeline.
	 *
	 * @argument.at Where the sound starts, in seconds. Use timeline.beat( n ) or timeline.bar( n ) to place it on the grid. Defaults to 0.
	 *
	 * @argument.track The track name, created on first use. Defaults to main.
	 *
	 * @argument.gainDb Gain in decibels. Defaults to 0.
	 *
	 * @argument.pan From -1 (left) to 1 (right). Defaults to 0.
	 *
	 * @return The same timeline, so calls can be chained.
	 */
	public Timeline _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Audios.requireTimeline( arguments.get( KeyDictionary.timeline ) ).add(
		    Audios.require( arguments.get( KeyDictionary.sound ), "sound" ),
		    arguments.getAsDouble( KeyDictionary.at ),
		    arguments.getAsString( KeyDictionary.track ),
		    arguments.getAsDouble( KeyDictionary.gainDb ),
		    arguments.getAsDouble( KeyDictionary.pan )
		);
	}

}
