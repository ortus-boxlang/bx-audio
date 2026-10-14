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
import ortus.boxlang.modules.audio.codec.WavCodec;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class AudioBifsTest extends BaseIntegrationTest {

	static Key result = new Key( "result" );

	@DisplayName( "audioSilence creates a buffer with defaults of stereo 44.1kHz" )
	@Test
	void testSilence() {
		// Read through BoxLang, not a Java cast: when the module is also loaded by another test, its classes live in a second class loader
		runtime.executeSource(
		    """
		    a = audioSilence( 2 );
		    result = [ a.getChannels(), a.getSampleRate(), a.getDurationSeconds() ];
		    """,
		    context );
		Array values = ( Array ) variables.get( result );
		assertThat( values.get( 0 ) ).isEqualTo( 2 );
		assertThat( values.get( 1 ) ).isEqualTo( 44100 );
		assertThat( values.get( 2 ) ).isEqualTo( 2.0 );
	}

	@DisplayName( "audioSilence takes channels and sampleRate, and its methods are callable from BoxLang" )
	@Test
	void testSilenceArguments() {
		runtime.executeSource( "result = audioSilence( seconds=0.5, channels=1, sampleRate=22050 ).getFrames()", context );
		assertThat( variables.get( result ) ).isEqualTo( 11025 );
	}

	@DisplayName( "audioSave then audioLoad round trips and audioInfo describes it" )
	@Test
	void testSaveLoadInfo( @TempDir Path dir ) {
		variables.put( Key.of( "target" ), dir.resolve( "nested/out.wav" ).toString() );
		runtime.executeSource(
		    """
		    a = audioSilence( 1, 2, 44100 );
		    saved = audioSave( a, target );
		    b = audioLoad( target );
		    result = audioInfo( b );
		    fromPath = audioInfo( target );
		    """,
		    context );
		IStruct info = ( IStruct ) variables.get( result );
		assertThat( info.get( Key.of( "channels" ) ) ).isEqualTo( 2 );
		assertThat( info.get( Key.of( "sampleRate" ) ) ).isEqualTo( 44100 );
		assertThat( info.get( Key.of( "frames" ) ) ).isEqualTo( 44100 );
		assertThat( info.get( Key.of( "seconds" ) ) ).isEqualTo( 1.0 );
		assertThat( info.get( Key.of( "peak" ) ) ).isEqualTo( 0.0 );
		assertThat( variables.get( Key.of( "saved" ) ) ).isEqualTo( dir.resolve( "nested/out.wav" ).toString() );
		assertThat( Files.exists( dir.resolve( "nested/out.wav" ) ) ).isTrue();
		assertThat( ( ( IStruct ) variables.get( Key.of( "fromPath" ) ) ).get( Key.of( "frames" ) ) ).isEqualTo( 44100 );
	}

	@DisplayName( "audioSave honours the bit depth" )
	@Test
	void testBitDepth( @TempDir Path dir ) throws Exception {
		Path file = dir.resolve( "deep.wav" );
		variables.put( Key.of( "target" ), file.toString() );
		runtime.executeSource( "audioSave( audioSilence( 1, 1, 8000 ), target, 24 )", context );
		assertThat( Files.size( file ) ).isEqualTo( 44 + 8000 * 3 );
	}

	@DisplayName( "Errors are clear for bad input" )
	@Test
	void testErrors( @TempDir Path dir ) {
		variables.put( Key.of( "missing" ), dir.resolve( "nope.wav" ).toString() );
		variables.put( Key.of( "target" ), dir.resolve( "x.wav" ).toString() );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioLoad( missing )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioInfo( missing )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioInfo( 42 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioSave( 'not audio', target )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioSave( audioSilence( 1 ), target, 12 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioSilence( -1 )", context ) );
	}

	@DisplayName( "The DSP BIFs chain together through BoxLang" )
	@Test
	void testDspPipeline( @TempDir Path dir ) {
		// A 0.5 second 440 Hz mono sine at 22.05kHz, as a TTS voice would arrive
		int		frames	= 11025;
		float[]	sine	= new float[ frames ];
		for ( int i = 0; i < frames; i++ ) {
			sine[ i ] = ( float ) ( 0.25 * Math.sin( 2 * Math.PI * 440 * i / 22050 ) );
		}
		Path source = dir.resolve( "voice.wav" );
		WavCodec.write( new AudioBuffer( new float[][] { sine }, 22050 ), source, 16 );
		variables.put( Key.of( "source" ), source.toString() );
		variables.put( Key.of( "target" ), dir.resolve( "mixdown.wav" ).toString() );

		runtime.executeSource(
		    """
		    voice = audioLoad( source );
		    voice = audioResample( voice, 44100 );
		    voice = audioChannels( voice, 2 );
		    voice = audioNormalize( voice, 0 );
		    voice = audioGain( voice, -6 );
		    voice = audioFade( voice, 0.05, 0.1, "smooth" );
		    voice = audioPan( voice, -0.5 );
		    voice = audioTrim( voice, 0.1 );
		    gap = audioSilence( 0.25, 2, 44100 );
		    full = audioConcat( [ voice, gap, audioReverse( voice ) ] );
		    audioSave( full, target, 24 );
		    result = audioInfo( target );
		    """,
		    context );
		IStruct info = ( IStruct ) variables.get( result );
		assertThat( info.get( Key.of( "channels" ) ) ).isEqualTo( 2 );
		assertThat( info.get( Key.of( "sampleRate" ) ) ).isEqualTo( 44100 );
		// 0.5 s - 0.1 s trimmed = 0.4 s, twice, plus the 0.25 s gap
		assertThat( ( Double ) info.get( Key.of( "seconds" ) ) ).isWithin( 1e-9 ).of( 1.05 );
		// 0 dB normalize then -6 dB, then a pan that leaves the left side untouched, so the peak is about 0.5
		assertThat( ( Double ) info.get( Key.of( "peak" ) ) ).isWithin( 0.02 ).of( 0.5 );
	}

	@DisplayName( "DSP BIFs reject bad arguments with clear errors" )
	@Test
	void testDspErrors() {
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioGain( 'nope', -6 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioFade( audioSilence( 1 ), 0, 0, 'wobbly' )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioConcat( [ audioSilence( 1 ), 42 ] )", context ) );
		assertThrows( BoxRuntimeException.class,
		    () -> runtime.executeSource( "audioConcat( [ audioSilence( 1, 1 ), audioSilence( 1, 2 ) ] )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioTrim( audioSilence( 1 ), 0.8, 0.2 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioPan( audioSilence( 1 ), 3 )", context ) );
	}

	@DisplayName( "A timeline mixes a beat grid, ducks music under a voice and renders through BoxLang" )
	@Test
	void testTimelineEndToEnd( @TempDir Path dir ) {
		// a 440 Hz tone at 0.5 amplitude, 1 second long
		int		frames	= 22050;
		float[]	sine	= new float[ frames ];
		for ( int i = 0; i < frames; i++ ) {
			sine[ i ] = ( float ) ( 0.5 * Math.sin( 2 * Math.PI * 440 * i / 22050 ) );
		}
		Path voice = dir.resolve( "voice.wav" );
		WavCodec.write( new AudioBuffer( new float[][] { sine }, 22050 ), voice, 16 );
		variables.put( Key.of( "voicePath" ), voice.toString() );
		variables.put( Key.of( "target" ), dir.resolve( "mix.wav" ).toString() );

		runtime.executeSource(
		    """
		    t = audioTimeline( bpm=120, beatsPerBar=4 );
		    voice = audioLoad( voicePath );
		    clickAt = audioBeats( t, 0, 8 );
		    clickCount = clickAt.len();
		    click = audioTrim( audioResample( voice, 44100 ), 0, 0.05 );
		    for( at in clickAt ){
		        audioAdd( timeline=t, sound=click, at=at, track="drums", db=-6, pan=0.3 );
		    }
		    audioAdd( timeline=t, sound=voice, at=t.bar( 1 ), track="voice" );
		    audioAdd( timeline=t, sound=audioReverse( audioResample( voice, 44100 ) ), at=0, track="music", db=-12 );
		    audioPump( t, "music", clickAt, -4, 0.1 );
		    audioDuck( t, "music", "voice", -9 );
		    audioTrackGain( t, "drums", -3 );
		    mix = audioRender( timeline=t, tail=0.5 );
		    mix = audioSaturate( audioFade( mix, 0, 0.5, "smooth" ), 2.2, 0.9 );
		    audioSave( mix, target );
		    result = audioInfo( target );
		    names = t.getTrackNames();
		    secondBeat = t.beat( 1 );
		    """,
		    context );
		IStruct info = ( IStruct ) variables.get( result );
		assertThat( variables.get( Key.of( "clickCount" ) ) ).isEqualTo( 8 );
		assertThat( variables.get( Key.of( "secondBeat" ) ) ).isEqualTo( 0.5 );
		assertThat( info.get( Key.of( "channels" ) ) ).isEqualTo( 2 );
		assertThat( info.get( Key.of( "sampleRate" ) ) ).isEqualTo( 44100 );
		// the last click starts at beat 7 (3.5 s) and the voice ends at 2 s + 1 s, so the music (1 s) and clicks end at 3.55 s, plus a 0.5 s tail
		assertThat( ( Double ) info.get( Key.of( "seconds" ) ) ).isWithin( 1e-6 ).of( 4.05 );
		assertThat( ( Double ) info.get( Key.of( "peak" ) ) ).isWithin( 0.01 ).of( 0.9 );
	}

	@DisplayName( "Timeline BIFs reject bad arguments with clear errors" )
	@Test
	void testTimelineErrors() {
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioTimeline( bpm=0 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioAdd( 'nope', audioSilence( 1 ) )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioAdd( audioTimeline(), 42 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioRender( audioSilence( 1 ) )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioBeats( audioTimeline(), 0, 8, 0 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioBeats( audioTimeline(), 0, 1000000000 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioPump( audioTimeline(), 'x', [ 0 ], 5 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioDuck( audioTimeline(), 'a', 'b' )", context ) );
	}

	@DisplayName( "audioBeats steps by the requested amount without drift" )
	@Test
	void testBeats() {
		runtime.executeSource(
		    """
		    t = audioTimeline( bpm=120 );
		    whole = audioBeats( t, 0, 4 );
		    eighths = audioBeats( t, 1, 3, 0.5 );
		    tenths = audioBeats( t, 0, 1, 0.1 );
		    result = [ whole, eighths, tenths.len() ];
		    """,
		    context );
		Array values = ( Array ) variables.get( result );
		assertThat( ( ( Array ) values.get( 0 ) ).toArray() ).asList().containsExactly( 0.0, 0.5, 1.0, 1.5 ).inOrder();
		assertThat( ( ( Array ) values.get( 1 ) ).toArray() ).asList().containsExactly( 0.5, 0.75, 1.0, 1.25 ).inOrder();
		assertThat( values.get( 2 ) ).isEqualTo( 10 );
	}

}
