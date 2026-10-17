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

import ortus.boxlang.modules.audio.tts.Credits;
import ortus.boxlang.modules.audio.tts.Speech;
import ortus.boxlang.modules.audio.tts.VoiceInfo;
import ortus.boxlang.modules.audio.util.AudioException;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;

@BoxBIF( description = "The credits the licenses of some voices ask for, as text for an about page" )
public class AudioCredits extends BIF {

	private static final Key voices = Key.of( "voices" );

	/**
	 * Constructor
	 */
	public AudioCredits() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", voices )
		};
	}

	/**
	 * Writes the credits for the voices you use, to put on an about page or in the notices of a product. A line for each voice whose license
	 * asks that the creator is credited (CC BY, CC BY-SA, Apache 2.0), with the creator of the training data, the license and a link to it,
	 * and a sentence naming the voices that ask for nothing. Voices that are not offered raise an error, as they cannot be used.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.voices A voice id or short name, a comma separated list of them, or an array, for example [ "joe", "en_US-sam-medium" ].
	 *
	 * @return The credits as text, one line a voice, in a form that is also valid Markdown.
	 */
	public String _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Object			given	= arguments.get( voices );
		List<String>	names	= new ArrayList<>();
		if ( given instanceof Array array ) {
			for ( Object each : array ) {
				names.add( String.valueOf( each ) );
			}
		} else {
			for ( String each : String.valueOf( given ).split( "," ) ) {
				names.add( each );
			}
		}
		names.removeIf( n -> n.isBlank() );
		if ( names.isEmpty() ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Name at least one voice, for example audioCredits( \"joe\" )" );
		}
		List<VoiceInfo> found = new ArrayList<>();
		for ( String name : names ) {
			found.add( Speech.catalog().find( name ) );
		}
		return Credits.text( found );
	}

}
