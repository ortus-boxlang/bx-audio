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

import java.util.Map;
import ortus.boxlang.modules.audio.realtime.Mixer;
import ortus.boxlang.modules.audio.realtime.Playback;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

@BoxBIF( description = "Play audio through the speakers" )
public class AudioPlay extends BIF {

	private static final Key	audio	= Key.of( "audio" );
	private static final Key	wait	= Key.of( "wait" );
	private static final Key	volume	= Key.of( "volume" );
	private static final Key	output	= Key.of( "output" );

	/**
	 * Constructor
	 */
	public AudioPlay() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( false, "boolean", wait, true ),
		    new Argument( false, "double", volume, 1.0 ),
		    new Argument( false, "string", output, "speakers" )
		};
	}

	/**
	 * Play audio through the speakers
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.wait True to return when the sound has finished. False to return at once and let it play on a background mixer. Defaults to true.
	 *
	 * @argument.volume Volume, 1 is the sound as it is. Defaults to 1.
	 *
	 * @argument.output speakers, null (keep time, no sound) or memory is not allowed here. Defaults to speakers.
	 *
	 * @return The playback, which can be stopped with stop().
	 */
	public Playback _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String where = arguments.getAsString( output ).toLowerCase();
		if ( where.equals( "memory" ) ) {
			throw new BoxRuntimeException(
			    "audioPlay needs a mixer that runs: use speakers or null, or audioMixer( output=\"memory\" ) and render() for the audio" );
		}
		AudioBuffer	sound		= Audios.require( arguments.get( audio ), "audio" );
		Mixer		mixer		= new Mixer( sound.getSampleRate() >= 8000 && sound.getSampleRate() <= 192000 ? sound.getSampleRate() : 44100,
		    sound.getChannels() == 1 ? 1 : 2, where );
		Playback	playback	= mixer.play( sound, Map.of( "volume", arguments.getAsDouble( volume ) ) );
		if ( arguments.getAsBoolean( wait ) ) {
			try {
				mixer.waitUntilSilent( sound.getDurationSeconds() + 5 );
			} finally {
				mixer.close();
			}
		} else {
			// Closed by a helper once the sound is done, so a script that does not wait does not leave the sound card open
			Thread closer = new Thread( () -> {
				mixer.waitUntilSilent( sound.getDurationSeconds() + 5 );
				mixer.close();
			}, "bxaudio-play-closer" );
			closer.setDaemon( true );
			closer.start();
		}
		return playback;
	}

}
