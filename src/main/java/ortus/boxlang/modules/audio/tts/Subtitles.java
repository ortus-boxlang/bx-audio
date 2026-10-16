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
package ortus.boxlang.modules.audio.tts;

import java.util.List;
import java.util.Locale;

import ortus.boxlang.modules.audio.tts.Narration.Cue;
import ortus.boxlang.modules.audio.util.Timecode;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Captions as SRT or WebVTT text
 */
public final class Subtitles {

	public static final List<String> FORMATS = List.of( "srt", "vtt" );

	private Subtitles() {
	}

	/**
	 * A blank line would end the cue, an arrow would read as a time range, and WebVTT reads markup in the text
	 */
	private static String cueText( String text, boolean srt ) {
		String clean = text.replace( "\r\n", "\n" ).replaceAll( "\\n{2,}", "\n" ).replace( "-->", "->" );
		return srt ? clean : clean.replace( "&", "&amp;" ).replace( "<", "&lt;" );
	}

	/**
	 * @param cues   The captions in order
	 * @param format srt or vtt
	 * @param offset Seconds added to every time, for audio that starts later in the video
	 */
	public static String format( List<Cue> cues, String format, double offset ) {
		String kind = format == null ? "" : format.toLowerCase( Locale.ROOT );
		if ( !FORMATS.contains( kind ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Unknown subtitle format [" + format + "]. Use srt or vtt." );
		}
		boolean			srt	= kind.equals( "srt" );
		StringBuilder	out	= new StringBuilder( srt ? "" : "WEBVTT\n\n" );
		int				n	= 1;
		for ( Cue cue : cues ) {
			if ( srt ) {
				out.append( n ).append( '\n' );
			}
			out.append( Timecode.clock( cue.start() + offset, srt ) ).append( " --> " ).append( Timecode.clock( cue.end() + offset, srt ) ).append( '\n' )
			    .append( cueText( cue.text(), srt ) ).append( "\n\n" );
			n++;
		}
		return out.toString();
	}

}
