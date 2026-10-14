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

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import ortus.boxlang.modules.audio.tts.VoiceInfo.VoiceFile;

public class VoiceStoreTest {

	@DisplayName( "A voice is installed into the folder layout the engine expects, found installed afterwards, and removed again" )
	@Test
	void testInstallAndRemove( @TempDir Path dir ) throws Exception {
		byte[]	model	= new byte[ 20_000 ];
		byte[]	config	= "{\"audio\":{\"sample_rate\":22050}}".getBytes();
		for ( int i = 0; i < model.length; i++ ) {
			model[ i ] = ( byte ) ( i % 251 );
		}
		Path scratch = dir.resolve( "scratch" );
		Files.write( scratch, model );
		String modelMd5 = Downloader.digest( scratch, "MD5" );
		Files.write( scratch, config );
		String			configMd5	= Downloader.digest( scratch, "MD5" );

		AtomicInteger	hits		= new AtomicInteger();
		HttpServer		server		= HttpServer.create( new InetSocketAddress( "127.0.0.1", 0 ), 0 );
		server.createContext( "/v/", exchange -> {
			hits.incrementAndGet();
			byte[] body = exchange.getRequestURI().getPath().endsWith( ".json" ) ? config : model;
			exchange.sendResponseHeaders( 200, body.length );
			try ( OutputStream out = exchange.getResponseBody() ) {
				out.write( body );
			}
		} );
		server.start();
		try {
			VoiceInfo	voice	= new VoiceInfo( "xx_XX-test-low", "test", "xx_XX", "low", "CC0-1.0", "CC0", false, "",
			    new VoiceFile( "v/xx_XX-test-low.onnx", model.length, modelMd5 ), new VoiceFile( "v/xx_XX-test-low.onnx.json", config.length, configMd5 ) );
			VoiceStore	store	= new VoiceStore( dir.resolve( "voices" ), "http://127.0.0.1:" + server.getAddress().getPort() );
			assertThat( store.isInstalled( voice ) ).isFalse();

			store.install( voice, null );
			assertThat( store.isInstalled( voice ) ).isTrue();
			assertThat( store.modelFile( voice ).toString() ).endsWith( "voices/xx_XX-test-low/xx_XX-test-low.onnx".replace( "/", java.io.File.separator ) );
			assertThat( Files.readAllBytes( store.modelFile( voice ) ) ).isEqualTo( model );
			assertThat( Files.readAllBytes( store.configFile( voice ) ) ).isEqualTo( config );

			// installing again fetches nothing
			int before = hits.get();
			store.install( voice, null );
			assertThat( hits.get() ).isEqualTo( before );

			// a truncated model means it is not installed
			Files.write( store.modelFile( voice ), new byte[ 10 ] );
			assertThat( store.isInstalled( voice ) ).isFalse();
			store.install( voice, null );
			assertThat( store.isInstalled( voice ) ).isTrue();

			assertThat( store.remove( voice ) ).isTrue();
			assertThat( store.isInstalled( voice ) ).isFalse();
			assertThat( Files.exists( dir.resolve( "voices/xx_XX-test-low" ) ) ).isFalse();
			assertThat( store.remove( voice ) ).isFalse();
		} finally {
			server.stop( 0 );
		}
	}

}
