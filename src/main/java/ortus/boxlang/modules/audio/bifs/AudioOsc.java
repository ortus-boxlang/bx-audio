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

import ortus.boxlang.modules.audio.synth.Synth;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.dynamic.casters.DoubleCaster;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Generate a tone with an oscillator" )
public class AudioOsc extends BIF {

	/**
	 * Constructor
	 */
	public AudioOsc() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "string", KeyDictionary.shape ),
		    new Argument( true, "double", KeyDictionary.freq ),
		    new Argument( true, "double", Key.seconds ),
		    new Argument( false, "integer", KeyDictionary.sampleRate, 44100 ),
		    new Argument( false, "integer", KeyDictionary.harmonics, 16 ),
		    new Argument( false, "array", KeyDictionary.partials ),
		    new Argument( false, "double", KeyDictionary.width, 0.5 ),
		    new Argument( false, "double", KeyDictionary.phase, 0.0 )
		};
	}

	/**
	 * Generate a tone with an oscillator
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.shape sine, saw, square, triangle, pulse or additive. Saw, square and triangle are band limited so they do not alias. Pulse is the naive
	 *                 digital kind used by chiptune hardware.
	 *
	 * @argument.freq The frequency in Hz, see audioFreq() for note names.
	 *
	 * @argument.seconds The length in seconds.
	 *
	 * @argument.sampleRate The sample rate in Hz. Defaults to 44100.
	 *
	 * @argument.harmonics For saw, square and triangle: the most harmonics to add, fewer are used near the Nyquist frequency. Fewer harmonics sound
	 *                     duller. Defaults to 16.
	 *
	 * @argument.partials For additive: an array of [ frequency multiple, amplitude ] pairs, for example [ [ 1, 1.0 ], [ 2, 0.5 ] ]. Amplitudes are used
	 *                    as given.
	 *
	 * @argument.width For pulse: the fraction of the cycle spent high, 0.5 is a square. NES style duties are 0.125, 0.25, 0.5 and 0.75. Defaults to 0.5.
	 *
	 * @argument.phase Where in the cycle to start, 0 to 1. Defaults to 0.
	 *
	 * @return A mono audio buffer with a peak of about 1.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		double[][]	partials	= null;
		Array		raw			= arguments.getAsArray( KeyDictionary.partials );
		if ( raw != null ) {
			partials = new double[ raw.size() ][];
			for ( int i = 0; i < partials.length; i++ ) {
				if ( ! ( raw.get( i ) instanceof Array pair ) || pair.size() != 2 ) {
					throw new BoxRuntimeException( "Each partial must be a pair of [ frequency multiple, amplitude ]" );
				}
				partials[ i ] = new double[] { DoubleCaster.cast( pair.get( 0 ) ), DoubleCaster.cast( pair.get( 1 ) ) };
			}
		}
		return Synth.oscillator(
		    arguments.getAsString( KeyDictionary.shape ),
		    arguments.getAsDouble( KeyDictionary.freq ),
		    arguments.getAsDouble( Key.seconds ),
		    arguments.getAsInteger( KeyDictionary.sampleRate ),
		    arguments.getAsInteger( KeyDictionary.harmonics ),
		    partials,
		    arguments.getAsDouble( KeyDictionary.width ),
		    arguments.getAsDouble( KeyDictionary.phase )
		);
	}

}
