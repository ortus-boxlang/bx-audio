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
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Encode an audio buffer to the bytes of a WAV, MP3, FLAC, OGG, Opus, M4A or AAC file, in memory" )
public class AudioEncode extends BIF {

	private static final Key format = Key.of( "format" );

	/**
	 * Constructor
	 */
	public AudioEncode() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.audio ),
		    new Argument( false, "string", format, "wav" ),
		    new Argument( false, "integer", KeyDictionary.bitDepth, 16 ),
		    new Argument( false, "integer", KeyDictionary.bitrate, 0 )
		};
	}

	/**
	 * Turns audio into the bytes of an audio file without touching the disk, for sending in a web response, storing in a database or handing to another
	 * service. WAV, MP3 and FLAC need nothing else. OGG, Opus, M4A and AAC need FFmpeg.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer to encode.
	 *
	 * @argument.format wav, mp3, flac, ogg, opus, m4a or aac. Defaults to wav.
	 *
	 * @argument.bitDepth For WAV: 16 or 24 for integer PCM, or 32 for 32 bit float. For FLAC: 16 or 24. Defaults to 16. Ignored for the other formats.
	 *
	 * @argument.bitrate For MP3, OGG, Opus and AAC: the bitrate in kbps, 0 for the format's default. Ignored for WAV and FLAC.
	 *
	 * @return The encoded file as binary data.
	 */
	public byte[] _invoke( IBoxContext context, ArgumentsScope arguments ) {
		AudioBuffer audio = Audios.require( arguments.get( KeyDictionary.audio ), "audio" );
		return AudioFiles.encode( audio, arguments.getAsString( format ), arguments.getAsInteger( KeyDictionary.bitDepth ),
		    arguments.getAsInteger( KeyDictionary.bitrate ) );
	}

}
