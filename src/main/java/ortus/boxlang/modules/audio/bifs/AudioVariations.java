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

import java.util.Random;
import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.dsp.Stretch;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.modules.audio.util.AudioException;

@BoxBIF( description = "Make several slightly different copies of a sound, so repeated effects do not sound the same" )
public class AudioVariations extends BIF {

	private static final Key	audio		= Key.of( "audio" );
	private static final Key	count		= Key.of( "count" );
	private static final Key	pitchRange	= Key.of( "pitchRange" );
	private static final Key	gainRangeDb	= Key.of( "gainRangeDb" );
	private static final Key	seed		= Key.of( "seed" );

	/**
	 * Constructor
	 */
	public AudioVariations() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( false, "integer", count, 5 ),
		    new Argument( false, "double", pitchRange, 1.0 ),
		    new Argument( false, "double", gainRangeDb, 2.0 ),
		    new Argument( false, "long", seed, 1 )
		};
	}

	/**
	 * Make several slightly different copies of a sound, so repeated effects do not sound the same
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.count How many variations. Defaults to 5.
	 *
	 * @argument.pitchRange Random change of pitch, plus or minus this many semitones; the speed changes with it, like a tape. Defaults to 1.
	 *
	 * @argument.gainRangeDb Random change of level, plus or minus this many dB. Defaults to 2.
	 *
	 * @argument.seed Seed of the random numbers, so the same call gives the same variations. Defaults to 1.
	 *
	 * @return An array of audio buffers.
	 */
	public Array _invoke( IBoxContext context, ArgumentsScope arguments ) {
		if ( arguments.getAsInteger( count ) < 1 || arguments.getAsInteger( count ) > 256 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "count must be between 1 and 256 but was " + arguments.getAsInteger( count ) );
		}
		AudioBuffer	source	= Audios.require( arguments.get( audio ), "audio" );
		Random		random	= new Random( arguments.getAsLong( seed ) );
		Array		result	= new Array();
		for ( int i = 0; i < arguments.getAsInteger( count ); i++ ) {
			double	semitones	= ( random.nextDouble() * 2 - 1 ) * arguments.getAsDouble( pitchRange );
			double	db			= ( random.nextDouble() * 2 - 1 ) * arguments.getAsDouble( gainRangeDb );
			result.add( Dsp.gain( Stretch.varispeed( source, Math.pow( 2, semitones / 12 ) ), db ) );
		}
		return result;
	}

}
