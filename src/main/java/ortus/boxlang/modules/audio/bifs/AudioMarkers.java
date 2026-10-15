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
import ortus.boxlang.modules.audio.ffmpeg.Markers;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

@BoxBIF( description = "Write markers (points in time with labels) as JSON, CSV, a Resolve EDL, SRT, WebVTT, FFmpeg chapters or a YouTube chapter list" )
public class AudioMarkers extends BIF {

	private static final Key	markers	= Key.of( "markers" );
	private static final Key	format	= Key.of( "format" );
	private static final Key	path	= Key.of( "path" );
	private static final Key	fps		= Key.of( "fps" );
	private static final Key	total	= Key.of( "total" );

	/**
	 * Constructor
	 */
	public AudioMarkers() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "array", markers ),
		    new Argument( false, "string", format, "json" ),
		    new Argument( false, "string", path, "" ),
		    new Argument( false, "integer", fps, 30 ),
		    new Argument( false, "double", total, 0.0 )
		};
	}

	/**
	 * Write markers (points in time with labels) as JSON, CSV, a Resolve EDL, SRT, WebVTT, FFmpeg chapters or a YouTube chapter list
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.markers An array of structs with time (seconds), label and optionally duration (seconds; 0 lasts until the next marker).
	 *
	 * @argument.format json, csv, edl, srt, vtt, ffmetadata or youtube. Defaults to json.
	 *
	 * @argument.path A file to write the result to as well. Empty to only return the text.
	 *
	 * @argument.fps Frame rate for EDL timecodes. Defaults to 30.
	 *
	 * @argument.total Length of the whole program in seconds, which ends the last marker. Defaults to 2 seconds after it.
	 *
	 * @return The text of the markers in that format.
	 */
	public String _invoke( IBoxContext context, ArgumentsScope arguments ) {
		List<Markers.Marker> list = new ArrayList<>();
		for ( Object entry : arguments.getAsArray( markers ) ) {
			if ( ! ( entry instanceof IStruct item ) || item.get( Key.of( "time" ) ) == null ) {
				throw new BoxRuntimeException( "Each marker must be a struct with a time in seconds and a label, such as { time: 12.5, label: \"Chorus\" }" );
			}
			Object d = item.get( Key.of( "duration" ) );
			list.add(
			    new Markers.Marker( ( ( Number ) item.get( Key.of( "time" ) ) ).doubleValue(), String.valueOf( item.getOrDefault( Key.of( "label" ), "" ) ),
			        d == null ? 0 : ( ( Number ) d ).doubleValue() ) );
		}
		String	text	= Markers.format( list, arguments.getAsString( format ), arguments.getAsInteger( fps ), arguments.getAsDouble( total ) );
		String	file	= arguments.getAsString( path );
		if ( !file.isBlank() ) {
			try {
				Path target = Path.of( file ).toAbsolutePath();
				Files.createDirectories( target.getParent() );
				Files.writeString( target, text );
			} catch ( IOException e ) {
				throw new BoxRuntimeException( "Could not write [" + file + "]: " + e.getMessage(), e );
			}
		}
		return text;
	}

}
