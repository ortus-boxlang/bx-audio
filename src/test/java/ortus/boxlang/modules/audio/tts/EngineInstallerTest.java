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

import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class EngineInstallerTest {

	@DisplayName( "The engine manifest pins every jar with a size and a SHA-1" )
	@Test
	void testManifest( @TempDir Path dir ) {
		EngineInstaller engine = new EngineInstaller( dir );
		assertThat( engine.getArtifacts().size() ).isAtLeast( 8 );
		for ( EngineInstaller.Artifact a : engine.getArtifacts() ) {
			assertThat( a.sha1() ).matches( "[0-9a-f]{40}" );
			assertThat( a.size() ).isGreaterThan( 1000L );
			assertThat( a.url() ).startsWith( "https://repo1.maven.org/maven2/" );
			assertThat( a.url() ).endsWith( a.fileName() );
		}
		assertThat( engine.getArtifacts().stream().map( EngineInstaller.Artifact::artifact ) ).containsAtLeast( "onnxruntime", "chorus", "core", "g2p" );
		// ONNX Runtime is the bulk of it
		assertThat( engine.downloadSize() ).isGreaterThan( 100_000_000L );
	}

	@DisplayName( "Not installed until every jar is there at its size, and then its class path is the jars" )
	@Test
	void testInstalledState( @TempDir Path dir ) throws Exception {
		EngineInstaller engine = new EngineInstaller( dir );
		assertThat( engine.isInstalled() ).isFalse();
		assertThat( assertThrows( BoxRuntimeException.class, engine::classpath ).getMessage() ).contains( "not installed" );

		Files.createDirectories( engine.getDir() );
		for ( EngineInstaller.Artifact a : engine.getArtifacts() ) {
			try ( RandomAccessFile file = new RandomAccessFile( engine.getDir().resolve( a.fileName() ).toFile(), "rw" ) ) {
				// sparse, so a 139 MB jar costs nothing; one jar is a byte short
				file.setLength( a.artifact().equals( "gson" ) ? a.size() - 1 : a.size() );
			}
		}
		assertThat( engine.isInstalled() ).isFalse();
		try ( RandomAccessFile file = new RandomAccessFile( engine.getDir().resolve( "gson-2.13.2.jar" ).toFile(), "rw" ) ) {
			file.setLength( engine.getArtifacts().stream().filter( a -> a.artifact().equals( "gson" ) ).findFirst().get().size() );
		}
		assertThat( engine.isInstalled() ).isTrue();
		URL[] urls = engine.classpath();
		assertThat( urls ).hasLength( engine.getArtifacts().size() );
		assertThat( urls[ 0 ].toString() ).endsWith( ".jar" );

		assertThat( engine.remove() ).isTrue();
		assertThat( engine.isInstalled() ).isFalse();
		assertThat( engine.remove() ).isFalse();
	}

}
