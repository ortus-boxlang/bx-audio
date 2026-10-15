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

import java.util.HashMap;
import java.util.Map;

import ortus.boxlang.modules.audio.tts.Narration;
import ortus.boxlang.modules.audio.tts.Speech;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.tts.EngineInstaller;

@BoxBIF( description = "Turn text into speech with an installed Piper voice, installing the engine and the voice on first use" )
public class AudioSpeak extends BIF {

	/**
	 * Constructor
	 */
	public AudioSpeak() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "string", KeyDictionary.text ),
		    new Argument( true, "string", KeyDictionary.voice ),
		    new Argument( false, "boolean", KeyDictionary.autoInstall ),
		    new Argument( false, "function", KeyDictionary.onProgress ),
		    new Argument( false, "double", Key.of( "speed" ), 1.0 ),
		    new Argument( false, "struct", Key.of( "pauses" ) )
		};
	}

	/**
	 * Turn text into speech with an installed Piper voice, installing the engine and the voice on first use
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.text What to say.
	 *
	 * @argument.voice The voice: an id such as en_US-joe-medium, or its short name such as joe. See audioVoices().
	 *
	 * @argument.autoInstall May the engine and the voice be downloaded when they are missing. Defaults to the module setting autoInstall, which is true.
	 *                       The first call downloads about 150 MB for the engine and 60 MB or more for the voice.
	 *
	 * @argument.onProgress A function called as ( name, bytesDone, bytesTotal ) while something downloads.
	 *
	 * @argument.speed How fast to speak: 1 is the voice's own pace, 1.25 faster, 0.8 slower. Between 0.25 and 4. Defaults to 1.
	 *
	 * @argument.pauses A struct with any of sentence, clause and paragraph, in seconds, to speak the text piece by piece with those pauses
	 *                  between the pieces (defaults 0.35, 0.15 and 0.8). Leave it out to let the voice phrase the whole text itself.
	 *
	 * @return A mono audio buffer at the voice sample rate, normally 22050 Hz.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		boolean	allow	= arguments.containsKey( KeyDictionary.autoInstall ) && arguments.get( KeyDictionary.autoInstall ) != null
		    ? arguments.getAsBoolean( KeyDictionary.autoInstall )
		    : Speech.autoInstall();
		double	speed	= arguments.getAsDouble( Key.of( "speed" ) );
		if ( arguments.get( Key.of( "pauses" ) ) instanceof IStruct pauses ) {
			Map<String, Object> values = new HashMap<>();
			pauses.forEach( ( k, v ) -> values.put( k.getName().toLowerCase(), v ) );
			return Speech
			    .narrate( arguments.getAsString( KeyDictionary.text ), arguments.getAsString( KeyDictionary.voice ), allow, progress( context, arguments ),
			        speed, Narration.Pauses.from( values ), false, 80 )
			    .audio();
		}
		return Speech.speak( arguments.getAsString( KeyDictionary.text ), arguments.getAsString( KeyDictionary.voice ), allow,
		    progress( context, arguments ), speed );
	}

	static EngineInstaller.NamedProgress progress( IBoxContext context, ArgumentsScope arguments ) {
		Object callback = arguments.get( KeyDictionary.onProgress );
		if ( callback == null ) {
			return null;
		}
		// Call the BoxLang function at most five times a second, and always for the last chunk of a file
		long[] last = { 0 };
		return ( name, done, total ) -> {
			long now = System.nanoTime();
			if ( done >= total || now - last[ 0 ] > 200_000_000L ) {
				last[ 0 ] = now;
				context.invokeFunction( callback, new Object[] { name, done, total } );
			}
		};
	}

}
