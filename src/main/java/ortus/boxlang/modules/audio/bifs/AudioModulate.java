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
import ortus.boxlang.modules.audio.dsp.Modulation;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Make the volume, pan, filter cutoff or pitch of a sound follow a curve or an LFO" )
public class AudioModulate extends BIF {

	private static final Key	audio	= Key.of( "audio" );
	private static final Key	param	= Key.of( "param" );
	private static final Key	curve	= Key.of( "curve" );
	private static final Key	filter	= Key.of( "filter" );
	private static final Key	q		= Key.of( "q" );

	/**
	 * Constructor
	 */
	public AudioModulate() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( true, "string", param ),
		    new Argument( true, "any", curve ),
		    new Argument( false, "string", filter, "lowpass" ),
		    new Argument( false, "double", q, 0.7071 )
		};
	}

	/**
	 * Changes a sound while it plays. One parameter of the sound follows a curve from audioLfo() or audioCurve(), at the time of the sound (the curve at
	 * 2
	 * seconds is what the sound does 2 seconds in).
	 *
	 * <ul>
	 * <li><b>gainDb</b>: the curve is the gain in dB. An LFO centered on -6 with a depth of 6 is tremolo, a line from -60 to 0 is a fade in.</li>
	 * <li><b>pan</b>: the curve is the position, -1 left to 1 right. An LFO is auto pan. A mono sound becomes stereo.</li>
	 * <li><b>cutoff</b>: the curve is the cutoff of a filter in Hz, a wah or a filter sweep. Choose the filter and its resonance.</li>
	 * <li><b>pitch</b>: the curve is the pitch in semitones, like a tape or a record, so a curve that swings around 0 is vibrato. A curve that is above 0
	 * for a
	 * while makes the sound shorter, and below 0 longer.</li>
	 * </ul>
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.param gainDb, pan, cutoff or pitch.
	 *
	 * @argument.curve A curve from audioCurve() or an LFO from audioLfo().
	 *
	 * @argument.filter For cutoff: lowpass, highpass, bandpass or notch. Defaults to lowpass.
	 *
	 * @argument.q For cutoff: the resonance. 0.7071 is flat, higher peaks at the cutoff. Defaults to 0.7071.
	 *
	 * @return A new audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		if ( ! ( arguments.get( curve ) instanceof Curve c ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The curve argument must be a curve from audioCurve() or an LFO from audioLfo()" );
		}
		return Modulation.apply( Audios.require( arguments.get( audio ), "audio" ), arguments.getAsString( param ), c, arguments.getAsString( filter ),
		    arguments.getAsDouble( q ) );
	}

}
