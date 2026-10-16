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
import ortus.boxlang.modules.audio.util.AudioException;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Read the value of a curve or an LFO at a time" )
public class AudioCurveAt extends BIF {

	private static final Key curve = Key.of( "curve" );

	/**
	 * Constructor
	 */
	public AudioCurveAt() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", curve ),
		    new Argument( true, "double", Key.seconds )
		};
	}

	/**
	 * Reads the value of a curve from audioCurve() or an LFO from audioLfo() at a time. It is how a script finds out what a modulation will do, to draw
	 * it,
	 * to check it, or to use the same movement for something else.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.curve The curve or LFO.
	 *
	 * @argument.seconds The time, 0 or more.
	 *
	 * @return The value at that time.
	 */
	public Double _invoke( IBoxContext context, ArgumentsScope arguments ) {
		if ( ! ( arguments.get( curve ) instanceof Curve c ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The curve argument must be a curve from audioCurve() or an LFO from audioLfo()" );
		}
		double seconds = arguments.getAsDouble( Key.seconds );
		if ( ! ( seconds >= 0 ) || Double.isInfinite( seconds ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The seconds must be zero or more but was " + seconds );
		}
		return c.at( seconds );
	}

}
