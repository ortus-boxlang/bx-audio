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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class StorageTest {

	private void fill( Path file, int bytes ) throws IOException {
		Files.createDirectories( file.getParent() );
		Files.write( file, new byte[ bytes ] );
	}

	@DisplayName( "the default home is .bxaudio in the user's home" )
	@Test
	void testDefaultHome() {
		String previous = System.getProperty( "bxaudio.home" );
		System.clearProperty( "bxaudio.home" );
		try {
			if ( System.getenv( "BX_AUDIO_HOME" ) == null ) {
				assertThat( TtsHome.home() ).isEqualTo( Path.of( System.getProperty( "user.home" ), ".bxaudio" ).toAbsolutePath() );
			}
			System.setProperty( "bxaudio.home", "/tmp/elsewhere" );
			assertThat( TtsHome.home() ).isEqualTo( Path.of( "/tmp/elsewhere" ).toAbsolutePath() );
		} finally {
			if ( previous == null ) {
				System.clearProperty( "bxaudio.home" );
			} else {
				System.setProperty( "bxaudio.home", previous );
			}
		}
	}

	@DisplayName( "list reports every kind with its size, and clear removes only what was asked" )
	@Test
	void testListAndClear( @TempDir Path home ) throws IOException {
		Files.writeString( home.resolve( Storage.MARKER ), "" );
		fill( TtsHome.engine( home ).resolve( "v1/a.jar" ), 1000 );
		fill( TtsHome.voices( home ).resolve( "joe/m.onnx" ), 500 );
		fill( TtsHome.cache( home ).resolve( "x.wav" ), 20 );

		var entries = Storage.list( home );
		assertThat( entries.stream().map( Storage.Entry::kind ).toList() ).containsExactly( "engine", "voices", "ffmpeg", "cache" ).inOrder();
		assertThat( entries.get( 0 ).bytes() ).isEqualTo( 1000 );
		assertThat( entries.get( 0 ).exists() ).isTrue();
		assertThat( entries.get( 2 ).exists() ).isFalse();
		assertThat( entries.get( 2 ).bytes() ).isEqualTo( 0 );

		assertThat( Storage.clear( home, "voices" ) ).isEqualTo( 500 );
		assertThat( Files.exists( TtsHome.voices( home ) ) ).isFalse();
		assertThat( Files.exists( TtsHome.engine( home ) ) ).isTrue();
		assertThat( Storage.clear( home, "ALL" ) ).isEqualTo( 1020 );
		assertThat( Files.exists( TtsHome.engine( home ) ) ).isFalse();
		assertThat( Storage.clear( home, "all" ) ).isEqualTo( 0 );
		assertThat( Files.isDirectory( home ) ).isTrue();
	}

	@DisplayName( "an unknown kind is refused and nothing is deleted" )
	@Test
	void testUnknownKind( @TempDir Path home ) throws IOException {
		Files.writeString( home.resolve( Storage.MARKER ), "" );
		fill( TtsHome.engine( home ).resolve( "a.jar" ), 10 );
		assertThat( assertThrows( BoxRuntimeException.class, () -> Storage.clear( home, "models" ) ).getMessage() ).contains( "Unknown kind [models]" );
		assertThat( Files.exists( TtsHome.engine( home ).resolve( "a.jar" ) ) ).isTrue();
	}

	@DisplayName( "a folder that is not known to be a bx-audio home is never cleared, even when it has folders with the same names" )
	@Test
	void testForeignFolderIsSafe( @TempDir Path project ) throws IOException {
		fill( project.resolve( "cache/their-own-file.txt" ), 100 );
		fill( project.resolve( "voices/their-own-file.txt" ), 100 );
		assertThrows( BoxRuntimeException.class, () -> Storage.clear( project, "all" ) );
		assertThat( Files.exists( project.resolve( "cache/their-own-file.txt" ) ) ).isTrue();
		assertThat( Files.exists( project.resolve( "voices/their-own-file.txt" ) ) ).isTrue();
	}

	@DisplayName( "a download marks its home as ours" )
	@Test
	void testClaim( @TempDir Path home ) throws IOException {
		fill( home.resolve( "voices/joe/model.onnx" ), 10 );
		Storage.claim( home.resolve( "voices/joe/model.onnx" ) );
		assertThat( Files.exists( home.resolve( Storage.MARKER ) ) ).isTrue();
		assertThat( Storage.clear( home, "voices" ) ).isEqualTo( 10L );
	}

	@DisplayName( "a locked file does not stop the rest being deleted, and the error names it and says what to do" )
	@Test
	void testLockedFile( @TempDir Path home ) throws IOException {
		Path engine = home.resolve( "engine" );
		fill( engine.resolve( "locked.jar" ), 5 );
		fill( engine.resolve( "lib" ).resolve( "other.jar" ), 5 );
		fill( engine.resolve( "lib" ).resolve( "locked-too.jar" ), 5 );
		Storage.Deleter		deleter	= path -> {
										if ( path.getFileName().toString().startsWith( "locked" ) ) {
											throw new IOException( "The process cannot access the file because it is being used by another process" );
										}
										Files.delete( path );
									};
		BoxRuntimeException	e		= assertThrows( BoxRuntimeException.class, () -> Storage.delete( engine, deleter ) );
		assertThat( e.getMessage() ).contains( "Could not delete 2 files" );
		assertThat( e.getMessage() ).contains( "being used by another process" );
		assertThat( e.getMessage() ).contains( "Stop BoxLang" );
		assertThat( Files.exists( engine.resolve( "lib" ).resolve( "other.jar" ) ) ).isFalse();
		assertThat( Files.exists( engine.resolve( "locked.jar" ) ) ).isTrue();
		// once nothing holds them the clean finishes
		Storage.delete( engine, Files::delete );
		assertThat( Files.exists( engine ) ).isFalse();
	}

}
