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
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

@BoxBIF( description = "Get the frequency spectrum of one window of audio" )
public class AudioFft extends BIF {

	private static final Key	audio	= Key.of( "audio" );
	private static final Key	size	= Key.of( "size" );
	private static final Key	start	= Key.of( "start" );

	/**
	 * Constructor
	 */
	public AudioFft() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( false, "integer", size, 2048 ),
		    new Argument( false, "double", start, 0.0 )
		};
	}

	/**
	 * Get the frequency spectrum of one window of audio
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.size FFT size, a power of two. Defaults to 2048.
	 *
	 * @argument.start Where the window starts, in seconds. Defaults to 0.
	 *
	 * @return A struct with frequencies (Hz) and magnitudes (1 for a full scale sine), arrays of size/2+1 values.
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Spectrum.Bins bins = Spectrum.fft( Audios.require( arguments.get( audio ), "audio" ), arguments.getAsInteger( size ), arguments.getAsDouble( start ) );
		return Struct.ofNonConcurrent( "frequencies", Array.fromArray( Audios.box( bins.frequencies() ) ), "magnitudes",
		    Array.fromArray( Audios.box( bins.magnitudes() ) ) );
	}

}
