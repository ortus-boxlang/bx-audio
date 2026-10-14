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

	@DisplayName( "audioFreq converts note names, MIDI numbers and numeric strings" )
	@Test
	void testFreq() {
		runtime.executeSource(
		    """
		    result = [ audioFreq( "A4" ), audioFreq( 69 ), audioFreq( "69" ), audioFreq( "C4" ), audioFreq( "Bb2" ), audioFreq( 81 ) ];
		    """,
		    context );
		Array values = ( Array ) variables.get( result );
		assertThat( ( Double ) values.get( 0 ) ).isWithin( 1e-9 ).of( 440.0 );
		assertThat( ( Double ) values.get( 1 ) ).isWithin( 1e-9 ).of( 440.0 );
		assertThat( ( Double ) values.get( 2 ) ).isWithin( 1e-9 ).of( 440.0 );
		assertThat( ( Double ) values.get( 3 ) ).isWithin( 1e-3 ).of( 261.6256 );
		assertThat( ( Double ) values.get( 4 ) ).isWithin( 1e-3 ).of( 116.5409 );
		assertThat( ( Double ) values.get( 5 ) ).isWithin( 1e-9 ).of( 880.0 );
	}

	@DisplayName( "The generator BIFs build sounds from BoxLang with named arguments" )
	@Test
	void testGenerators() {
		runtime.executeSource(
		    """
		    saw = audioOsc( shape="saw", freq=audioFreq( "A3" ), seconds=0.5, harmonics=8 );
		    pulse = audioOsc( shape="pulse", freq=440, seconds=0.25, width=0.125, sampleRate=22050 );
		    organ = audioOsc( shape="additive", freq=200, seconds=0.1, partials=[ [ 1, 1.0 ], [ 2, 0.5 ], [ 3, 0.25 ] ] );
		    thump = audioSweep( startHz=170, endHz=50, seconds=0.35 );
		    hiss = audioNoise( seconds=0.1, seed=3, color="pink" );
		    hiss2 = audioNoise( seconds=0.1, seed=3, color="pink" );
		    result = [
		        saw.getFrames(), pulse.getFrames(), pulse.getSampleRate(), organ.getPeak() <= 1.75, thump.getFrames(),
		        hiss.getFrames(), audioInfo( hiss ).rms, audioInfo( hiss2 ).rms == audioInfo( hiss ).rms
		    ];
		    """,
		    context );
		Array values = ( Array ) variables.get( result );
		assertThat( values.get( 0 ) ).isEqualTo( 22050 );
		// 0.25 s at 22050 Hz is 5512.5 frames, rounded up
		assertThat( values.get( 1 ) ).isEqualTo( 5513 );
		assertThat( values.get( 2 ) ).isEqualTo( 22050 );
		assertThat( values.get( 3 ) ).isEqualTo( true );
		assertThat( values.get( 4 ) ).isEqualTo( 15435 );
		assertThat( values.get( 5 ) ).isEqualTo( 4410 );
		assertThat( ( Double ) values.get( 6 ) ).isWithin( 1e-4 ).of( 0.25 );
		assertThat( values.get( 7 ) ).isEqualTo( true );
	}

	@DisplayName( "Envelope, drums and filters chain with the mixer through BoxLang" )
	@Test
	void testSynthPipeline( @TempDir Path dir ) {
		variables.put( Key.of( "target" ), dir.resolve( "beat.wav" ).toString() );
		runtime.executeSource(
		    """
		    t = audioTimeline( bpm=120 );
		    kick = audioDrum( type="kick", level=0.9 );
		    hat = audioDrum( "hat", 44100, 2, 1, 1, 0.4 );
		    pluck = audioEnvelope( audio=audioOsc( "saw", audioFreq( "C3" ), 0.5 ), attack=0.002, decay=0.15 );
		    pluck = audioFilter( pluck, "lowpass", 1200, 2 );
		    for( at in audioBeats( t, 0, 8 ) ){
		        audioAdd( timeline=t, sound=kick, at=at, track="drums" );
		        audioAdd( timeline=t, sound=hat, at=at + t.beat( 0.5 ), track="drums", pan=0.4 );
		    }
		    audioAdd( timeline=t, sound=pluck, at=0, track="bass", db=-6 );
		    audioPump( t, "bass", audioBeats( t, 0, 8 ), -6, 0.2 );
		    mix = audioSaturate( audioRender( timeline=t, tail=0.25 ) );
		    audioSave( mix, target );
		    result = audioInfo( target );
		    """,
		    context );
		IStruct info = ( IStruct ) variables.get( result );
		assertThat( info.get( Key.of( "channels" ) ) ).isEqualTo( 2 );
		// the last kick starts at beat 7 = 3.5 s and lasts 0.35 s (it ends after the last hat), plus the 0.25 s tail
		assertThat( ( Double ) info.get( Key.of( "seconds" ) ) ).isWithin( 1e-6 ).of( 4.1 );
		assertThat( ( Double ) info.get( Key.of( "peak" ) ) ).isWithin( 0.01 ).of( 0.9 );
	}

	@DisplayName( "Synth BIFs reject bad arguments with clear errors" )
	@Test
	void testSynthErrors() {
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioFreq( 'H9' )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioFreq( [ 1 ] )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioOsc( 'wobble', 440, 1 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioOsc( 'sine', 30000, 1 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioOsc( 'additive', 440, 1 )", context ) );
		assertThrows( BoxRuntimeException.class,
		    () -> runtime.executeSource( "audioOsc( shape='additive', freq=440, seconds=1, partials=[ [ 1 ] ] )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioSweep( 0, 100, 1 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioNoise( 1, 44100, 1, 'green' )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioDrum( 'cowbell' )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioDrum( 'riser' , 44100, 1, 1, 1, 1, 0 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioEnvelope( audioSilence( 1 ), -1 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioEnvelope( 'nope' )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioFilter( audioSilence( 1 ), 'comb', 100 )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioFilter( audioSilence( 1 ), 'lowpass', 99999 )", context ) );
	}

	@DisplayName( "audioMix sums voices into one sound" )
	@Test
	void testMixBif() {
		runtime.executeSource(
		    """
		    a = audioOsc( "sine", 440, 0.5 );
		    b = audioOsc( "sine", 440, 0.25 );
		    both = audioMix( [ a, b ] );
		    result = [ both.getFrames(), both.getPeak() > a.getPeak() ];
		    """,
		    context );
		Array values = ( Array ) variables.get( result );
		assertThat( values.get( 0 ) ).isEqualTo( 22050 );
		assertThat( values.get( 1 ) ).isEqualTo( true );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioMix( [] )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioMix( [ audioSilence( 1 ), 5 ] )", context ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "audioMix( [ audioSilence( 1, 1 ), audioSilence( 1, 2 ) ] )", context ) );
	}

}
