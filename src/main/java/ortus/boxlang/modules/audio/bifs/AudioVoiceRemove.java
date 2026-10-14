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

import ortus.boxlang.modules.audio.tts.Speech;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Delete an installed voice, or the text to speech engine, from disk" )
public class AudioVoiceRemove extends BIF {

	/**
	 * Constructor
	 */
	public AudioVoiceRemove() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "string", KeyDictionary.voice )
		};
	}

	/**
	 * Delete an installed voice, or the text to speech engine, from disk
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.voice A voice id or short name from audioVoices(), or "engine".
	 *
	 * @return True if something was deleted, false if it was not installed.
	 */
	public Boolean _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String name = arguments.getAsString( KeyDictionary.voice );
		return name.equalsIgnoreCase( "engine" ) ? Speech.engine().remove() : Speech.store().remove( Speech.catalog().find( name ) );
	}

}
