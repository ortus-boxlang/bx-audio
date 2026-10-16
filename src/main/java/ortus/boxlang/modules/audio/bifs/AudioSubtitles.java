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
import java.util.ArrayList;
import java.util.List;
import ortus.boxlang.modules.audio.tts.Narration;
import ortus.boxlang.modules.audio.tts.Subtitles;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.modules.audio.util.Numbers;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.util.FileSystemUtil;

@BoxBIF( description = "Write captions as SRT or WebVTT subtitles for a video" )
public class AudioSubtitles extends BIF {

	private static final Key	cues	= Key.of( "cues" );
	private static final Key	format	= Key.of( "format" );
	private static final Key	path	= Key.of( "path" );
	private static final Key	offset	= Key.of( "offset" );

	/**
	 * Constructor
	 */
	public AudioSubtitles() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "array", cues ),
		    new Argument( false, "string", format, "srt" ),
		    new Argument( false, "string", path, "" ),
		    new Argument( false, "double", offset, 0.0 )
		};
	}

	/**
	 * Write captions as SRT or WebVTT subtitles for a video
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.cues An array of structs with start, end (seconds) and text, such as the cues from audioSpeakCues().
	 *
	 * @argument.format srt or vtt. Defaults to srt.
	 *
	 * @argument.path A file to write the subtitles to as well. Empty to only return the text.
	 *
	 * @argument.offset Seconds added to every time, for narration that starts later in the video. Defaults to 0.
	 *
	 * @return The subtitle text.
	 */
	public String _invoke( IBoxContext context, ArgumentsScope arguments ) {
		List<Narration.Cue> list = new ArrayList<>();
		for ( Object entry : arguments.getAsArray( cues ) ) {
			if ( ! ( entry instanceof IStruct item ) || item.get( Key.of( "start" ) ) == null || item.get( Key.of( "end" ) ) == null
			    || item.get( Key.of( "text" ) ) == null ) {
				throw new BoxRuntimeException( "Each cue must be a struct with start, end and text, such as { start: 0, end: 2.5, text: \"Hello\" }" );
			}
			list.add( new Narration.Cue( Numbers.toDouble( item.get( Key.of( "start" ) ), "the start of a cue" ),
			    Numbers.toDouble( item.get( Key.of( "end" ) ), "the end of a cue" ),
			    String.valueOf( item.get( Key.of( "text" ) ) ) ) );
		}
		String	result	= Subtitles.format( list, arguments.getAsString( format ), arguments.getAsDouble( offset ) );
		String	file	= arguments.getAsString( path );
		if ( !file.isBlank() ) {
			try {
				Path target = FileSystemUtil.expandPath( context, file ).absolutePath();
				Files.createDirectories( target.getParent() );
				Files.writeString( target, result );
			} catch ( IOException e ) {
				throw new BoxRuntimeException( "Could not write [" + file + "]: " + e.getMessage(), e );
			}
		}
		return result;
	}

}
