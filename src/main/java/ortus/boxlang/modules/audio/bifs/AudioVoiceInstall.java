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
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.modules.audio.tts.EngineInstaller;
import ortus.boxlang.modules.audio.tts.VoiceInfo;

@BoxBIF( description = "Download a voice, or the text to speech engine itself, and check it" )
public class AudioVoiceInstall extends BIF {

	/**
	 * Constructor
	 */
	public AudioVoiceInstall() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "string", KeyDictionary.voice ),
		    new Argument( false, "function", KeyDictionary.onProgress )
		};
	}

	/**
	 * Download a voice, or the text to speech engine itself, and check it
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.voice A voice id or short name from audioVoices(), or "engine" for the speech engine that every voice needs.
	 *
	 * @argument.onProgress A function called as ( name, bytesDone, bytesTotal ) while files download.
	 *
	 * @return A struct with name, installed and the path of what was installed.
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String							name		= arguments.getAsString( KeyDictionary.voice );
		var								callback	= arguments.get( KeyDictionary.onProgress );
		long[]							last		= { 0 };
		EngineInstaller.NamedProgress	progress	= callback == null ? null : ( file, done, total ) -> {
														long now = System.nanoTime();
														if ( done >= total || now - last[ 0 ] > 200_000_000L ) {
															last[ 0 ] = now;
															context.invokeFunction( callback, new Object[] { file, done, total } );
														}
													};
		if ( name.equalsIgnoreCase( "engine" ) ) {
			EngineInstaller engine = Speech.engine();
			engine.install( progress );
			return Struct.ofNonConcurrent( "name", "engine", "installed", engine.isInstalled(), "path", engine.getDir().toString() );
		}
		VoiceInfo	voice	= Speech.catalog().find( name );
		var			store	= Speech.store();
		store.install( voice, progress == null ? null : ( done, total ) -> progress.update( voice.id(), done, total ) );
		return Struct.ofNonConcurrent( "name", voice.id(), "installed", store.isInstalled( voice ), "path", store.modelFile( voice ).toString() );
	}

}
