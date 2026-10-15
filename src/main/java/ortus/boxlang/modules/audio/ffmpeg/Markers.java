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
package ortus.boxlang.modules.audio.ffmpeg;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.jr.ob.JSON;

import ortus.boxlang.modules.audio.util.Timecode;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Markers, the points in time that a video editor or a player shows: written as JSON, CSV, a DaVinci Resolve EDL, SRT or WebVTT cues, FFmpeg
 * chapters or a YouTube style chapter list
 */
public final class Markers {

	public static final List<String> FORMATS = List.of( "json", "csv", "edl", "srt", "vtt", "ffmetadata", "youtube" );

	private Markers() {
	}

	/**
	 * @param time     Seconds from the start
	 * @param label    What to call it
	 * @param duration Seconds it lasts, 0 to last until the next marker
	 */
	public record Marker( double time, String label, double duration ) {
	}

	/**
	 * @param fps   Frame rate for EDL timecodes
	 * @param total Length of the whole program in seconds, which ends the last marker, 0 to give it two seconds
	 */
	public static String format( List<Marker> markers, String format, int fps, double total ) {
		String kind = format == null ? "" : format.toLowerCase( Locale.ROOT );
		if ( !FORMATS.contains( kind ) ) {
			throw new BoxRuntimeException( "Unknown marker format [" + format + "]. Use one of " + FORMATS );
		}
		if ( fps < 1 || fps > 120 ) {
			throw new BoxRuntimeException( "fps must be between 1 and 120 but was " + fps );
		}
		List<Marker>	sorted	= markers.stream().sorted( Comparator.comparingDouble( Marker::time ) ).toList();
		StringBuilder	out		= new StringBuilder();
		switch ( kind ) {
			case "json" -> {
				try {
					java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
					for ( Marker m : sorted ) {
						list.add( new java.util.LinkedHashMap<>(
						    java.util.Map.of( "time", m.time(), "label", m.label(), "duration", end( sorted, m, total ) - m.time() ) ) );
					}
					return JSON.std.with( JSON.Feature.PRETTY_PRINT_OUTPUT ).asString( list ) + "\n";
				} catch ( IOException e ) {
					throw new BoxRuntimeException( "Could not write the markers: " + e.getMessage(), e );
				}
			}
			case "csv" -> {
				out.append( "time,label,duration\n" );
				for ( Marker m : sorted ) {
					out.append( String.format( Locale.ROOT, "%.3f,%s,%.3f%n", m.time(), csv( m.label() ), end( sorted, m, total ) - m.time() ) );
				}
			}
			case "edl" -> {
				out.append( "TITLE: Markers\nFCM: NON-DROP FRAME\n\n" );
				int n = 1;
				for ( Marker m : sorted ) {
					String in = Timecode.frames( m.time(), fps ), after = Timecode.frames( m.time() + 1.0 / fps, fps );
					out.append(
					    String.format( Locale.ROOT, "%03d  001      V     C        %s %s %s %s%n |C:ResolveColorBlue |M:%s |D:1%n%n", n++, in, after, in, after,
					        m.label().replace( '|', '/' ) ) );
				}
			}
			case "srt", "vtt" -> {
				boolean srt = kind.equals( "srt" );
				if ( !srt ) {
					out.append( "WEBVTT\n\n" );
				}
				int n = 1;
				for ( Marker m : sorted ) {
					if ( srt ) {
						out.append( n ).append( "\n" );
					}
					out.append( Timecode.clock( m.time(), srt ) ).append( " --> " ).append( Timecode.clock( end( sorted, m, total ), srt ) ).append( "\n" )
					    .append( m.label() ).append( "\n\n" );
					n++;
				}
			}
			case "ffmetadata" -> {
				out.append( ";FFMETADATA1\n" );
				for ( Marker m : sorted ) {
					out.append( "[CHAPTER]\nTIMEBASE=1/1000\nSTART=" ).append( Math.round( m.time() * 1000 ) ).append( "\nEND=" )
					    .append( Math.round( end( sorted, m, total ) * 1000 ) ).append( "\ntitle=" ).append( m.label().replace( "\n", " " ) ).append( "\n" );
				}
			}
			default -> {
				for ( Marker m : sorted ) {
					out.append( Timecode.short_( m.time() ) ).append( ' ' ).append( m.label() ).append( '\n' );
				}
			}
		}
		return out.toString();
	}

	private static double end( List<Marker> sorted, Marker marker, double total ) {
		if ( marker.duration() > 0 ) {
			return marker.time() + marker.duration();
		}
		int index = sorted.indexOf( marker );
		if ( index + 1 < sorted.size() ) {
			return sorted.get( index + 1 ).time();
		}
		return total > marker.time() ? total : marker.time() + 2;
	}

	private static String csv( String text ) {
		return text.contains( "," ) || text.contains( "\"" ) || text.contains( "\n" ) ? "\"" + text.replace( "\"", "\"\"" ) + "\"" : text;
	}

}
