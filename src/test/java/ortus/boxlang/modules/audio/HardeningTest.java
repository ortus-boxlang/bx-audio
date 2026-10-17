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
package ortus.boxlang.modules.audio;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.util.AudioException;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Hostile and extreme input from the launch review: every one ends in a bxaudio error or a sensible result, never a raw Java exception
 */
public class HardeningTest extends BaseIntegrationTest {

	private String type( String source ) {
		BoxRuntimeException e = assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( source, context ) );
		return e.getType();
	}

	private double number( String source ) {
		runtime.executeSource( source, context );
		return ( ( Number ) variables.get( Key.of( "result" ) ) ).doubleValue();
	}

	@Test
	@DisplayName( "Numbers that are not numbers or are absurdly large are invalidArgument, not silence" )
	void badNumbers() {
		String nan = "nan = createObject( 'java', 'java.lang.Double' ).NaN; a = audioOsc( 'sine', 440, 1 ); ";
		assertThat( type( nan + "audioCompress( a, nan )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( nan + "audioLimit( a, nan )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( nan + "audioLoopify( a, nan )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( "audioGain( audioOsc( 'sine', 440, 1 ), 900 )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( "audioNormalize( audioOsc( 'sine', 440, 1 ), 1e12 )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( "audioFft( audioOsc( 'sine', 440, 1 ), 1073741824 )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( "audioLoopify( audioOsc( 'sine', 440, 1 ), 1e10 )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( "audioFreq( 200 )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
	}

	@Test
	@DisplayName( "A negative time or a bad fit is invalidArgument" )
	void negativeValues() {
		String a = "a = audioOsc( 'sine', 440, 1 ); ";
		assertThat( type( a + "audioFade( a, -1, 0 )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( a + "audioCompress( audio=a, attackMs=-1 )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( a + "audioFitTo( a, 30 )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
	}

	@Test
	@DisplayName( "A mistyped EQ key or track name is caught instead of doing nothing" )
	void typos() {
		String a = "a = audioOsc( 'sine', 440, 1 ); ";
		assertThat( type( a + "audioEq( a, [ { type: 'peak', freq: 1000, gain: 3 } ] )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( a + "audioEq( a, [ { type: 'peak', freq: 1000 } ] )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( a + "t = audioTimeline(); audioAdd( t, a, 0, 'music' ); audioTrackGain( t, 'musik', -20 ); audioRender( t )" ) )
		    .isEqualTo( AudioException.INVALID_ARGUMENT );
	}

	@Test
	@DisplayName( "The end of a slowed down sound does not fade away" )
	void stretchTail() {
		double tail = number( "s = audioStretch( audioOsc( 'sine', 440, 1 ), 0.5 ); n = s.getFrames(); "
		    + "result = audioTrim( s, ( n - 441 ) / 44100, n / 44100 ).getRms()" );
		assertThat( tail ).isGreaterThan( 0.3 );
	}

	@Test
	@DisplayName( "A limiter protects the very first samples" )
	void limiterStart() {
		// the ceiling holds for the true peak and the hit is not left several dB under it
		double truePeak = number( "result = audioLoudness( audioLimit( audioDrum( type='snare', level=1 ), -6 ) ).truePeakDb" );
		assertThat( truePeak ).isAtMost( -5.95 );
		assertThat( truePeak ).isAtLeast( -6.6 );
	}

	@Test
	@DisplayName( "A damaged FLAC is corruptAudio, not silently wrong audio" )
	void flacChecksum( @TempDir Path dir ) throws Exception {
		Path flac = dir.resolve( "tone.flac" );
		variables.put( Key.of( "flac" ), flac.toString() );
		runtime.executeSource( "audioSave( audioOsc( 'sine', 440, 1 ), flac )", context );
		byte[] bytes = Files.readAllBytes( flac );
		bytes[ bytes.length / 2 ] ^= 0x55;
		Path bad = dir.resolve( "bad.flac" );
		Files.write( bad, bytes );
		variables.put( Key.of( "bad" ), bad.toString() );
		assertThat( type( "audioLoad( bad )" ) ).isEqualTo( AudioException.CORRUPT_AUDIO );
		Path cut = dir.resolve( "cut.flac" );
		Files.write( cut, java.util.Arrays.copyOf( Files.readAllBytes( flac ), bytes.length / 2 ) );
		variables.put( Key.of( "cut" ), cut.toString() );
		assertThat( type( "audioLoad( cut )" ) ).isEqualTo( AudioException.CORRUPT_AUDIO );
	}

	@Test
	@DisplayName( "A tiny FLAC is written with a valid block size" )
	void tinyFlac( @TempDir Path dir ) throws Exception {
		variables.put( Key.of( "tiny" ), dir.resolve( "tiny.flac" ).toString() );
		assertThat( number( "audioSave( audioTrim( audioOsc( 'sine', 440, 1 ), 0, 0.0001 ), tiny ); result = audioLoad( tiny ).getFrames()" ) )
		    .isGreaterThan( 0 );
	}

	@Test
	@DisplayName( "Tag fields with huge sizes do not throw raw Java exceptions" )
	void hostileTags( @TempDir Path dir ) throws Exception {
		// fLaC, then a last Vorbis comment block whose vendor length claims 0x7FFFFFFF
		byte[]	flac	= new byte[] { 'f', 'L', 'a', 'C', ( byte ) 0x84, 0, 0, 12, ( byte ) 0xFF, ( byte ) 0xFF, ( byte ) 0xFF, 0x7F, 0, 0, 0, 0, 0, 0, 0, 0 };
		Path	file	= dir.resolve( "hostile.flac" );
		Files.write( file, flac );
		variables.put( Key.of( "file" ), file.toString() );
		try {
			runtime.executeSource( "audioTags( file )", context );
		} catch ( BoxRuntimeException e ) {
			assertThat( e.getType() ).startsWith( "bxaudio." );
		}
		// an ID3v2.3 tag with a frame of 0x7FFFFFFF bytes
		byte[] id3 = new byte[ 40 ];
		System.arraycopy( new byte[] { 'I', 'D', '3', 3, 0, 0, 0, 0, 0, 30, 'T', 'I', 'T', '2', 0x7F, ( byte ) 0xFF, ( byte ) 0xFF, ( byte ) 0xFF, 0, 0 }, 0,
		    id3, 0, 20 );
		Path mp3 = dir.resolve( "hostile.mp3" );
		Files.write( mp3, id3 );
		variables.put( Key.of( "mp3" ), mp3.toString() );
		try {
			runtime.executeSource( "audioTags( mp3 )", context );
		} catch ( BoxRuntimeException e ) {
			assertThat( e.getType() ).startsWith( "bxaudio." );
		}
	}

	@Test
	@DisplayName( "Text the voices cannot say is refused before anything is downloaded" )
	void unsayableText() {
		assertThat( type( "audioSpeak( text='...', voice='kristin', autoInstall=false )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( "audioSpeak( text='hello', voice='kristin', speed=9, autoInstall=false )" ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
	}
}
