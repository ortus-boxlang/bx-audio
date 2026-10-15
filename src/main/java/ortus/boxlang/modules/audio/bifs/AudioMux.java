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

import java.nio.file.Path;
import ortus.boxlang.modules.audio.ffmpeg.Ffmpeg;
import ortus.boxlang.modules.audio.ffmpeg.FfmpegSupport;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Put audio on a video file, copying the picture without re-encoding it" )
public class AudioMux extends BIF {

	private static final Key	video			= Key.of( "video" );
	private static final Key	audio			= Key.of( "audio" );
	private static final Key	output			= Key.of( "output" );
	private static final Key	keepOriginal	= Key.of( "keepOriginal" );
	private static final Key	originalDb		= Key.of( "originalDb" );
	private static final Key	offset			= Key.of( "offset" );
	private static final Key	bitrate			= Key.of( "bitrate" );

	/**
	 * Constructor
	 */
	public AudioMux() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "string", video ),
		    new Argument( true, "any", audio ),
		    new Argument( true, "string", output ),
		    new Argument( false, "boolean", keepOriginal, false ),
		    new Argument( false, "double", originalDb, -12.0 ),
		    new Argument( false, "double", offset, 0.0 ),
		    new Argument( false, "integer", bitrate, 0 )
		};
	}

	/**
	 * Put audio on a video file, copying the picture without re-encoding it
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.video The video file.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.output Where to write the result. The extension picks the container: mp4, mov, mkv or webm.
	 *
	 * @argument.keepOriginal True to keep the video's own audio underneath the new audio. Defaults to false, which replaces it.
	 *
	 * @argument.originalDb Gain of the original audio when it is kept, in dB. Defaults to -12.
	 *
	 * @argument.offset Seconds to delay the new audio. Defaults to 0.
	 *
	 * @argument.bitrate AAC (or Opus for webm) bitrate in kbps. 0 means 192.
	 *
	 * @return The absolute path of the written file. The result is never longer than the video.
	 */
	public String _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Path	out		= Path.of( arguments.getAsString( output ) ).toAbsolutePath();
		Ffmpeg	tools	= FfmpegSupport.require( "Adding audio to a video" );
		tools.mux( Path.of( arguments.getAsString( video ) ).toAbsolutePath(), Audios.require( arguments.get( audio ), "audio" ), out,
		    arguments.getAsBoolean( keepOriginal ), arguments.getAsDouble( originalDb ),
		    arguments.getAsDouble( offset ), arguments.getAsInteger( bitrate ) );
		return out.toString();
	}

}
