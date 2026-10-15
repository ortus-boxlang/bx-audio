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
import ortus.boxlang.modules.audio.tts.Storage;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Delete downloaded engine, voices, FFmpeg or cached files from the bx-audio home" )
public class AudioStorageClear extends BIF {

	private static final Key kind = Key.of( "kind" );

	/**
	 * Constructor
	 */
	public AudioStorageClear() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "string", kind, "all" )
		};
	}

	/**
	 * Delete downloaded engine, voices, FFmpeg or cached files from the bx-audio home
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.kind What to delete: engine, voices, ffmpeg, cache or all. Defaults to all.
	 *
	 * @return The megabytes freed.
	 */
	public Double _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return Speech.megabytes( Storage.clear( Speech.home(), arguments.getAsString( kind ) ) );
	}

}
