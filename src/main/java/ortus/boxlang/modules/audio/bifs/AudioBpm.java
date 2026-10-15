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

import ortus.boxlang.modules.audio.analysis.Tempo;
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

@BoxBIF( description = "Detect the tempo of audio and where its beats fall" )
public class AudioBpm extends BIF {

	private static final Key	audio	= Key.of( "audio" );
	private static final Key	minBpm	= Key.of( "minBpm" );
	private static final Key	maxBpm	= Key.of( "maxBpm" );

	/**
	 * Constructor
	 */
	public AudioBpm() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( false, "double", minBpm, 60.0 ),
		    new Argument( false, "double", maxBpm, 200.0 )
		};
	}

	/**
	 * Detect the tempo of audio and where its beats fall
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.minBpm Lowest tempo to consider. Defaults to 60.
	 *
	 * @argument.maxBpm Highest tempo to consider. Defaults to 200.
	 *
	 * @return A struct with bpm (0 if there is no clear pulse), confidence (0 to 1) and beats, the beat times in seconds.
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Tempo.Result r = Tempo.detect( Audios.require( arguments.get( audio ), "audio" ), arguments.getAsDouble( minBpm ), arguments.getAsDouble( maxBpm ) );
		return Struct.ofNonConcurrent( "bpm", r.bpm(), "confidence", r.confidence(), "beats", Array.fromArray( Audios.box( r.beats() ) ) );
	}

}
