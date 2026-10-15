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
import ortus.boxlang.modules.audio.dsp.Eq;
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
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

@BoxBIF( description = "Shape the tone of audio with parametric equalizer bands" )
public class AudioEq extends BIF {

	private static final Key	audio	= Key.of( "audio" );
	private static final Key	bands	= Key.of( "bands" );

	/**
	 * Constructor
	 */
	public AudioEq() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( true, "array", bands )
		};
	}

	/**
	 * Shape the tone of audio with parametric equalizer bands
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.bands An array of structs, each with type (peak, lowshelf, highshelf, lowpass, highpass or notch), freq in Hz, gain in dB (peak and
	 *                 shelves) and optionally q (width, default 1 for a peak and 0.707 for others).
	 *
	 * @return A new audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Array			list	= arguments.getAsArray( bands );
		List<Eq.Band>	parsed	= new ArrayList<>();
		for ( Object entry : list ) {
			if ( ! ( entry instanceof IStruct band ) ) {
				throw new BoxRuntimeException( "Each EQ band must be a struct such as { type: \"peak\", freq: 3000, gain: 3 }" );
			}
			String	kind	= String.valueOf( band.getOrDefault( Key.of( "type" ), "peak" ) );
			Object	f		= band.get( Key.of( "freq" ) );
			if ( f == null ) {
				throw new BoxRuntimeException( "Each EQ band needs a freq in Hz" );
			}
			Object	g	= band.getOrDefault( Key.of( "gain" ), 0 );
			Object	w	= band.get( Key.of( "q" ) );
			parsed.add( new Eq.Band( kind, ( ( Number ) f ).doubleValue(), ( ( Number ) g ).doubleValue(),
			    w == null ? ( kind.equalsIgnoreCase( "peak" ) ? 1.0 : 0.7071 ) : ( ( Number ) w ).doubleValue() ) );
		}
		return Eq.apply( Audios.require( arguments.get( audio ), "audio" ), parsed );
	}

}
