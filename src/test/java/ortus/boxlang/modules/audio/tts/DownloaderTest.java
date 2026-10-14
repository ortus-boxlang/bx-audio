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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class DownloaderTest {

	private HttpServer			server;
	private final List<String>	rangeHeaders	= new CopyOnWriteArrayList<>();
	private int					requests		= 0;
	private boolean				honourRanges	= true;
	private byte[]				content;
	private String				md5;

	@BeforeEach
	void start() throws Exception {
		this.content = new byte[ 300_000 ];
		for ( int i = 0; i < this.content.length; i++ ) {
			this.content[ i ] = ( byte ) ( i * 31 + i / 7 );
		}
		Path scratch = Files.createTempFile( "dl", ".bin" );
		Files.write( scratch, this.content );
		this.md5 = Downloader.digest( scratch, "MD5" );
		Files.delete( scratch );

		this.server = HttpServer.create( new InetSocketAddress( "127.0.0.1", 0 ), 0 );
		this.server.createContext( "/file", exchange -> {
			this.requests++;
			String range = exchange.getRequestHeaders().getFirst( "Range" );
			this.rangeHeaders.add( range == null ? "" : range );
			int start = 0;
			if ( range != null && this.honourRanges ) {
				start = Integer.parseInt( range.replace( "bytes=", "" ).replace( "-", "" ) );
				exchange.sendResponseHeaders( 206, this.content.length - start );
			} else {
				exchange.sendResponseHeaders( 200, this.content.length );
			}
			try ( OutputStream out = exchange.getResponseBody() ) {
				out.write( this.content, start, this.content.length - start );
			}
		} );
		this.server.createContext( "/moved", exchange -> {
			exchange.getResponseHeaders().add( "Location", "/file" );
			exchange.sendResponseHeaders( 302, -1 );
			exchange.close();
		} );
		this.server.createContext( "/missing", exchange -> {
			byte[] body = "nope".getBytes( StandardCharsets.UTF_8 );
			exchange.sendResponseHeaders( 404, body.length );
			try ( OutputStream out = exchange.getResponseBody() ) {
				out.write( body );
			}
		} );
		this.server.start();
	}

	@AfterEach
	void stop() {
		this.server.stop( 0 );
	}

	private String url( String path ) {
		return "http://127.0.0.1:" + this.server.getAddress().getPort() + path;
	}

	@DisplayName( "A download is verified and put in place, with progress reported up to the full size" )
	@Test
	void testDownload( @TempDir Path dir ) throws Exception {
		Path		target	= dir.resolve( "nested/out.bin" );
		List<Long>	seen	= new ArrayList<>();
		Downloader.download( url( "/file" ), target, this.content.length, "MD5", this.md5, ( done, total ) -> seen.add( done ) );
		assertThat( Files.readAllBytes( target ) ).isEqualTo( this.content );
		assertThat( Files.exists( dir.resolve( "nested/out.bin.part" ) ) ).isFalse();
		assertThat( seen.get( seen.size() - 1 ) ).isEqualTo( ( long ) this.content.length );
	}

	@DisplayName( "A file that is already there and correct is not downloaded again" )
	@Test
	void testSkipsWhenPresent( @TempDir Path dir ) {
		Path target = dir.resolve( "out.bin" );
		Downloader.download( url( "/file" ), target, this.content.length, "MD5", this.md5, null );
		Downloader.download( url( "/file" ), target, this.content.length, "MD5", this.md5, null );
		assertThat( this.requests ).isEqualTo( 1 );
	}

	@DisplayName( "Redirects are followed" )
	@Test
	void testRedirect( @TempDir Path dir ) throws Exception {
		Path target = dir.resolve( "out.bin" );
		Downloader.download( url( "/moved" ), target, this.content.length, "MD5", this.md5, null );
		assertThat( Files.readAllBytes( target ) ).isEqualTo( this.content );
	}

	@DisplayName( "A wrong digest is refused, and nothing is left behind" )
	@Test
	void testWrongDigest( @TempDir Path dir ) {
		Path	target	= dir.resolve( "out.bin" );
		String	message	= assertThrows( BoxRuntimeException.class,
		    () -> Downloader.download( url( "/file" ), target, this.content.length, "MD5", "0".repeat( 32 ), null ) ).getMessage();
		assertThat( message ).contains( "not the file that was expected" );
		assertThat( Files.exists( target ) ).isFalse();
		assertThat( Files.exists( dir.resolve( "out.bin.part" ) ) ).isFalse();
	}

	@DisplayName( "A wrong size is refused" )
	@Test
	void testWrongSize( @TempDir Path dir ) {
		Path target = dir.resolve( "out.bin" );
		assertThrows( BoxRuntimeException.class, () -> Downloader.download( url( "/file" ), target, this.content.length - 5, "MD5", this.md5, null ) );
		assertThat( Files.exists( target ) ).isFalse();
	}

	@DisplayName( "An error from the server is reported with the status" )
	@Test
	void testServerError( @TempDir Path dir ) {
		String message = assertThrows( BoxRuntimeException.class,
		    () -> Downloader.download( url( "/missing" ), dir.resolve( "out.bin" ), 10, "MD5", this.md5, null ) ).getMessage();
		assertThat( message ).contains( "404" );
	}

	@DisplayName( "An interrupted download carries on from where it stopped" )
	@Test
	void testResume( @TempDir Path dir ) throws Exception {
		Path	target	= dir.resolve( "out.bin" );
		int		half	= this.content.length / 2;
		Files.write( dir.resolve( "out.bin.part" ), java.util.Arrays.copyOf( this.content, half ) );
		Downloader.download( url( "/file" ), target, this.content.length, "MD5", this.md5, null );
		assertThat( this.rangeHeaders ).containsExactly( "bytes=" + half + "-" );
		assertThat( Files.readAllBytes( target ) ).isEqualTo( this.content );
	}

	@DisplayName( "A server that ignores the range makes the download start over, and a corrupt partial file never survives" )
	@Test
	void testResumeIgnored( @TempDir Path dir ) throws Exception {
		this.honourRanges = false;
		Path target = dir.resolve( "out.bin" );
		Files.write( dir.resolve( "out.bin.part" ), new byte[] { 1, 2, 3, 4 } );
		Downloader.download( url( "/file" ), target, this.content.length, "MD5", this.md5, null );
		assertThat( Files.readAllBytes( target ) ).isEqualTo( this.content );
	}

	@DisplayName( "Cannot reach the server" )
	@Test
	void testUnreachable( @TempDir Path dir ) throws IOException {
		String dead = url( "/file" );
		this.server.stop( 0 );
		String message = assertThrows( BoxRuntimeException.class, () -> Downloader.download( dead, dir.resolve( "out.bin" ), 10, "MD5", this.md5, null ) )
		    .getMessage();
		assertThat( message ).contains( "Could not download" );
	}

}
