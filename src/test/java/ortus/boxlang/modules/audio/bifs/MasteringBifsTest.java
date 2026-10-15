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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.BaseIntegrationTest;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * The loudness, dynamics, tempo and analysis BIFs as a script calls them
 */
public class MasteringBifsTest extends BaseIntegrationTest {

	private Object run( String source ) {
		runtime.executeSource( source, context );
		return variables.get( Key.of( "result" ) );
	}

	private String message( String source ) {
		return assertThrows( BoxRuntimeException.class, () -> run( source ) ).getMessage();
	}

	@DisplayName( "audioLoudness reports LUFS, and audioMaster and audioNormalizeLoudness reach a target" )
	@Test
	void testLoudnessAndMaster() {
		IStruct r = ( IStruct ) run( "result = audioLoudness( audioGain( audioOsc( 'sine', 997, 10 ), -23 ) )" );
		// A mono sine at -23 dBFS peak is 3 LU below the same sine in both channels
		assertThat( ( ( Number ) r.get( Key.of( "integrated" ) ) ).doubleValue() ).isWithin( 0.3 ).of( -26.0 );
		assertThat( r.keySet().stream().map( Key::getName ).toList() ).containsAtLeast( "integrated", "range", "truePeak", "samplePeak", "momentaryMax",
		    "shortTermMax" );
		String source = "a = audioPan( audioGain( audioNoise( seconds=8, color='pink' ), -30 ), 0 ); ";
		assertThat( ( ( Number ) run( source + "result = audioLoudness( audioMaster( a ) ).integrated" ) ).doubleValue() ).isWithin( 0.5 ).of( -14.0 );
		assertThat( ( ( Number ) run( source + "result = audioLoudness( audioMaster( a, 'podcast' ) ).integrated" ) ).doubleValue() ).isWithin( 0.5 )
		    .of( -16.0 );
		assertThat( ( ( Number ) run( source + "result = audioLoudness( audioNormalizeLoudness( a, -20 ) ).integrated" ) ).doubleValue() )
		    .isWithin( 0.5 ).of( -20.0 );
		assertThat( ( ( Number ) run( source + "result = audioLoudness( audioMaster( audio=a, glue=true ) ).truePeak" ) ).doubleValue() ).isAtMost( -0.9 );
		assertThat( message( "result = audioMaster( audioNoise( seconds=1 ), 'loud' )" ) ).contains( "Unknown loudness target [loud]" );
	}

	@DisplayName( "compress, limit and EQ change the level the way their arguments say" )
	@Test
	void testDynamicsAndEq() {
		String tone = "t = audioGain( audioOsc( 'sine', 1000, 2 ), -6 ); ";
		assertThat( ( ( Number ) run( tone + "result = audioLimit( t, -12 ).getPeak()" ) ).doubleValue() ).isAtMost( Math.pow( 10, -12 / 20.0 ) + 1e-6 );
		assertThat( ( ( Number ) run( tone + "result = audioCompress( audio=t, thresholdDb=-20, ratio=10, makeupDb=0 ).getRms()" ) ).doubleValue() )
		    .isLessThan( ( ( Number ) run( tone + "result = t.getRms()" ) ).doubleValue() );
		assertThat( ( ( Number ) run( tone + "result = audioCompress( audio=t, makeupDb=0 ).getFrames()" ) ).intValue() ).isEqualTo( 88200 );
		Number boosted = ( Number ) run( tone + "result = audioEq( t, [ { type: 'peak', freq: 1000, gain: 6 } ] ).getRms() / t.getRms()" );
		assertThat( boosted.doubleValue() ).isWithin( 0.3 ).of( 2.0 );
		assertThat( message( tone + "result = audioEq( t, [ { type: 'peak', gain: 3 } ] )" ) ).contains( "needs a freq" );
		assertThat( message( tone + "result = audioEq( t, [ { type: 'wobble', freq: 100 } ] )" ) ).contains( "Unknown EQ band type" );
		assertThat( message( tone + "result = audioLimit( t, 3 )" ) ).contains( "ceiling" );
	}

