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

import ortus.boxlang.modules.audio.codec.AudioFiles;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Decode the bytes of an audio file into an audio buffer" )
public class AudioDecode extends BIF {

	private static final Key	data	= Key.of( "data" );
	private static final Key	format	= Key.of( "format" );

	/**
	 * Constructor
	 */
	public AudioDecode() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", data ),
		    new Argument( false, "string", format, "" )
		};
	}

	/**
	 * Reads audio from memory, for example an upload, a database column or the body of an HTTP response. The format is found from the content, so
	 * WAV, MP3 and FLAC need no hint. OGG, Opus, M4A, AAC and other containers need FFmpeg.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.data The bytes of the file, as binary data.
	 *
	 * @argument.format Optional: a file name or extension such as upload.ogg, which helps to recognise formats that have no signature.
	 *
	 * @return An audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		if ( ! ( arguments.get( data ) instanceof byte[] bytes ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "The data argument must be binary data, for example from fileReadBinary() or audioEncode()" );
		}
		return AudioFiles.decode( bytes, arguments.getAsString( format ) );
	}

}
