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
import java.util.List;
import java.util.Map;
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
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

@BoxBIF( description = "Describe any audio or video file with FFprobe: length, size, and the video and audio streams" )
public class VideoInfo extends BIF {

	private static final Key path = Key.of( "path" );

	/**
	 * Constructor
	 */
	public VideoInfo() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "string", path )
		};
	}

	/**
	 * Describe any audio or video file with FFprobe: length, size, and the video and audio streams
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.path The media file.
	 *
	 * @return A struct with path, format, seconds, bitrate, sizeBytes, video (codec, width, height, fps; null when there is none), audio (codec,
	 *         channels, sampleRate; null when none) and streams, the raw stream list.
	 */
	@SuppressWarnings( "unchecked" )
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Path				file		= Path.of( arguments.getAsString( path ) ).toAbsolutePath();
		Ffmpeg				tools		= FfmpegSupport.require( "Reading media information" );
		Map<String, Object>	info		= tools.probe( file );
		Map<String, Object>	format		= ( Map<String, Object> ) info.getOrDefault( "format", Map.of() );
		IStruct				video		= null;
		IStruct				audioInfo	= null;
		Array				streams		= new Array();
		for ( Object s : ( List<Object> ) info.getOrDefault( "streams", List.of() ) ) {
			Map<String, Object> stream = ( Map<String, Object> ) s;
			streams.add( Struct.fromMap( stream ) );
			if ( video == null && "video".equals( stream.get( "codec_type" ) ) ) {
				video = Struct.ofNonConcurrent( "codec", stream.get( "codec_name" ), "width", stream.get( "width" ), "height", stream.get( "height" ), "fps",
				    fps( stream ) );
			} else if ( audioInfo == null && "audio".equals( stream.get( "codec_type" ) ) ) {
				audioInfo = Struct.ofNonConcurrent( "codec", stream.get( "codec_name" ), "channels", stream.get( "channels" ), "sampleRate",
				    Integer.parseInt( String.valueOf( stream.get( "sample_rate" ) ) ) );
			}
		}
		return Struct.ofNonConcurrent( "path", file.toString(), "format", format.get( "format_name" ), "seconds", number( format.get( "duration" ) ), "bitrate",
		    number( format.get( "bit_rate" ) ),
		    "sizeBytes", number( format.get( "size" ) ), "video", video, "audio", audioInfo, "streams", streams );
	}

	private static Object number( Object value ) {
		return value == null ? 0.0 : Double.parseDouble( String.valueOf( value ) );
	}

	private static double fps( Map<String, Object> stream ) {
		String[]	parts	= String.valueOf( stream.getOrDefault( "avg_frame_rate", "0/1" ) ).split( "/" );
		double		top		= Double.parseDouble( parts[ 0 ] ), bottom = parts.length > 1 ? Double.parseDouble( parts[ 1 ] ) : 1;
		return bottom == 0 ? 0 : Math.round( top / bottom * 1000 ) / 1000.0;
	}

}
