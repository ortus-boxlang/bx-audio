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
package ortus.boxlang.modules.audio.web;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpAudioTest {

	private static final HttpAudio.Options OPTIONS = new HttpAudio.Options( "audio/mpeg", 3600, "", false );

	private static byte[] data( int n ) {
		byte[] b = new byte[ n ];
		for ( int i = 0; i < n; i++ ) {
			b[ i ] = ( byte ) i;
		}
		return b;
	}

	@Test
	@DisplayName( "A plain request gets 200 with the type, length, ETag and Accept-Ranges" )
	void plain() {
		var r = HttpAudio.respond( "GET", Map.of(), HttpAudio.of( data( 1000 ) ), OPTIONS );
		assertThat( r.status() ).isEqualTo( 200 );
		assertThat( r.body() ).hasLength( 1000 );
		assertThat( r.headers().get( "Content-Length" ) ).isEqualTo( "1000" );
		assertThat( r.headers().get( "Content-Type" ) ).isEqualTo( "audio/mpeg" );
		assertThat( r.headers().get( "Accept-Ranges" ) ).isEqualTo( "bytes" );
		assertThat( r.headers().get( "ETag" ) ).matches( "\"[0-9a-f]{32}\"" );
		assertThat( r.headers().get( "Cache-Control" ) ).isEqualTo( "public, max-age=3600" );
	}

	@Test
	@DisplayName( "A range gets 206 with only those bytes, for start-end, start- and the last N bytes" )
	void ranges() {
		var	content	= HttpAudio.of( data( 1000 ) );
		var	r		= HttpAudio.respond( "GET", Map.of( "range", "bytes=10-19" ), content, OPTIONS );
		assertThat( r.status() ).isEqualTo( 206 );
		assertThat( r.body() ).isEqualTo( java.util.Arrays.copyOfRange( data( 1000 ), 10, 20 ) );
		assertThat( r.headers().get( "Content-Range" ) ).isEqualTo( "bytes 10-19/1000" );
		assertThat( r.headers().get( "Content-Length" ) ).isEqualTo( "10" );

		r = HttpAudio.respond( "GET", Map.of( "range", "bytes=990-" ), content, OPTIONS );
		assertThat( r.headers().get( "Content-Range" ) ).isEqualTo( "bytes 990-999/1000" );
		r = HttpAudio.respond( "GET", Map.of( "range", "bytes=-5" ), content, OPTIONS );
		assertThat( r.headers().get( "Content-Range" ) ).isEqualTo( "bytes 995-999/1000" );
		// an end past the file is cut to the file
		r = HttpAudio.respond( "GET", Map.of( "range", "bytes=900-5000" ), content, OPTIONS );
		assertThat( r.headers().get( "Content-Range" ) ).isEqualTo( "bytes 900-999/1000" );
	}

	@Test
	@DisplayName( "A range past the end gets 416, and nonsense or several ranges are ignored" )
	void badRanges() {
		var	content	= HttpAudio.of( data( 100 ) );
		var	r		= HttpAudio.respond( "GET", Map.of( "range", "bytes=100-" ), content, OPTIONS );
		assertThat( r.status() ).isEqualTo( 416 );
		assertThat( r.headers().get( "Content-Range" ) ).isEqualTo( "bytes */100" );
		assertThat( r.body() ).hasLength( 0 );
		assertThat( HttpAudio.respond( "GET", Map.of( "range", "bytes=5-2" ), content, OPTIONS ).status() ).isEqualTo( 416 );
		// several ranges are allowed to be answered with the whole file
		assertThat( HttpAudio.respond( "GET", Map.of( "range", "bytes=0-1,5-6" ), content, OPTIONS ).status() ).isEqualTo( 200 );
		// a unit we do not know is ignored
		assertThat( HttpAudio.respond( "GET", Map.of( "range", "items=0-1" ), content, OPTIONS ).status() ).isEqualTo( 200 );
	}

	@Test
	@DisplayName( "A matching If-None-Match gets 304 with no body, a different one gets the file" )
	void notModified() {
		var	content	= HttpAudio.of( data( 100 ) );
		var	etag	= HttpAudio.respond( "GET", Map.of(), content, OPTIONS ).headers().get( "ETag" );
		var	r		= HttpAudio.respond( "GET", Map.of( "if-none-match", etag ), content, OPTIONS );
		assertThat( r.status() ).isEqualTo( 304 );
		assertThat( r.body() ).hasLength( 0 );
		assertThat( r.headers().get( "ETag" ) ).isEqualTo( etag );
		assertThat( HttpAudio.respond( "GET", Map.of( "if-none-match", "W/" + etag + ", \"other\"" ), content, OPTIONS ).status() ).isEqualTo( 304 );
		assertThat( HttpAudio.respond( "GET", Map.of( "if-none-match", "*" ), content, OPTIONS ).status() ).isEqualTo( 304 );
		assertThat( HttpAudio.respond( "GET", Map.of( "if-none-match", "\"other\"" ), content, OPTIONS ).status() ).isEqualTo( 200 );
	}

	@Test
	@DisplayName( "If-Range lets the range through only when the file is the same" )
	void ifRange() {
		var	content	= HttpAudio.of( data( 100 ) );
		var	etag	= HttpAudio.respond( "GET", Map.of(), content, OPTIONS ).headers().get( "ETag" );
		assertThat( HttpAudio.respond( "GET", Map.of( "range", "bytes=0-9", "if-range", etag ), content, OPTIONS ).status() ).isEqualTo( 206 );
		var changed = HttpAudio.respond( "GET", Map.of( "range", "bytes=0-9", "if-range", "\"old\"" ), content, OPTIONS );
		assertThat( changed.status() ).isEqualTo( 200 );
		assertThat( changed.body() ).hasLength( 100 );
	}

	@Test
	@DisplayName( "HEAD gets the headers of the answer without a body, and other methods get 405" )
	void head() {
		var	content	= HttpAudio.of( data( 100 ) );
		var	r		= HttpAudio.respond( "HEAD", Map.of(), content, OPTIONS );
		assertThat( r.status() ).isEqualTo( 200 );
		assertThat( r.body() ).hasLength( 0 );
		assertThat( r.headers().get( "Content-Length" ) ).isEqualTo( "100" );
		r = HttpAudio.respond( "HEAD", Map.of( "range", "bytes=0-9" ), content, OPTIONS );
		assertThat( r.status() ).isEqualTo( 206 );
		assertThat( r.body() ).hasLength( 0 );
		assertThat( r.headers().get( "Content-Length" ) ).isEqualTo( "10" );
		var post = HttpAudio.respond( "POST", Map.of(), content, OPTIONS );
		assertThat( post.status() ).isEqualTo( 405 );
		assertThat( post.headers().get( "Allow" ) ).isEqualTo( "GET, HEAD" );
	}

	@Test
	@DisplayName( "A file on disk is read only where asked, has a weak ETag and a Last-Modified date" )
	void onDisk( @TempDir Path dir ) throws Exception {
		Path file = dir.resolve( "long.bin" );
		Files.write( file, data( 5000 ) );
		var	content	= HttpAudio.of( file );
		var	r		= HttpAudio.respond( "GET", Map.of( "range", "bytes=4000-4009" ), content, OPTIONS );
		assertThat( r.status() ).isEqualTo( 206 );
		assertThat( r.body() ).isEqualTo( java.util.Arrays.copyOfRange( data( 5000 ), 4000, 4010 ) );
		assertThat( r.headers().get( "ETag" ) ).startsWith( "W/\"" );
		assertThat( r.headers().get( "Last-Modified" ) ).endsWith( "GMT" );
		// a weak tag is never strong enough for If-Range: the whole file is sent
		assertThat( HttpAudio.respond( "GET", Map.of( "range", "bytes=0-9", "if-range", r.headers().get( "ETag" ) ), content, OPTIONS ).status() )
		    .isEqualTo( 200 );
		// ... but is for If-None-Match
		assertThat( HttpAudio.respond( "GET", Map.of( "if-none-match", r.headers().get( "ETag" ) ), content, OPTIONS ).status() ).isEqualTo( 304 );
	}

	@Test
	@DisplayName( "Cache-Control and Content-Disposition follow the options" )
	void options() {
		var	content	= HttpAudio.of( data( 10 ) );
		var	r		= HttpAudio.respond( "GET", Map.of(), content, new HttpAudio.Options( "audio/wav", 0, "Söng \"1\".wav", true ) );
		assertThat( r.headers().get( "Cache-Control" ) ).isEqualTo( "no-cache" );
		assertThat( r.headers().get( "Content-Disposition" ) ).isEqualTo( "attachment; filename=\"S_ng 1.wav\"; filename*=UTF-8''S%C3%B6ng%201.wav" );
		assertThat( HttpAudio.respond( "GET", Map.of(), content, new HttpAudio.Options( "audio/wav", -1, "", false ) ).headers().get( "Cache-Control" ) )
		    .isEqualTo( "no-store" );
	}

	@Test
	@DisplayName( "Encoded audio is recognised from its first bytes" )
	void sniff() {
		assertThat( HttpAudio.sniff( "RIFF....WAVEfmt ".getBytes() ) ).isEqualTo( "wav" );
		assertThat( HttpAudio.sniff( "fLaC............".getBytes() ) ).isEqualTo( "flac" );
		assertThat( HttpAudio.sniff( "OggS............".getBytes() ) ).isEqualTo( "ogg" );
		assertThat( HttpAudio.sniff( "ID3\u0004............".getBytes() ) ).isEqualTo( "mp3" );
		assertThat( HttpAudio.sniff( "nothing audio here".getBytes() ) ).isNull();
	}

}
