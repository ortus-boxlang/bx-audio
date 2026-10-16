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
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

@BoxBIF( description = "Speak a text piece by piece and return the audio with the time each caption line is spoken" )
public class AudioSpeakCues extends BIF {

	private static final Key	text		= Key.of( "text" );
	private static final Key	voice		= Key.of( "voice" );
	private static final Key	speed		= Key.of( "speed" );
	private static final Key	pauses		= Key.of( "pauses" );
	private static final Key	maxChars	= Key.of( "maxChars" );
	private static final Key	split		= Key.of( "split" );
	private static final Key	autoInstall	= Key.of( "autoInstall" );
	private static final Key	onProgress	= Key.of( "onProgress" );

	/**
	 * Constructor
	 */
	public AudioSpeakCues() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "string", text ),
		    new Argument( true, "string", voice ),
		    new Argument( false, "double", speed, 1.0 ),
		    new Argument( false, "struct", pauses ),
		    new Argument( false, "integer", maxChars, 80 ),
		    new Argument( false, "string", split, "sentence" ),
		    new Argument( false, "boolean", autoInstall ),
		    new Argument( false, "function", onProgress ),
		    new Argument( false, "struct", Key.of( "pronunciations" ) )
		};
	}

	/**
	 * Speak a text piece by piece and return the audio with the time each caption line is spoken
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.text What to say. Blank lines separate paragraphs.
	 *
	 * @argument.voice The voice: an id such as en_US-joe-medium, or its short name. See audioVoices().
	 *
	 * @argument.speed 1 is the voice's own pace. Between 0.25 and 4. Defaults to 1.
	 *
	 * @argument.pauses A struct with any of sentence, clause and paragraph in seconds. Defaults 0.35, 0.15 and 0.8.
	 *
	 * @argument.maxChars Longest caption line in characters; longer sentences are split into several lines. Defaults to 80.
	 *
	 * @argument.split sentence to speak and time whole sentences, clause to work clause by clause (more natural pauses, shorter captions). Defaults to
	 *                 sentence.
	 *
	 * @argument.autoInstall May the engine and the voice be downloaded when missing. Defaults to the module setting autoInstall.
	 *
	 * @argument.onProgress A function called as ( name, bytesDone, bytesTotal ) while something downloads.
	 *
	 * @argument.pronunciations A struct of word to spoken form for words the voice gets wrong, for example { Ortus: "Or tus" }. The captions keep the
	 *                          original words. BoxLang and the names of its family are already said correctly.
	 *
	 * @return A struct with audio (a mono buffer), cues (an array of structs with start, end and text, in seconds) and seconds (the length). Pass the
	 *         cues to audioSubtitles().
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		boolean				allow	= arguments.get( autoInstall ) != null ? arguments.getAsBoolean( autoInstall ) : Speech.autoInstall();
		Map<String, Object>	values	= new HashMap<>();
		if ( arguments.get( pauses ) instanceof IStruct given ) {
			given.forEach( ( k, v ) -> values.put( k.getName().toLowerCase(), v ) );
		}
		String mode = arguments.getAsString( split ).toLowerCase();
		if ( !mode.equals( "sentence" ) && !mode.equals( "clause" ) ) {
			throw new BoxRuntimeException( "split must be sentence or clause but was [" + mode + "]" );
		}
		Narration.Result	result	= Speech.narrate( arguments.getAsString( text ), arguments.getAsString( voice ), allow,
		    AudioSpeak.progress( context, arguments ),
		    arguments.getAsDouble( speed ), Narration.Pauses.from( values ), mode.equals( "clause" ), arguments.getAsInteger( maxChars ),
		    AudioSpeak.pronunciations( arguments ) );
		Array				cueList	= new Array();
		for ( Narration.Cue cue : result.cues() ) {
			cueList.add( Struct.ofNonConcurrent( "start", cue.start(), "end", cue.end(), "text", cue.text() ) );
		}
		return Struct.ofNonConcurrent( "audio", result.audio(), "cues", cueList, "seconds", result.audio().getDurationSeconds() );
	}

}
