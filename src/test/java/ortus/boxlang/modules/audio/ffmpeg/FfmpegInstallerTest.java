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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class FfmpegInstallerTest {

	@DisplayName( "every supported platform has pinned, checksummed downloads and the programs they unpack to" )
	@Test
	void testManifest( @TempDir Path dir ) {
		for ( String platform : List.of( "linux-amd64", "linux-arm64", "macos-amd64", "macos-arm64", "windows-amd64" ) ) {
			FfmpegInstaller installer = new FfmpegInstaller( dir, platform );
			assertThat( installer.isSupported() ).isTrue();
			assertThat( installer.downloadSize() ).isGreaterThan( 20_000_000L );
			assertThat( installer.getVersion() ).matches( "\\d+\\.\\d+(\\.\\d+)?" );
			assertThat( installer.ffmpegPath().getFileName().toString() ).isEqualTo( platform.startsWith( "windows" ) ? "ffmpeg.exe" : "ffmpeg" );
			assertThat( installer.getDir().getFileName().toString() ).isEqualTo( installer.getVersion() );
			assertThat( installer.isInstalled() ).isFalse();
		}
		assertThat( new FfmpegInstaller( dir, "plan9-mips" ).isSupported() ).isFalse();
		assertThat( FfmpegInstaller.platformKey() ).matches( "[a-z0-9]+-[a-z0-9_]+" );
	}

	@DisplayName( "an unsupported platform is refused with advice" )
	@Test
	void testUnsupported( @TempDir Path dir ) {
		assertThat( assertThrows( BoxRuntimeException.class, () -> new FfmpegInstaller( dir, "plan9-mips" ).install( null ) ).getMessage() )
		    .contains( "BX_AUDIO_FFMPEG" );
	}

	private static byte[] zip( String entry, String content ) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try ( ZipOutputStream zip = new ZipOutputStream( out ) ) {
			zip.putNextEntry( new ZipEntry( entry ) );
			zip.write( content.getBytes( StandardCharsets.UTF_8 ) );
			zip.closeEntry();
		}
		return out.toByteArray();
	}

	private static String sha256( byte[] data ) throws Exception {
		return HexFormat.of().formatHex( MessageDigest.getInstance( "SHA-256" ).digest( data ) );
	}

	@DisplayName( "install downloads, verifies and unpacks the programs, and refuses a download whose checksum is wrong" )
	@Test
	void testInstallFromLocalServer( @TempDir Path dir ) throws Exception {
		byte[]		first	= zip( "bin/ffmpeg", "program one" );
		byte[]		second	= zip( "ffprobe", "program two" );
		HttpServer	server	= HttpServer.create( new InetSocketAddress( "127.0.0.1", 0 ), 0 );
		server.createContext( "/a.zip", exchange -> {
			exchange.sendResponseHeaders( 200, first.length );
			exchange.getResponseBody().write( first );
			exchange.close();
		} );
		server.createContext( "/b.zip", exchange -> {
			exchange.sendResponseHeaders( 200, second.length );
			exchange.getResponseBody().write( second );
			exchange.close();
		} );
		server.start();
		try {
			String				base		= "http://127.0.0.1:" + server.getAddress().getPort();
			Map<String, Object>	manifest	= Map.of( "version", "1.2.3", "platforms", Map.of( "linux-amd64", Map.of( "archives", List.of(
			    Map.of( "url", base + "/a.zip", "size", first.length, "sha256", sha256( first ), "extract", Map.of( "ffmpeg", "bin/ffmpeg" ) ),
			    Map.of( "url", base + "/b.zip", "size", second.length, "sha256", sha256( second ), "extract", Map.of( "ffprobe", "ffprobe" ) ) ) ) ) );
			FfmpegInstaller		installer	= new FfmpegInstaller( dir, "linux-amd64", manifest );
			assertThat( installer.isInstalled() ).isFalse();
			StringBuilder seen = new StringBuilder();
			installer.install( ( name, done, total ) -> seen.append( name ).append( ' ' ) );
			assertThat( installer.isInstalled() ).isTrue();
			assertThat( Files.readString( installer.ffmpegPath() ) ).isEqualTo( "program one" );
			assertThat( Files.readString( installer.ffprobePath() ) ).isEqualTo( "program two" );
			assertThat( Files.isExecutable( installer.ffmpegPath() ) ).isTrue();
			assertThat( seen.toString() ).contains( "ffmpeg" );
			assertThat( Files.exists( installer.getDir().resolve( "downloads" ) ) ).isFalse();
			assertThat( installer.remove() ).isTrue();
			assertThat( installer.isInstalled() ).isFalse();

			Map<String, Object>	wrong	= Map.of( "version", "1.2.3", "platforms", Map.of( "linux-amd64", Map.of( "archives", List.of(
			    Map.of( "url", base + "/a.zip", "size", first.length, "sha256", "0".repeat( 64 ), "extract", Map.of( "ffmpeg", "bin/ffmpeg" ) ) ) ) ) );
			FfmpegInstaller		bad		= new FfmpegInstaller( dir, "linux-amd64", wrong );
			assertThat( assertThrows( BoxRuntimeException.class, () -> bad.install( null ) ).getMessage() ).contains( "not the file that was expected" );
			assertThat( Files.exists( bad.ffmpegPath() ) ).isFalse();
		} finally {
			server.stop( 0 );
		}
	}

}
