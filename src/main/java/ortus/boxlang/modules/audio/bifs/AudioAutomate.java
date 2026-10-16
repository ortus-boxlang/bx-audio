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

import ortus.boxlang.modules.audio.curve.Curve;
import ortus.boxlang.modules.audio.timeline.Timeline;
import ortus.boxlang.modules.audio.util.AudioException;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Make the volume or the balance of a timeline track follow a curve or an LFO" )
public class AudioAutomate extends BIF {

	private static final Key	param	= Key.of( "param" );
	private static final Key	curve	= Key.of( "curve" );

	/**
	 * Constructor
	 */
	public AudioAutomate() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.timeline ),
		    new Argument( true, "string", KeyDictionary.track ),
		    new Argument( true, "string", param ),
		    new Argument( true, "any", curve )
		};
	}

	/**
	 * Moves a track's volume or balance over the time of the timeline, as the fader of a mixing desk is moved during a mix: a swell, a fade out, a sound
	 * that
	 * travels from left to right. The movement is applied when the timeline is rendered, on top of the track gain and the ducks. A second call for the
	 * same
	 * parameter of the same track replaces the first.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.timeline The timeline from audioTimeline().
	 *
	 * @argument.track The track. It is made when it does not exist yet.
	 *
	 * @argument.param gainDb (the curve is the gain in dB) or pan (the curve is the position, -1 left to 1 right, applied as a balance control on a
	 *                 stereo
	 *                 timeline).
	 *
	 * @argument.curve A curve from audioCurve() or an LFO from audioLfo(), in seconds of the timeline.
	 *
	 * @return The same timeline, so calls can be chained.
	 */
	public Timeline _invoke( IBoxContext context, ArgumentsScope arguments ) {
		if ( ! ( arguments.get( curve ) instanceof Curve c ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The curve argument must be a curve from audioCurve() or an LFO from audioLfo()" );
		}
		return Audios.requireTimeline( arguments.get( KeyDictionary.timeline ) ).automate( arguments.getAsString( KeyDictionary.track ),
		    arguments.getAsString( param ), c );
	}

}
