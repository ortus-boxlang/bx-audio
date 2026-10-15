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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.codec.AudioFiles;
import ortus.boxlang.modules.audio.dsp.MasteringTest;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Runs against the FFmpeg on this computer, and is skipped where there is none
 */
public class FfmpegTest {

	private static Ffmpeg ffmpeg;

	@BeforeAll
	static void find() {
		ffmpeg = Ffmpeg.locate();
		assumeTrue( ffmpeg != null, "FFmpeg is not installed here" );
	}

	@DisplayName( "FFmpeg is found with its version and the encoders the module uses" )
	@Test
	void testLocate() {
		assertThat( ffmpeg.version() ).matches( "[0-9N][\\w.\\-]*" );
		assertThat( ffmpeg.encoders() ).containsAtLeast( "aac", "flac" );
		assertThat( ffmpeg.getSource() ).isAnyOf( "system", "installed", "configured" );
	}

	@DisplayName( "OGG, Opus and M4A files are written and read back with the right length, rate and pitch" )
	@Test
	void testEncodeDecode( @TempDir Path dir ) throws IOException {
		assumeTrue( ffmpeg.encoders().containsAll( List.of( "libvorbis", "libopus", "aac" ) ), "The encoders are not all in this FFmpeg" );
		AudioBuffer tone = MasteringTest.sine( 440, 2, -10, 2 );
		for ( String ext : List.of( ".ogg", ".opus", ".m4a", ".aac" ) ) {
			Path file = dir.resolve( "tone" + ext );
			AudioFiles.write( tone, file, 16, 0 );
			assertThat( Files.size( file ) ).isGreaterThan( 1000L );
			AudioBuffer back = AudioFiles.read( file );
			assertThat( back.getChannels() ).isEqualTo( 2 );
			assertThat( back.getDurationSeconds() ).isWithin( 0.15 ).of( 2.0 );
			assertThat( MasteringTest.frequency( back ) ).isWithin( 8.0 ).of( 440.0 );
		}
		Path ogg = dir.resolve( "q.ogg" );
		AudioFiles.write( tone, ogg, 16, 64 );
		assertThat( Files.size( ogg ) ).isLessThan( 40_000L );
	}

	@DisplayName( "a video's audio is read, probed, and replaced with the picture left alone" )
	@Test
	void testVideo( @TempDir Path dir ) throws IOException {
		Path video = dir.resolve( "video.mp4" );
		Ffmpeg.run(
		    List.of( ffmpeg.getFfmpeg().toString(), "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc=duration=3:size=160x120:rate=25", "-f", "lavfi", "-i",
		        "sine=frequency=330:duration=3", "-c:v", "mpeg4", "-c:a", "aac", "-shortest", video.toString() ) );
		assumeTrue( Files.exists( video ), "FFmpeg could not make a test video" );
		@SuppressWarnings( "unchecked" )
		Map<String, Object> info = ffmpeg.probe( video );
		assertThat( info ).containsKey( "streams" );
		AudioBuffer original = ffmpeg.decode( video );
		assertThat( original.getDurationSeconds() ).isWithin( 0.2 ).of( 3.0 );
		assertThat( MasteringTest.frequency( original ) ).isWithin( 10.0 ).of( 330.0 );

		Path out = dir.resolve( "scored.mp4" );
		ffmpeg.mux( video, MasteringTest.sine( 880, 10, -10, 2 ), out, false, -12, 0, 0 );
		AudioBuffer scored = ffmpeg.decode( out );
		// Never longer than the picture, and the new audio replaced the old
		assertThat( scored.getDurationSeconds() ).isWithin( 0.2 ).of( 3.0 );
		assertThat( MasteringTest.frequency( scored ) ).isWithin( 15.0 ).of( 880.0 );

		Path mixed = dir.resolve( "mixed.mp4" );
		ffmpeg.mux( video, MasteringTest.sine( 880, 2, -10, 2 ), mixed, true, -6, 0.5, 0 );
		AudioBuffer both = ffmpeg.decode( mixed );
		assertThat( both.getDurationSeconds() ).isWithin( 0.2 ).of( 3.0 );
		assertThat( both.getChannels() ).isEqualTo( 2 );
		assertThat( assertThrows( BoxRuntimeException.class, () -> ffmpeg.mux( dir.resolve( "none.mp4" ), scored, out, false, 0, 0, 0 ) ).getMessage() )
		    .contains( "does not exist" );
	}

	@DisplayName( "a file without audio, or that is not media, is refused with a message" )
	@Test
	void testErrors( @TempDir Path dir ) throws IOException {
		Path text = dir.resolve( "notes.txt" );
		Files.writeString( text, "this is not audio" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> ffmpeg.probe( text ) ).getMessage() ).contains( "could not read" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> ffmpeg.probe( dir.resolve( "missing.ogg" ) ) ).getMessage() ).contains( "does not exist" );
		assertThat(
		    assertThrows( BoxRuntimeException.class, () -> ffmpeg.encode( MasteringTest.sine( 440, 1, -10, 1 ), dir.resolve( "x.xyz" ), 0 ) ).getMessage() )
		    .contains( "Cannot write" );
	}

}
