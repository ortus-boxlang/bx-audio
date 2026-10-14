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
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.modules.audio.tts.EngineInstaller;
import ortus.boxlang.modules.audio.tts.VoiceInfo;

@BoxBIF( description = "Describe the text to speech setup: where it lives, whether the engine is installed, and which voices are" )
public class AudioSpeechStatus extends BIF {

	/**
	 * Constructor
	 */
	public AudioSpeechStatus() {
		super();
		declaredArguments = new Argument[] {

		};
	}

	/**
	 * Describe the text to speech setup: where it lives, whether the engine is installed, and which voices are
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return A struct with home, engineInstalled, engineSizeMB, voicesDir and voices, the ids of the installed voices.
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		EngineInstaller	engine		= Speech.engine();
		var				store		= Speech.store();
		Array			installed	= new Array();
		for ( VoiceInfo voice : Speech.catalog().all() ) {
			if ( store.isInstalled( voice ) ) {
				installed.add( voice.id() );
			}
		}
		return Struct.ofNonConcurrent( "home", Speech.home().toString(), "engineInstalled", engine.isInstalled(), "engineSizeMB",
		    Speech.megabytes( engine.downloadSize() ), "voicesDir", store.getVoicesDir().toString(), "voices", installed );
	}

}