	@DisplayName( "stretch, fitTo and pitch change length or pitch as asked" )
	@Test
	void testStretchAndPitch() {
		String tone = "t = audioOsc( 'sine', 440, 4 ); ";
		assertThat( ( ( Number ) run( tone + "result = audioStretch( t, 2 ).getDurationSeconds()" ) ).doubleValue() ).isWithin( 0.03 ).of( 2.0 );
		assertThat( ( ( Number ) run( tone + "result = audioFitTo( t, 5.5 ).getDurationSeconds()" ) ).doubleValue() ).isWithin( 0.001 ).of( 5.5 );
		assertThat( ( ( Number ) run( tone + "result = audioPitch( t, 3 ).getDurationSeconds()" ) ).doubleValue() ).isWithin( 0.001 ).of( 4.0 );
		assertThat( message( tone + "result = audioStretch( t, 10 )" ) ).contains( "rate must be between" );
		assertThat( message( tone + "result = audioPitch( t, 40 )" ) ).contains( "24 semitones" );
		assertThat( message( tone + "result = audioFitTo( t, 0 )" ) ).contains( "above 0 seconds" );
	}

	@DisplayName( "spectrum, FFT, tempo and silence BIFs return plain arrays and structs" )
	@Test
	void testAnalysis() {
		String	tone	= "t = audioOsc( 'sine', 1000, 1 ); ";
		IStruct	fft		= ( IStruct ) run( tone + "result = audioFft( t, 2048 )" );
		assertThat( ( ( Array ) fft.get( Key.of( "frequencies" ) ) ).size() ).isEqualTo( 1025 );
		assertThat( ( ( Array ) run( tone + "result = audioSpectrum( audio=t, bands=10 )" ) ).size() ).isEqualTo( 10 );
		Array grid = ( Array ) run( tone + "result = audioSpectrogram( audio=t, bands=6, frames=4 )" );
		assertThat( grid.size() ).isEqualTo( 4 );
		assertThat( ( ( Array ) grid.get( 0 ) ).size() ).isEqualTo( 6 );
		Array silences = ( Array ) run(
		    "result = audioDetectSilence( audioConcat( [ audioOsc( 'sine', 440, 1 ), audioSilence( seconds=1, channels=1 ), audioOsc( 'sine', 440, 1 ) ] ) )" );
		assertThat( silences.size() ).isEqualTo( 1 );
		assertThat( ( ( Number ) ( ( IStruct ) silences.get( 0 ) ).get( Key.of( "duration" ) ) ).doubleValue() ).isWithin( 0.03 ).of( 1.0 );
		assertThat( ( ( Number ) run(
		    "result = audioTrimSilence( audioConcat( [ audioSilence( seconds=1, channels=1 ), audioOsc( 'sine', 440, 1 ), audioSilence( seconds=1, channels=1 ) ] ) ).getDurationSeconds()" ) )
		    .doubleValue() ).isWithin( 0.03 ).of( 1.0 );
		IStruct	tempo		= ( IStruct ) run(
		    "kick = audioDrum( type='kick', seconds=0.5 ); bar = audioConcat( [ kick, kick, kick, kick, kick, kick, kick, kick, kick, kick, kick, kick, kick, kick, kick, kick ] ); result = audioBpm( bar ); expected = 60 / kick.getDurationSeconds()" );
		double	expected	= ( ( Number ) variables.get( Key.of( "expected" ) ) ).doubleValue();
		assertThat( ( ( Number ) tempo.get( Key.of( "bpm" ) ) ).doubleValue() ).isWithin( 2.0 ).of( expected );
		assertThat( tempo.keySet().stream().map( Key::getName ).toList() ).containsAtLeast( "bpm", "confidence", "beats" );
	}

}
