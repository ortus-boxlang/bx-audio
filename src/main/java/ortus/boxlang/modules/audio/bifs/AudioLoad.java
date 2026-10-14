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

import java.nio.file.Files;
import java.nio.file.Path;

import ortus.boxlang.modules.audio.codec.AudioFiles;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.util.FileSystemUtil;

@BoxBIF( description = "Load a WAV or MP3 file into an audio buffer" )
public class AudioLoad extends BIF {

	/**
	 * Constructor
	 */
	public AudioLoad() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "string", Key.path )
		};
	}

	/**
	 * Reads an audio file fully into memory. WAV (PCM at 8, 16, 24 and 32 bits, and IEEE float) and MP3 are supported, and the format is found from the
	 * content of the file, not its name. MP3 needs the optional codec jars that ship with the module.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.path The path of the audio file. Relative paths and mappings are resolved like other file functions.
	 *
	 * @return The audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Path path = FileSystemUtil.expandPath( context, arguments.getAsString( Key.path ) ).absolutePath();
		if ( !Files.isRegularFile( path ) ) {
			throw new BoxRuntimeException( "The audio file [" + path + "] does not exist" );
		}
		return AudioFiles.read( path );
	}

}
