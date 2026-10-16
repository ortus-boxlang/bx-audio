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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.BaseIntegrationTest;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * audioEncode, audioDecode and audioHttpResponse as a script calls them
 */
public class EncodeBifsTest extends BaseIntegrationTest {

	private Object run( String source ) {
		runtime.executeSource( source, context );
		return variables.get( Key.of( "result" ) );
	}

	@DisplayName( "audioEncode gives the bytes of a file and audioDecode reads them back, for WAV, MP3 and FLAC" )
	@Test
	void roundTrip() {
		for ( String format : new String[] { "wav", "mp3", "flac" } ) {
			IStruct r = ( IStruct ) run( "tone = audioGain( audioOsc( 'sine', 440, 1 ), -6 ); bytes = audioEncode( audio=tone, format='" + format
			    + "' ); back = audioDecode( data=bytes ); result = { size : bytes.len(), seconds : back.getDurationSeconds(), peak : back.getPeak() }" );
			assertThat( ( ( Number ) r.get( Key.of( "size" ) ) ).intValue() ).isGreaterThan( 1000 );
			assertThat( ( ( Number ) r.get( Key.of( "seconds" ) ) ).doubleValue() ).isWithin( 0.1 ).of( 1.0 );
			assertThat( ( ( Number ) r.get( Key.of( "peak" ) ) ).doubleValue() ).isWithin( 0.1 ).of( 0.5 );
		}
	}

	@DisplayName( "audioEncode of WAV equals the file audioSave writes" )
	@Test
	void sameAsFile( @TempDir Path dir ) throws Exception {
		variables.put( Key.of( "target" ), dir.resolve( "tone.wav" ).toString() );
		Object bytes = run( "tone = audioOsc( 'sine', 440, 0.5 ); audioSave( tone, target ); result = audioEncode( audio=tone, format='wav' )" );
		assertThat( ( byte[] ) bytes ).isEqualTo( Files.readAllBytes( dir.resolve( "tone.wav" ) ) );
	}

	@DisplayName( "Bad input is a typed error" )
	@Test
	void errors() {
		var e = assertThrows( BoxRuntimeException.class, () -> run( "audioEncode( audio=audioOsc( 'sine', 440, 0.1 ), format='xyz' )" ) );
		assertThat( e.getType() ).isEqualTo( "bxaudio.unsupportedFormat" );
		e = assertThrows( BoxRuntimeException.class, () -> run( "audioDecode( data='text' )" ) );
		assertThat( e.getType() ).isEqualTo( "bxaudio.invalidArgument" );
		e = assertThrows( BoxRuntimeException.class, () -> run( "audioDecode( data=charsetDecode( 'not audio at all, just words', 'utf-8' ) )" ) );
		assertThat( e.getType() ).isEqualTo( "bxaudio.unsupportedFormat" );
	}

	@DisplayName( "audioHttpResponse serves a buffer, bytes and a file with ranges and 304" )
	@Test
	void httpResponse( @TempDir Path dir ) {
		variables.put( Key.of( "target" ), dir.resolve( "tone.wav" ).toString() );
		IStruct r = ( IStruct ) run(
		    """
		    tone = audioOsc( 'sine', 440, 1 );
		    audioSave( tone, target );
		    full = audioHttpResponse( source=tone, format='mp3', bitrate=64 );
		    part = audioHttpResponse( source=target, request={ method:'GET', headers:{ range:'bytes=0-99' } } );
		    again = audioHttpResponse( source=target, request={ headers:{ 'if-none-match': part.headers.etag } } );
		    result = { fullStatus:full.status, fullType:full.headers[ 'Content-Type' ], fullBytes:full.body.len(),
		        partStatus:part.status, partBytes:part.body.len(), partRange:part.headers[ 'Content-Range' ], partType:part.headers[ 'Content-Type' ],
		        againStatus:again.status, againBytes:again.body.len() }
		    """ );
		assertThat( r.get( Key.of( "fullStatus" ) ) ).isEqualTo( 200 );
		assertThat( r.get( Key.of( "fullType" ) ) ).isEqualTo( "audio/mpeg" );
		assertThat( ( ( Number ) r.get( Key.of( "fullBytes" ) ) ).intValue() ).isGreaterThan( 1000 );
		assertThat( r.get( Key.of( "partStatus" ) ) ).isEqualTo( 206 );
		assertThat( r.get( Key.of( "partBytes" ) ) ).isEqualTo( 100 );
		assertThat( r.get( Key.of( "partRange" ) ).toString() ).startsWith( "bytes 0-99/" );
		assertThat( r.get( Key.of( "partType" ) ) ).isEqualTo( "audio/wav" );
		assertThat( r.get( Key.of( "againStatus" ) ) ).isEqualTo( 304 );
		assertThat( r.get( Key.of( "againBytes" ) ) ).isEqualTo( 0 );
	}

}
