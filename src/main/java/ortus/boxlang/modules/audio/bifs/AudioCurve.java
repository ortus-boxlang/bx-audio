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

import ortus.boxlang.modules.audio.curve.Curve;
import ortus.boxlang.modules.audio.curve.Curves;
import ortus.boxlang.modules.audio.util.AudioException;
import ortus.boxlang.modules.audio.util.Numbers;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;

@BoxBIF( description = "Make a curve from points: a value that moves from point to point over time" )
public class AudioCurve extends BIF {

	private static final Key	points			= Key.of( "points" );
	private static final Key	interpolation	= Key.of( "interpolation" );

	/**
	 * Constructor
	 */
	public AudioCurve() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "array", points ),
		    new Argument( false, "string", interpolation, "linear" )
		};
	}

	/**
	 * Makes a value that moves from point to point over time: a fade of any shape, a filter sweep, a glide. Before the first point the curve has the
	 * first
	 * value and after the last it keeps the last. Give it to audioModulate() to change a sound, or to audioAutomate() to change a timeline track.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.points An array of structs with at (seconds, 0 or more; time works too) and value, in any order, at different times. The unit of the
	 *                  value is
	 *                  that of what it controls: dB for gainDb, -1 to 1 for pan, Hz for cutoff, semitones for pitch.
	 *
	 * @argument.interpolation How it moves between points: linear (straight lines), smooth (an eased curve) or step (holds each value until the next
	 *                         point).
	 *                         Defaults to linear.
	 *
	 * @return A curve.
	 */
	public Curve _invoke( IBoxContext context, ArgumentsScope arguments ) {
		List<Curves.Point> list = new ArrayList<>();
		for ( Object entry : arguments.getAsArray( points ) ) {
			if ( ! ( entry instanceof IStruct s ) ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT, "Each point must be a struct such as { at: 2, value: -6 }" );
			}
			Object	at		= s.containsKey( Key.of( "at" ) ) ? s.get( Key.of( "at" ) ) : s.get( Key.of( "time" ) );
			Object	value	= s.get( Key.of( "value" ) );
			if ( at == null || value == null ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT, "Each point needs an at (seconds) and a value, such as { at: 2, value: -6 }" );
			}
			list.add( new Curves.Point( Numbers.toDouble( at, "the time of a point" ), Numbers.toDouble( value, "the value of a point" ) ) );
		}
		return Curves.points( list, arguments.getAsString( interpolation ) );
	}

}
