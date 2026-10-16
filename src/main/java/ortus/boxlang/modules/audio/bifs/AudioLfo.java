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
import ortus.boxlang.modules.audio.curve.Curves;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Make a low frequency oscillator, a value that swings up and down over time" )
public class AudioLfo extends BIF {

	private static final Key	shape	= Key.of( "shape" );
	private static final Key	rate	= Key.of( "rate" );
	private static final Key	depth	= Key.of( "depth" );
	private static final Key	center	= Key.of( "center" );
	private static final Key	phase	= Key.of( "phase" );
	private static final Key	fadeIn	= Key.of( "fadeIn" );
	private static final Key	seed	= Key.of( "seed" );

	/**
	 * Constructor
	 */
	public AudioLfo() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "string", shape, "sine" ),
		    new Argument( false, "double", rate, 1.0 ),
		    new Argument( false, "double", depth, 1.0 ),
		    new Argument( false, "double", center, 0.0 ),
		    new Argument( false, "double", phase, 0.0 ),
		    new Argument( false, "double", fadeIn, 0.0 ),
		    new Argument( false, "long", seed, 0L )
		};
	}

	/**
	 * Makes a low frequency oscillator: a value that swings between center - depth and center + depth, a number of times a second. On its own it is only
	 * a
	 * curve; give it to audioModulate() to make tremolo (the volume swings), auto pan, a wah (the filter cutoff swings) or vibrato (the pitch swings), or
	 * to
	 * audioAutomate() to move a track's volume or balance. audioCurveAt() reads its value at a time.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.shape sine, triangle, saw (a ramp up that jumps back), square or random (a new random value each cycle, held). Every shape starts at the
	 *                 center,
	 *                 and a square starts high. Defaults to sine.
	 *
	 * @argument.rate Cycles per second, above 0 and at most 1000. Defaults to 1.
	 *
	 * @argument.depth How far it swings from the center, in the unit of what it controls: dB for gainDb, -1 to 1 for pan, Hz for cutoff, semitones for
	 *                 pitch.
	 *                 Defaults to 1.
	 *
	 * @argument.center The value it swings around. Defaults to 0.
	 *
	 * @argument.phase Where in the cycle it starts, 0 to 1. Defaults to 0.
	 *
	 * @argument.fadeIn Seconds for the swing to grow from nothing to its full depth, as a player brings in vibrato. Defaults to 0, no fade.
	 *
	 * @argument.seed For the random shape: the same seed gives the same values. Defaults to 0.
	 *
	 * @return A curve.
	 */
	public Curve _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Curves.lfo( arguments.getAsString( shape ), arguments.getAsDouble( rate ), arguments.getAsDouble( depth ), arguments.getAsDouble( center ),
		    arguments.getAsDouble( phase ), arguments.getAsDouble( fadeIn ), arguments.getAsLong( seed ) );
	}

}
