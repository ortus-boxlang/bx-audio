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
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.modules.audio.tts.VoiceInfo;

@BoxBIF( description = "List the voices that can be installed, with their licenses and whether they are installed" )
public class AudioVoices extends BIF {

	/**
	 * Constructor
	 */
	public AudioVoices() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "string", KeyDictionary.language, "" ),
		    new Argument( false, "boolean", KeyDictionary.installed, false )
		};
	}

	/**
	 * List the voices that can be installed, with their licenses and whether they are installed
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.language Only voices of this language, for example en_US or en_GB. Empty for all.
	 *
	 * @argument.installed True to list only the voices that are installed.
	 *
	 * @return An array of structs with id, name, language, quality, license, licenseText, shareAlike, source, sizeMB and installed.
	 */
	public Array _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String	language		= arguments.getAsString( KeyDictionary.language );
		boolean	onlyInstalled	= arguments.getAsBoolean( KeyDictionary.installed );
		var		store			= Speech.store();
		Array	result			= new Array();
		for ( VoiceInfo voice : Speech.catalog().all() ) {
			boolean isInstalled = store.isInstalled( voice );
			if ( ( !language.isEmpty() && !voice.language().equalsIgnoreCase( language ) ) || ( onlyInstalled && !isInstalled ) ) {
				continue;
			}
			result.add( Struct.ofNonConcurrent( "id", voice.id(), "name", voice.name(), "language", voice.language(), "quality", voice.quality(),
			    "license", voice.license(), "licenseText", voice.licenseText(), "shareAlike", voice.shareAlike(), "source", voice.source(),
			    "sizeMB", Speech.megabytes( voice.downloadSize() ), "installed", isInstalled ) );
		}
		return result;
	}

}
