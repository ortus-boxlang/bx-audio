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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class MarkersTest {

	private static final List<Markers.Marker> MARKERS = List.of( new Markers.Marker( 65.5, "Chorus, loud", 0 ), new Markers.Marker( 0, "Intro", 0 ),
	    new Markers.Marker( 30, "Verse", 10 ) );

	@DisplayName( "markers are sorted and written in every format" )
	@Test
	void testFormats() {
		String csv = Markers.format( MARKERS, "csv", 30, 90 );
		assertThat( csv ).startsWith( "time,label,duration\n0.000,Intro,30.000\n30.000,Verse,10.000\n" );
		assertThat( csv ).contains( "65.500,\"Chorus, loud\",24.500" );
		assertThat( Markers.format( MARKERS, "json", 30, 90 ) ).contains( "\"label\" : \"Intro\"" );
		String srt = Markers.format( MARKERS, "srt", 30, 90 );
		assertThat( srt ).startsWith( "1\n00:00:00,000 --> 00:00:30,000\nIntro\n" );
		assertThat( srt ).contains( "2\n00:00:30,000 --> 00:00:40,000\nVerse" );
		assertThat( Markers.format( MARKERS, "vtt", 30, 90 ) ).startsWith( "WEBVTT\n\n00:00:00.000 --> 00:00:30.000" );
		String edl = Markers.format( MARKERS, "edl", 25, 90 );
		assertThat( edl ).contains( "FCM: NON-DROP FRAME" );
		assertThat( edl ).contains( "00:00:30:00" );
		assertThat( edl ).contains( "|M:Verse" );
		String meta = Markers.format( MARKERS, "ffmetadata", 30, 90 );
		assertThat( meta ).startsWith( ";FFMETADATA1\n[CHAPTER]\nTIMEBASE=1/1000\nSTART=0\nEND=30000\ntitle=Intro" );
		assertThat( Markers.format( MARKERS, "youtube", 30, 90 ) ).isEqualTo( "0:00 Intro\n0:30 Verse\n1:06 Chorus, loud\n" );
	}

	@DisplayName( "an unknown format or frame rate is refused" )
	@Test
	void testErrors() {
		assertThat( assertThrows( BoxRuntimeException.class, () -> Markers.format( MARKERS, "xml", 30, 0 ) ).getMessage() ).contains( "Unknown marker format" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> Markers.format( MARKERS, "edl", 0, 0 ) ).getMessage() ).contains( "fps" );
	}

}
