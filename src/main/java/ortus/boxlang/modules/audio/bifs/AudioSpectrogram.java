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

import ortus.boxlang.modules.audio.analysis.Spectrum;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;

@BoxBIF( description = "Get the band levels of audio over time, for drawing or analysis" )
public class AudioSpectrogram extends BIF {

	private static final Key	audio	= Key.of( "audio" );
	private static final Key	bands	= Key.of( "bands" );
	private static final Key	frames	= Key.of( "frames" );
	private static final Key	minHz	= Key.of( "minHz" );
	private static final Key	maxHz	= Key.of( "maxHz" );

	/**
	 * Constructor
	 */
	public AudioSpectrogram() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( false, "integer", bands, 16 ),
		    new Argument( false, "integer", frames, 64 ),
		    new Argument( false, "double", minHz, 40.0 ),
		    new Argument( false, "double", maxHz, 16000.0 )
		};
	}

	/**
	 * Get the band levels of audio over time, for drawing or analysis
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.bands How many bands. Defaults to 16.
	 *
	 * @argument.frames How many time slices. Defaults to 64.
	 *
	 * @argument.minHz Lower edge of the first band. Defaults to 40.
	 *
	 * @argument.maxHz Upper edge of the last band. Defaults to 16000.
	 *
	 * @return An array of frames, each an array of band levels in dB.
	 */
	public Array _invoke( IBoxContext context, ArgumentsScope arguments ) {
		double[][]	grid	= Spectrum.spectrogram( Audios.require( arguments.get( audio ), "audio" ), arguments.getAsInteger( bands ),
		    arguments.getAsInteger( frames ), arguments.getAsDouble( minHz ), arguments.getAsDouble( maxHz ) );
		Array		result	= new Array();
		for ( double[] row : grid ) {
			result.add( Array.fromArray( Audios.box( row ) ) );
		}
		return result;
	}

}
