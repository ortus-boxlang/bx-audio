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
 * audioWaveformImage and audioSpectrogramImage as a script calls them
 */
public class ImageBifsTest extends BaseIntegrationTest {

	private Object run( String source ) {
		runtime.executeSource( source, context );
		return variables.get( Key.of( "result" ) );
	}

	@DisplayName( "Without a path the waveform is an SVG string or PNG bytes, the spectrogram a PNG or an SVG" )
	@Test
	void returns() {
		IStruct r = ( IStruct ) run( """
		                             tone = audioOsc( 'sine', 440, 1 );
		                             result = {
		                                 svg : audioWaveformImage( audio=tone, width=100, height=40 ),
		                                 png : audioWaveformImage( audio=tone, width=100, height=40, format='png' ),
		                                 spec : audioSpectrogramImage( audio=tone, width=50, height=32 ),
		                                 specSvg : audioSpectrogramImage( audio=tone, width=50, height=32, format='svg' )
		                             }
		                             """ );
		assertThat( ( String ) r.get( Key.of( "svg" ) ) ).startsWith( "<svg " );
		assertThat( ( byte[] ) r.get( Key.of( "png" ) ) ).hasLength( ( ( byte[] ) r.get( Key.of( "png" ) ) ).length );
		assertThat( ( ( byte[] ) r.get( Key.of( "png" ) ) )[ 1 ] ).isEqualTo( ( byte ) 'P' );
		assertThat( ( ( byte[] ) r.get( Key.of( "spec" ) ) )[ 1 ] ).isEqualTo( ( byte ) 'P' );
		assertThat( ( String ) r.get( Key.of( "specSvg" ) ) ).contains( "data:image/png;base64," );
	}

	@DisplayName( "With a path the picture is written, the format follows the extension, and the path is returned" )
	@Test
	void writes( @TempDir Path dir ) throws Exception {
		variables.put( Key.of( "dir" ), dir.toString() );
		IStruct r = ( IStruct ) run(
		    """
		    tone = audioOsc( 'sine', 440, 1 );
		    result = {
		        wave : audioWaveformImage( audio=tone, path=dir & '/new/folder/wave.png', width=80, height=40 ),
		        svg : audioWaveformImage( audio=tone, path=dir & '/wave.svg', width=80, height=40, color='ff00aa', background='111827' ),
		        spec : audioSpectrogramImage( audio=tone, path=dir & '/spec.png', width=40, height=32, scale='viridis' )
		    }
		    """ );
		assertThat( r.get( Key.of( "wave" ) ).toString() ).isEqualTo( dir.resolve( "new/folder/wave.png" ).toString() );
		byte[] png = Files.readAllBytes( dir.resolve( "new/folder/wave.png" ) );
		assertThat( png[ 1 ] ).isEqualTo( ( byte ) 'P' );
		assertThat( Files.readString( dir.resolve( "wave.svg" ) ) ).contains( "#ff00aa" );
		assertThat( Files.size( dir.resolve( "spec.png" ) ) ).isGreaterThan( 100L );
		try ( var files = Files.list( dir ) ) {
			assertThat( files.map( p -> p.getFileName().toString() ).filter( n -> n.startsWith( ".bxaudio" ) ).toList() ).isEmpty();
		}
	}

	@DisplayName( "Bad arguments are typed errors" )
	@Test
	void errors() {
		for ( String call : new String[] { "audioWaveformImage( audio=tone, color='red' )", "audioWaveformImage( audio=tone, style='dots' )",
		    "audioWaveformImage( audio=tone, width=2 )", "audioWaveformImage( audio=tone, format='gif' )", "audioSpectrogramImage( audio=tone, rangeDb=500 )",
		    "audioSpectrogramImage( audio=tone, scale='rainbow' )", "audioSpectrogramImage( audio=tone, height=9999 )", "audioWaveformImage( audio=42 )" } ) {
			var e = assertThrows( BoxRuntimeException.class, () -> run( "tone = audioOsc( 'sine', 440, 1 ); " + call ), call );
			assertThat( e.getType() ).isEqualTo( "bxaudio.invalidArgument" );
		}
	}

}
