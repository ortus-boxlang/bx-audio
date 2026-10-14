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

import ortus.boxlang.modules.audio.analysis.Waveform;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;

@BoxBIF( description = "Summarize audio as a list of peak levels for drawing a waveform" )
public class AudioWaveform extends BIF {

	/**
	 * Constructor
	 */
	public AudioWaveform() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.audio ),
		    new Argument( false, "integer", KeyDictionary.points, 800 )
		};
	}

	/**
	 * Summarize audio as a list of peak levels for drawing a waveform. The audio is split into equal slices and each value is the loudest
	 * absolute sample in its slice across all channels.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.points How many values to return, from 1 to 100000. Defaults to 800.
	 *
	 * @return An array of numbers from 0 (silence) upwards, 1 is full scale.
	 */
	public Array _invoke( IBoxContext context, ArgumentsScope arguments ) {
		double[]	peaks	= Waveform.peaks( Audios.require( arguments.get( KeyDictionary.audio ), "audio" ), arguments.getAsInteger( KeyDictionary.points ) );
		Array		result	= new Array( peaks.length );
		for ( double peak : peaks ) {
			result.add( peak );
		}
		return result;
	}

}
