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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.BaseIntegrationTest;
import ortus.boxlang.modules.audio.ffmpeg.Ffmpeg;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class FfmpegBifsTest extends BaseIntegrationTest {

	private Object run( String source ) {
		runtime.executeSource( source, context );
		return variables.get( Key.of( "result" ) );
	}

	private String message( String source ) {
		return assertThrows( BoxRuntimeException.class, () -> run( source ) ).getMessage();
	}

	@DisplayName( "audioFfmpeg says whether FFmpeg is there and what a download would be" )
	@Test
	void testStatus( @TempDir Path home ) {
		useSpeechHome( home );
		IStruct status = ( IStruct ) run( "result = audioFfmpeg()" );
		assertThat( status.get( Key.of( "available" ) ) ).isEqualTo( Ffmpeg.locate() != null );
		assertThat( status.get( Key.of( "platform" ) ).toString() ).matches( "[a-z0-9]+-[a-z0-9_]+" );
		assertThat( status.get( Key.of( "installDir" ) ).toString() ).startsWith( home.toString() );
		if ( status.get( Key.of( "available" ) ) == Boolean.TRUE ) {
			assertThat( status.get( Key.of( "version" ) ).toString() ).isNotEmpty();
			assertThat( ( Array ) status.get( Key.of( "encoders" ) ) ).isNotEmpty();
		}
	}

	@DisplayName( "audioMarkers returns the text and writes the file" )
	@Test
	void testMarkers( @TempDir Path dir ) throws Exception {
		variables.put( Key.of( "target" ), dir.resolve( "nested/chapters.txt" ).toString() );
		String text = ( String ) run( "result = audioMarkers( [ { time: 75, label: 'Outro' }, { time: 0, label: 'Intro' } ], 'youtube', target )" );
		assertThat( text ).isEqualTo( "0:00 Intro\n1:15 Outro\n" );
		assertThat( Files.readString( dir.resolve( "nested/chapters.txt" ) ) ).isEqualTo( text );
		assertThat( ( String ) run( "result = audioMarkers( [ { time: 1, label: 'a' } ], 'srt' )" ) ).contains( "00:00:01,000 --> 00:00:03,000" );
		assertThat( message( "result = audioMarkers( [ { label: 'no time' } ] )" ) ).contains( "must be a struct with a time" );
		assertThat( message( "result = audioMarkers( [ { time: 1, label: 'a' } ], 'pdf' )" ) ).contains( "Unknown marker format" );
	}

	@DisplayName( "OGG output, video info and mux work through the BIFs" )
	@Test
	void testMediaBifs( @TempDir Path dir ) throws Exception {
		assumeTrue( Ffmpeg.locate() != null, "FFmpeg is not installed here" );
		variables.put( Key.of( "ogg" ), dir.resolve( "tone.ogg" ).toString() );
		variables.put( Key.of( "mp4" ), dir.resolve( "video.mp4" ).toString() );
		variables.put( Key.of( "scored" ), dir.resolve( "scored.mp4" ).toString() );
		run( "audioSave( audioOsc( 'sine', 440, 2 ), ogg, 16, 96 ); result = 1" );
		assertThat( ( ( Number ) run( "result = audioLoad( ogg ).getDurationSeconds()" ) ).doubleValue() ).isWithin( 0.15 ).of( 2.0 );
		IStruct info = ( IStruct ) run( "result = videoInfo( ogg )" );
		assertThat( info.get( Key.of( "video" ) ) ).isNull();
		assertThat( ( ( IStruct ) info.get( Key.of( "audio" ) ) ).get( Key.of( "channels" ) ) ).isEqualTo( 1 );
		Ffmpeg tools = Ffmpeg.locate();
		new ProcessBuilder( tools.getFfmpeg().toString(), "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc=duration=2:size=160x120:rate=25", "-c:v", "mpeg4",
		    dir.resolve( "video.mp4" ).toString() ).inheritIO().start().waitFor();
		IStruct video = ( IStruct ) run( "result = videoInfo( mp4 )" );
		assertThat( ( ( IStruct ) video.get( Key.of( "video" ) ) ).get( Key.of( "width" ) ) ).isEqualTo( 160 );
		assertThat( video.get( Key.of( "audio" ) ) ).isNull();
		run( "result = audioMux( video=mp4, audio=audioOsc( 'sine', 440, 5 ), output=scored )" );
		assertThat( ( ( Number ) run( "result = videoInfo( scored ).seconds" ) ).doubleValue() ).isWithin( 0.2 ).of( 2.0 );
		assertThat( ( ( IStruct ) run( "result = videoInfo( scored ).audio" ) ).get( Key.of( "codec" ) ) ).isEqualTo( "aac" );
		assertThat( message( "result = videoInfo( 'nope.mp4' )" ) ).contains( "does not exist" );
	}

}
