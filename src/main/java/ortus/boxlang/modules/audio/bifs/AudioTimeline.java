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
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Create a multi track timeline for mixing sounds" )
public class AudioTimeline extends BIF {

	/**
	 * Constructor
	 */
	public AudioTimeline() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "double", KeyDictionary.bpm, 120.0 ),
		    new Argument( false, "integer", KeyDictionary.beatsPerBar, 4 ),
		    new Argument( false, "integer", KeyDictionary.sampleRate, 44100 ),
		    new Argument( false, "integer", KeyDictionary.channels, 2 )
		};
	}

	/**
	 * Create a multi track timeline for mixing sounds
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.bpm The tempo in beats per minute. Defaults to 120.
	 *
	 * @argument.beatsPerBar Beats in one bar. Defaults to 4.
	 *
	 * @argument.sampleRate The sample rate of the mix in Hz. Defaults to 44100.
	 *
	 * @argument.channels 1 for mono or 2 for stereo. Defaults to 2.
	 *
	 * @return The timeline. Use timeline.beat( n ) and timeline.bar( n ) to turn beats and bars into seconds.
	 */
	public Timeline _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return new Timeline(
		    arguments.getAsDouble( KeyDictionary.bpm ),
		    arguments.getAsInteger( KeyDictionary.beatsPerBar ),
		    arguments.getAsInteger( KeyDictionary.sampleRate ),
		    arguments.getAsInteger( KeyDictionary.channels )
		);
	}

}
