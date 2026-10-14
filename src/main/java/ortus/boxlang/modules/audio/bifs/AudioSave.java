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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import ortus.boxlang.modules.audio.codec.WavCodec;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.util.FileSystemUtil;

@BoxBIF( description = "Save an audio buffer to a file" )
public class AudioSave extends BIF {

	/**
	 * Constructor
	 */
	public AudioSave() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.audio ),
		    new Argument( true, "string", Key.path ),
		    new Argument( false, "integer", KeyDictionary.bitDepth, 16 )
		};
	}

	/**
	 * Writes an audio buffer to a WAV file, replacing any existing file. Missing parent folders are created.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer to save.
	 *
	 * @argument.path The destination path. Relative paths and mappings are resolved like other file functions.
	 *
	 * @argument.bitDepth 16 or 24 for integer PCM, or 32 for 32 bit float. Defaults to 16. Samples beyond -1 to 1 are clipped for 16 and 24.
	 *
	 * @return The absolute path of the file that was written.
	 */
	public String _invoke( IBoxContext context, ArgumentsScope arguments ) {
		if ( ! ( arguments.get( KeyDictionary.audio ) instanceof AudioBuffer audio ) ) {
			throw new BoxRuntimeException( "The audio argument must be an audio buffer, for example from audioLoad() or audioSilence()" );
		}
		Path path = FileSystemUtil.expandPath( context, arguments.getAsString( Key.path ) ).absolutePath();
		try {
			Path parent = path.getParent();
			if ( parent != null ) {
				Files.createDirectories( parent );
			}
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Could not create the folder for [" + path + "]: " + e.getMessage(), e );
		}
		WavCodec.write( audio, path, arguments.getAsInteger( KeyDictionary.bitDepth ) );
		return path.toString();
	}

}
