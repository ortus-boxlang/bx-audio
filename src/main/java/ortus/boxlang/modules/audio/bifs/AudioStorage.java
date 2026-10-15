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
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

@BoxBIF( description = "Show where bx-audio keeps its downloaded engine, voices and tools, and how big they are" )
public class AudioStorage extends BIF {

	/**
	 * Constructor
	 */
	public AudioStorage() {
		super();
		declaredArguments = new ortus.boxlang.runtime.types.Argument[] {};
	}

	/**
	 * Show where bx-audio keeps its downloaded engine, voices and tools, and how big they are
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return A struct with home, totalMB and folders: an array of structs with kind, path, description, exists and sizeMB.
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Array	folders	= new Array();
		long	total	= 0;
		for ( Storage.Entry entry : Storage.list( Speech.home() ) ) {
			total += entry.bytes();
			folders.add( Struct.ofNonConcurrent( "kind", entry.kind(), "path", entry.path().toString(), "description", entry.description(), "exists",
			    entry.exists(), "sizeMB", Speech.megabytes( entry.bytes() ) ) );
		}
		return Struct.ofNonConcurrent( "home", Speech.home().toString(), "totalMB", Speech.megabytes( total ), "folders", folders );
	}

}
