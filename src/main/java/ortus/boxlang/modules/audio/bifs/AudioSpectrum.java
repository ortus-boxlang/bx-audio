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

@BoxBIF( description = "Get the level of audio in log spaced frequency bands, in dB" )
public class AudioSpectrum extends BIF {

	private static final Key	audio	= Key.of( "audio" );
	private static final Key	bands	= Key.of( "bands" );
	private static final Key	minHz	= Key.of( "minHz" );
	private static final Key	maxHz	= Key.of( "maxHz" );
	private static final Key	start	= Key.of( "start" );
	private static final Key	seconds	= Key.of( "seconds" );

	/**
	 * Constructor
	 */
	public AudioSpectrum() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( false, "integer", bands, 16 ),
		    new Argument( false, "double", minHz, 40.0 ),
		    new Argument( false, "double", maxHz, 16000.0 ),
		    new Argument( false, "double", start, 0.0 ),
		    new Argument( false, "double", seconds, 0.0 )
		};
	}

	/**
	 * Get the level of audio in log spaced frequency bands, in dB
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.bands How many bands. Defaults to 16.
	 *
	 * @argument.minHz Lower edge of the first band. Defaults to 40.
	 *
	 * @argument.maxHz Upper edge of the last band, capped at half the sample rate. Defaults to 16000.
	 *
	 * @argument.start Start in seconds. Defaults to 0.
	 *
	 * @argument.seconds Length to analyse in seconds, 0 for the rest. Defaults to 0.
	 *
	 * @return An array of band levels in dB, lowest frequency first. -120 means nothing.
	 */
	public Array _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Array.fromArray( Audios.box( Spectrum.bands( Audios.require( arguments.get( audio ), "audio" ), arguments.getAsInteger( bands ),
		    arguments.getAsDouble( minHz ), arguments.getAsDouble( maxHz ), arguments.getAsDouble( start ), arguments.getAsDouble( seconds ) ) ) );
	}

}
