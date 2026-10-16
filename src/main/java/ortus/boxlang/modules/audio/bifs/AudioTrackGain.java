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

@BoxBIF( description = "Set the volume of a whole timeline track" )
public class AudioTrackGain extends BIF {

	/**
	 * Constructor
	 */
	public AudioTrackGain() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.timeline ),
		    new Argument( true, "string", KeyDictionary.track ),
		    new Argument( true, "double", KeyDictionary.gainDb )
		};
	}

	/**
	 * Set the volume of a whole timeline track
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.timeline The timeline from audioTimeline().
	 *
	 * @argument.track The track name, created if it does not exist yet.
	 *
	 * @argument.gainDb The track gain in decibels, applied when the timeline is rendered.
	 *
	 * @return The same timeline, so calls can be chained.
	 */
	public Timeline _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Audios.requireTimeline( arguments.get( KeyDictionary.timeline ) ).setTrackGain(
		    arguments.getAsString( KeyDictionary.track ),
		    arguments.getAsDouble( KeyDictionary.gainDb )
		);
	}

}
