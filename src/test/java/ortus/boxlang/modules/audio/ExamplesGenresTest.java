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
import static com.google.common.truth.Truth.assertWithMessage;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.Struct;

/**
 * Renders the genre examples with short settings and checks what each one sounds like by measurement
 */
public class ExamplesGenresTest extends ExamplesTestBase {

	/**
	 * Render the same script twice and make sure the files are byte for byte the same
	 */
	private void assertRepeatable( Path dir, String script, int bars ) throws Exception {
		Path	a	= dir.resolve( "a.wav" );
		Path	b	= dir.resolve( "b.wav" );
		render( script, Struct.of( "out", a.toString(), "bars", bars ) );
		render( script, Struct.of( "out", b.toString(), "bars", bars ) );
		assertThat( Files.readAllBytes( b ) ).isEqualTo( Files.readAllBytes( a ) );
	}

	/**
	 * RMS of a stretch of the mono mix
	 */
	private static double rmsBetween( AudioBuffer audio, double from, double to ) {
		float[]	mono	= AudioMetrics.mono( audio );
		int		start	= ( int ) ( from * audio.getSampleRate() );
		int		end		= Math.min( mono.length, ( int ) ( to * audio.getSampleRate() ) );
		return AudioMetrics.rms( java.util.Arrays.copyOfRange( mono, start, end ) );
	}

	@DisplayName( "Techno has a hard kick on every beat, a punchy attack and a clean ending" )
	@Test
	void testTechno( @TempDir Path dir ) throws Exception {
		AudioBuffer	techno	= renderAudio( dir, "techno", "05-techno/techno.bxs", Struct.of( "bars", 16 ) );
		double		beat	= 60.0 / 132;
		assertThat( techno.getChannels() ).isEqualTo( 2 );
		assertThat( techno.getPeak() ).isWithin( 0.005f ).of( 0.9f );
		// 16 bars at 132 BPM is 29.1 s of music, then the echoes, reverb and riser ring on for about 7.9 s
		assertThat( techno.getDurationSeconds() - 16 * 4 * beat ).isWithin( 1.0 ).of( 7.85 );
		assertThat( AudioMetrics.lowBandAutocorrelation( techno, beat ) ).isGreaterThan( 0.8 );
		// the pulse is weaker at the bar than at the beat, as the breakdown has no kick
		assertThat( AudioMetrics.lowBandAutocorrelation( techno, beat * 4 ) ).isLessThan( AudioMetrics.lowBandAutocorrelation( techno, beat ) );
		assertThat( AudioMetrics.brightnessDb( techno ) ).isGreaterThan( -28.0 );
		assertThat( AudioMetrics.onsetStrength( techno ) ).isGreaterThan( 2.5 );
		assertThat( AudioMetrics.tailRms( techno, 0.05 ) ).isLessThan( 0.001 );
	}

	@DisplayName( "Techno has no kick in its breakdown and is the same every time" )
	@Test
	void testTechnoBreakdownAndRepeat( @TempDir Path dir ) throws Exception {
		AudioBuffer	techno	= renderAudio( dir, "techno", "05-techno/techno.bxs", Struct.of( "bars", 16 ) );
		double		bar		= 4 * 60.0 / 132;
		// bars 8 to 11 are the breakdown at 16 bars: much quieter than the groove at bars 4 to 7
		assertThat( rmsBetween( techno, 9 * bar, 11 * bar ) ).isLessThan( rmsBetween( techno, 5 * bar, 7 * bar ) * 0.5 );
		assertRepeatable( dir, "05-techno/techno.bxs", 16 );
	}

	@DisplayName( "Synthwave has a steady beat, a gated snare reverb and a long tail" )
	@Test
	void testSynthwave( @TempDir Path dir ) throws Exception {
		AudioBuffer	synth	= renderAudio( dir, "synthwave", "06-synthwave/synthwave.bxs", Struct.of( "bars", 16 ) );
		double		beat	= 60.0 / 100;
		assertThat( synth.getChannels() ).isEqualTo( 2 );
		assertThat( synth.getPeak() ).isWithin( 0.005f ).of( 0.9f );
		// 16 bars at 100 BPM is 38.4 s of music, then the snare reverb, pads and echoes ring on for about 9.5 s
		assertThat( synth.getDurationSeconds() - 16 * 4 * beat ).isWithin( 1.0 ).of( 9.5 );
		// kick on one and three plus eighth note bass: a strong pulse at the beat and at the bar
		assertThat( AudioMetrics.lowBandAutocorrelation( synth, beat ) ).isGreaterThan( 0.7 );
		assertThat( AudioMetrics.lowBandAutocorrelation( synth, beat * 4 ) ).isGreaterThan( 0.5 );
		assertThat( AudioMetrics.brightnessDb( synth ) ).isGreaterThan( -30.0 );
		assertThat( AudioMetrics.onsetStrength( synth ) ).isGreaterThan( 1.8 );
		assertThat( AudioMetrics.tailRms( synth, 0.05 ) ).isLessThan( 0.001 );
		assertRepeatable( dir, "06-synthwave/synthwave.bxs", 16 );
	}

	@DisplayName( "Cinematic builds from quiet to loud, goes silent for a beat, then hits" )
	@Test
	void testCinematic( @TempDir Path dir ) throws Exception {
		AudioBuffer	cinematic	= renderAudio( dir, "cinematic", "07-cinematic/cinematic.bxs", Struct.of( "bars", 16 ) );
		double		beat		= 60.0 / 90;
		double		bar			= beat * 4;
		assertThat( cinematic.getChannels() ).isEqualTo( 2 );
		assertThat( cinematic.getPeak() ).isWithin( 0.005f ).of( 0.9f );
		// 16 bars is a 4 bar tension, 4 bar build, 4 bar climax and 4 bar ending, then the final hit rings on for about 7.3 s
		assertThat( cinematic.getDurationSeconds() - 16 * bar ).isWithin( 1.5 ).of( 7.3 );
		double	tension	= rmsBetween( cinematic, 0, 4 * bar );
		double	climax	= rmsBetween( cinematic, 8 * bar, 12 * bar );
		assertThat( climax ).isGreaterThan( tension * 1.8 );
		// the last beat of the build is true silence, and the boom right after it is loud
		assertThat( rmsBetween( cinematic, 8 * bar - beat + 0.1, 8 * bar - 0.02 ) ).isLessThan( 0.0001 );
		assertThat( rmsBetween( cinematic, 8 * bar, 8 * bar + 0.5 ) ).isGreaterThan( 0.1 );
		// it is not a dance track: no steady beat in the low end
		assertThat( AudioMetrics.lowBandAutocorrelation( cinematic, beat ) ).isLessThan( 0.7 );
		assertThat( AudioMetrics.tailRms( cinematic, 0.05 ) ).isLessThan( 0.001 );
		assertRepeatable( dir, "07-cinematic/cinematic.bxs", 16 );
	}

	/**
	 * Render one chiptune track for a single loop
	 */
	private AudioBuffer chiptune( Path dir, String track ) throws Exception {
		return renderAudio( dir, track, "08-chiptune/chiptune.bxs", Struct.of( "track", track, "loops", 1 ) );
	}

	@DisplayName( "Every chiptune track renders, is bright and dry, ends cleanly and has the length its tempo and form give" )
	@Test
	void testChiptuneTracks( @TempDir Path dir ) throws Exception {
		// track, bpm, bars in one loop, the seconds of tail past the last bar: 0.3 for the last note, 4.3 for the cave's echo
		Object[][] tracks = { { "overworld", 150.0, 16, 0.3 }, { "cave", 90.0, 8, 4.3 }, { "boss", 170.0, 16, 0.33 }, { "title", 120.0, 8, 0.3 },
		    { "victory", 140.0, 8, 0.3 } };
		for ( Object[] t : tracks ) {
			AudioBuffer audio = chiptune( dir, ( String ) t[ 0 ] );
			assertThat( audio.getChannels() ).isEqualTo( 2 );
			assertThat( audio.getPeak() ).isWithin( 0.005f ).of( 0.9f );
			assertThat( audio.getDurationSeconds() - ( int ) t[ 2 ] * 4 * 60.0 / ( double ) t[ 1 ] ).isWithin( 0.5 ).of( ( double ) t[ 3 ] );
			// pulse and noise channels have no rolled off highs, the brightest music in the catalog
			assertThat( AudioMetrics.brightnessDb( audio ) ).isGreaterThan( -20.0 );
			assertThat( AudioMetrics.tailRms( audio, 0.05 ) ).isLessThan( 0.001 );
		}
	}

	@DisplayName( "Chiptune keeps time: a pulse at the beat and the bar" )
	@Test
	void testChiptuneRhythm( @TempDir Path dir ) throws Exception {
		AudioBuffer overworld = chiptune( dir, "overworld" );
		assertThat( AudioMetrics.lowBandAutocorrelation( overworld, 60.0 / 150 ) ).isGreaterThan( 0.7 );
		assertThat( AudioMetrics.lowBandAutocorrelation( overworld, 4 * 60.0 / 150 ) ).isGreaterThan( 0.6 );
		// the boss track is the fastest and keeps a strong bar pulse too
		assertThat( AudioMetrics.lowBandAutocorrelation( chiptune( dir, "boss" ), 4 * 60.0 / 170 ) ).isGreaterThan( 0.4 );
	}

	@DisplayName( "The tracks differ, the same track repeats exactly, and loops lengthen it" )
	@Test
	void testChiptuneVariants( @TempDir Path dir ) throws Exception {
		Path	a	= dir.resolve( "a.wav" );
		Path	b	= dir.resolve( "b.wav" );
		Path	c	= dir.resolve( "c.wav" );
		render( "08-chiptune/chiptune.bxs", Struct.of( "out", a.toString(), "track", "victory" ) );
		render( "08-chiptune/chiptune.bxs", Struct.of( "out", b.toString(), "track", "victory" ) );
		render( "08-chiptune/chiptune.bxs", Struct.of( "out", c.toString(), "track", "title" ) );
		assertThat( Files.readAllBytes( b ) ).isEqualTo( Files.readAllBytes( a ) );
		assertThat( Files.readAllBytes( c ) ).isNotEqualTo( Files.readAllBytes( a ) );

		double	once	= chiptune( dir, "title" ).getDurationSeconds();
		Path	twice	= dir.resolve( "twice.wav" );
		render( "08-chiptune/chiptune.bxs", Struct.of( "out", twice.toString(), "track", "title", "loops", 2 ) );
		assertThat( ortus.boxlang.modules.audio.codec.WavCodec.read( twice ).getDurationSeconds() - 0.3 ).isWithin( 0.1 ).of( 2 * ( once - 0.3 ) );
	}

	@DisplayName( "An unknown track is refused with the list of tracks" )
	@Test
	void testChiptuneUnknownTrack( @TempDir Path dir ) {
		String message = org.junit.jupiter.api.Assertions
		    .assertThrows( Exception.class, () -> render( "08-chiptune/chiptune.bxs", Struct.of( "out", dir.resolve( "x.wav" ).toString(), "track", "nope" ) ) )
		    .getMessage();
		assertThat( message ).contains( "Unknown track [nope]" );
		assertThat( message ).contains( "overworld" );
	}

	/**
	 * Render one sound effect
	 */
	private AudioBuffer sfx( Path dir, String sound ) throws Exception {
		return renderAudio( dir, "sfx-" + sound, "09-sfx/sfx.bxs", Struct.of( "sound", sound ) );
	}

	/**
	 * Zero crossings per second between two moments, which is twice the pitch of a simple tone
	 */
	private static double crossingsPerSecond( AudioBuffer audio, double from, double to ) {
		float[]	mono	= audio.channel( 0 );
		int		start	= ( int ) ( from * audio.getSampleRate() );
		int		end		= Math.min( mono.length, ( int ) ( to * audio.getSampleRate() ) );
		int		count	= 0;
		for ( int i = start + 1; i < end; i++ ) {
			if ( ( mono[ i - 1 ] < 0 ) != ( mono[ i ] < 0 ) ) {
				count++;
			}
		}
		return count / 2.0 / ( ( end - start ) / ( double ) audio.getSampleRate() );
	}

	@DisplayName( "Every sound effect is mono, at the common level, as long as it should be and ends cleanly" )
	@Test
	void testSfxEachSound( @TempDir Path dir ) throws Exception {
		Object[][] sounds = { { "jump", 0.20 }, { "coin", 0.47 }, { "laser", 0.28 }, { "explosion", 1.0 }, { "powerup", 0.36 }, { "hit", 0.12 },
		    { "blip", 0.06 }, { "alarm", 0.64 }, { "gameover", 1.56 }, { "footstep", 0.12 } };
		for ( Object[] sound : sounds ) {
			AudioBuffer audio = sfx( dir, ( String ) sound[ 0 ] );
			assertThat( audio.getChannels() ).isEqualTo( 1 );
			// normalized to -2 dB
			assertThat( audio.getPeak() ).isWithin( 0.005f ).of( 0.794f );
			assertThat( audio.getDurationSeconds() ).isWithin( 0.03 ).of( ( double ) sound[ 1 ] );
			// the last sample is the end of a fade out: no click
			assertThat( Math.abs( audio.channel( 0 )[ audio.getFrames() - 1 ] ) ).isLessThan( 0.01f );
		}
	}

	@DisplayName( "The pitch moves the way each sound is meant to: jump and power up rise, laser, hit and game over fall" )
	@Test
	void testSfxPitchDirections( @TempDir Path dir ) throws Exception {
		AudioBuffer jump = sfx( dir, "jump" );
		assertThat( crossingsPerSecond( jump, 0.14, 0.2 ) ).isGreaterThan( crossingsPerSecond( jump, 0, 0.06 ) * 1.4 );
		AudioBuffer laser = sfx( dir, "laser" );
		assertThat( crossingsPerSecond( laser, 0, 0.09 ) ).isGreaterThan( crossingsPerSecond( laser, 0.19, 0.28 ) * 2.5 );
		AudioBuffer powerup = sfx( dir, "powerup" );
		assertThat( crossingsPerSecond( powerup, 0.24, 0.36 ) ).isGreaterThan( crossingsPerSecond( powerup, 0, 0.12 ) * 1.8 );
		AudioBuffer hit = sfx( dir, "hit" );
		assertThat( crossingsPerSecond( hit, 0, 0.04 ) ).isGreaterThan( crossingsPerSecond( hit, 0.08, 0.12 ) * 1.5 );
		AudioBuffer gameover = sfx( dir, "gameover" );
		assertThat( crossingsPerSecond( gameover, 0, 0.2 ) ).isGreaterThan( crossingsPerSecond( gameover, 1.2, 1.5 ) * 1.3 );
	}

	@DisplayName( "The coin is two notes, B5 then E6, and the alarm alternates two pitches" )
	@Test
	void testSfxCoinAndAlarm( @TempDir Path dir ) throws Exception {
		AudioBuffer coin = sfx( dir, "coin" );
		assertThat( crossingsPerSecond( coin, 0.005, 0.065 ) ).isWithin( 80.0 ).of( 988.0 );
		assertThat( crossingsPerSecond( coin, 0.1, 0.4 ) ).isWithin( 40.0 ).of( 1319.0 );
		AudioBuffer alarm = sfx( dir, "alarm" );
		assertThat( crossingsPerSecond( alarm, 0.02, 0.14 ) ).isWithin( 60.0 ).of( 620.0 );
		assertThat( crossingsPerSecond( alarm, 0.18, 0.30 ) ).isWithin( 60.0 ).of( 930.0 );
		assertThat( crossingsPerSecond( alarm, 0.34, 0.46 ) ).isWithin( 60.0 ).of( 620.0 );
	}

	@DisplayName( "The explosion is a decaying noise burst and the footstep is a dull thud" )
	@Test
	void testSfxNoiseSounds( @TempDir Path dir ) throws Exception {
		AudioBuffer explosion = sfx( dir, "explosion" );
		assertThat( rmsBetween( explosion, 0, 0.33 ) ).isGreaterThan( rmsBetween( explosion, 0.67, 1.0 ) * 10 );
		assertThat( AudioMetrics.brightnessDb( sfx( dir, "footstep" ) ) ).isLessThan( -25.0 );
		// a pulse tone is bright, noise that was low passed is not
		assertThat( AudioMetrics.brightnessDb( sfx( dir, "jump" ) ) ).isGreaterThan( AudioMetrics.brightnessDb( explosion ) );
	}

	@DisplayName( "The sampler plays every sound with a pause after it, and a folder gets one file per sound" )
	@Test
	void testSfxSamplerAndFolder( @TempDir Path dir ) throws Exception {
		AudioBuffer	sampler	= renderAudio( dir, "sampler", "09-sfx/sfx.bxs", Struct.of() );
		double		sounds	= 0.20 + 0.47 + 0.28 + 1.0 + 0.36 + 0.12 + 0.06 + 0.64 + 1.56 + 0.12;
		assertThat( sampler.getDurationSeconds() ).isWithin( 0.3 ).of( sounds + 10 * 0.35 );

		Path pack = dir.resolve( "pack" );
		// folder mode writes no single file, so run the script directly instead of through render(), which reads one back
		variables.put( ortus.boxlang.runtime.scopes.Key.of( "overrides" ), Struct.of( "folder", pack.toString() ) );
		runtime.executeSource( Files.readString( EXAMPLES.resolve( "09-sfx/sfx.bxs" ) ), context );
		try ( var files = Files.list( pack ) ) {
			assertThat( files.map( f -> f.getFileName().toString() ).sorted().toList() ).containsExactly( "alarm.wav", "blip.wav", "coin.wav",
			    "explosion.wav", "footstep.wav", "gameover.wav", "hit.wav", "jump.wav", "laser.wav", "powerup.wav" );
		}
	}

	@DisplayName( "The seed changes the noise sounds and nothing else, and the same seed gives the same file" )
	@Test
	void testSfxSeed( @TempDir Path dir ) throws Exception {
		Path[]		files	= new Path[ 6 ];
		String[]	sounds	= { "explosion", "explosion", "explosion", "jump", "jump", "jump" };
		int[]		seeds	= { 1, 1, 2, 1, 1, 2 };
		for ( int i = 0; i < files.length; i++ ) {
			files[ i ] = dir.resolve( "f" + i + ".wav" );
			render( "09-sfx/sfx.bxs", Struct.of( "out", files[ i ].toString(), "sound", sounds[ i ], "seed", seeds[ i ] ) );
		}
		assertThat( Files.readAllBytes( files[ 1 ] ) ).isEqualTo( Files.readAllBytes( files[ 0 ] ) );
		assertThat( Files.readAllBytes( files[ 2 ] ) ).isNotEqualTo( Files.readAllBytes( files[ 0 ] ) );
		assertThat( Files.readAllBytes( files[ 5 ] ) ).isEqualTo( Files.readAllBytes( files[ 3 ] ) );
	}

	@DisplayName( "An unknown sound is refused with the list of sounds" )
	@Test
	void testSfxUnknownSound( @TempDir Path dir ) {
		String message = org.junit.jupiter.api.Assertions
		    .assertThrows( Exception.class, () -> render( "09-sfx/sfx.bxs", Struct.of( "out", dir.resolve( "x.wav" ).toString(), "sound", "nope" ) ) )
		    .getMessage();
		assertThat( message ).contains( "Unknown sound [nope]" );
		assertThat( message ).contains( "explosion" );
	}

	/**
	 * How much of a tone at one frequency there is in a stretch of the mono mix: the Goertzel algorithm, which is a single bin of a DFT
	 */
	private static double energyAt( AudioBuffer audio, double from, double to, double hz ) {
		float[]	mono	= audio.channel( 0 );
		int		start	= ( int ) ( from * audio.getSampleRate() );
		int		end		= Math.min( mono.length, ( int ) ( to * audio.getSampleRate() ) );
		double	w		= 2 * Math.PI * hz / audio.getSampleRate();
		double	coeff	= 2 * Math.cos( w ), s1 = 0, s2 = 0;
		for ( int i = start; i < end; i++ ) {
			double s0 = mono[ i ] * ( 0.5 - 0.5 * Math.cos( 2 * Math.PI * ( i - start ) / ( end - start ) ) ) + coeff * s1 - s2;
			s2	= s1;
			s1	= s0;
		}
		return ( s1 * s1 + s2 * s2 - coeff * s1 * s2 ) / ( end - start );
	}

	@DisplayName( "The jazz walking bass starts every bar of the blues on the root of its chord" )
	@Test
	void testJazzBassRoots( @TempDir Path dir ) throws Exception {
		AudioBuffer	jazz	= renderAudio( dir, "jazz", "10-jazz/jazz.bxs", Struct.of( "choruses", 2 ) );
		double		beat	= 60.0 / 132;
		// the root of each of the 12 bars in Hz: F2, Bb2, F, F, Bb, Bb, F, D3, G2, C3, F2, G2, as the bass register puts them
		double		f		= 87.31, bb = 116.54, d = 146.83, g = 97.99, c = 130.81;
		double[]	roots	= { f, bb, f, f, bb, bb, f, d, g, c, f, g };
		double[]	all		= { f, bb, d, g, c };
		for ( int chorus = 0; chorus < 2; chorus++ ) {
			for ( int bar = 0; bar < 12; bar++ ) {
				double	from		= ( chorus * 12 + bar ) * 4 * beat + 0.03;
				double	expected	= energyAt( jazz, from, from + 0.25, roots[ bar ] );
				for ( double other : all ) {
					if ( other != roots[ bar ] ) {
						assertWithMessage( "bar " + ( bar + 1 ) + " root " + roots[ bar ] + " against " + other ).that( expected )
						    .isGreaterThan( energyAt( jazz, from, from + 0.25, other ) );
					}
				}
			}
		}
	}

	@DisplayName( "Jazz keeps a steady pulse, has the length of its choruses plus a held ending, and is mild in the highs" )
	@Test
	void testJazzCharacter( @TempDir Path dir ) throws Exception {
		AudioBuffer	jazz	= renderAudio( dir, "jazz", "10-jazz/jazz.bxs", Struct.of( "choruses", 2 ) );
		double		beat	= 60.0 / 132;
		assertThat( jazz.getChannels() ).isEqualTo( 2 );
		assertThat( jazz.getPeak() ).isWithin( 0.005f ).of( 0.9f );
		// 2 choruses of 12 bars at 132 BPM is 43.6 s, then the held chord, the crash and the reverb ring on for about 3.8 s
		assertThat( jazz.getDurationSeconds() - 2 * 12 * 4 * beat ).isWithin( 1.0 ).of( 3.8 );
		// a walking bass puts a note on every beat, so the low end pulses at the beat
		assertThat( AudioMetrics.lowBandAutocorrelation( jazz, beat ) ).isGreaterThan( 0.5 );
		assertThat( AudioMetrics.brightnessDb( jazz ) ).isLessThan( -25.0 );
		assertThat( AudioMetrics.tailRms( jazz, 0.05 ) ).isLessThan( 0.001 );
	}

	@DisplayName( "The seed changes the solo, the same seed repeats exactly, swing changes the feel but not the length, and more choruses are longer" )
	@Test
	void testJazzVariants( @TempDir Path dir ) throws Exception {
		Path[]		files	= new Path[ 5 ];
		String[]	names	= { "a", "b", "c", "d", "e" };
		int[]		seeds	= { 4, 4, 5, 4, 4 };
		double[]	swings	= { 0.64, 0.64, 0.64, 0.5, 0.64 };
		int[]		chorus	= { 2, 2, 2, 2, 3 };
		for ( int i = 0; i < files.length; i++ ) {
			files[ i ] = dir.resolve( names[ i ] + ".wav" );
			render( "10-jazz/jazz.bxs", Struct.of( "out", files[ i ].toString(), "seed", seeds[ i ], "swing", swings[ i ], "choruses", chorus[ i ] ) );
		}
		assertThat( Files.readAllBytes( files[ 1 ] ) ).isEqualTo( Files.readAllBytes( files[ 0 ] ) );
		assertThat( Files.readAllBytes( files[ 2 ] ) ).isNotEqualTo( Files.readAllBytes( files[ 0 ] ) );
		assertThat( Files.readAllBytes( files[ 3 ] ) ).isNotEqualTo( Files.readAllBytes( files[ 0 ] ) );
		double two = ortus.boxlang.modules.audio.codec.WavCodec.read( files[ 0 ] ).getDurationSeconds();
		assertThat( ortus.boxlang.modules.audio.codec.WavCodec.read( files[ 3 ] ).getDurationSeconds() ).isWithin( 0.05 ).of( two );
		assertThat( ortus.boxlang.modules.audio.codec.WavCodec.read( files[ 4 ] ).getDurationSeconds() - two ).isWithin( 0.1 ).of( 12 * 4 * 60.0 / 132 );
	}

	/**
	 * RMS of one bar of a piece
	 */
	private static double barRms( AudioBuffer audio, double barSeconds, int bar ) {
		return rmsBetween( audio, bar * barSeconds, ( bar + 1 ) * barSeconds );
	}

	/**
	 * How alike two stretches of the mono mix are, from 0 to 1 (cosine similarity of the samples)
	 */
	private static double similarity( AudioBuffer a, AudioBuffer b, double from, double to ) {
		float[]	x		= a.channel( 0 );
		float[]	y		= b.channel( 0 );
		int		start	= ( int ) ( from * a.getSampleRate() );
		int		end		= Math.min( Math.min( x.length, y.length ), ( int ) ( to * a.getSampleRate() ) );
		double	dot		= 0, nx = 0, ny = 0;
		for ( int i = start; i < end; i++ ) {
			dot	+= x[ i ] * y[ i ];
			nx	+= x[ i ] * x[ i ];
			ny	+= y[ i ] * y[ i ];
		}
		return dot / Math.sqrt( nx * ny );
	}

	@DisplayName( "Rock is loud and compressed, with an arc: a quiet riff, a verse, a louder chorus, a solo and a big ending" )
	@Test
	void testRockArc( @TempDir Path dir ) throws Exception {
		AudioBuffer	rock	= renderAudio( dir, "rock", "11-rock/rock.bxs", Struct.of() );
		double		beat	= 60.0 / 128;
		double		bar		= 4 * beat;
		assertThat( rock.getChannels() ).isEqualTo( 2 );
		assertThat( rock.getPeak() ).isWithin( 0.005f ).of( 0.9f );
		// 32 bars at 128 BPM is 60 s, then the last chord and its crash ring on for about 4.5 s
		assertThat( rock.getDurationSeconds() - 32 * bar ).isWithin( 1.0 ).of( 4.5 );
		// bars 0 and 1: the riff alone. 4 is a verse bar, 12 a chorus bar, 20 a solo bar.
		double	intro	= barRms( rock, bar, 0 );
		double	verse	= barRms( rock, bar, 4 );
		double	chorus	= barRms( rock, bar, 12 );
		double	solo	= barRms( rock, bar, 20 );
		assertThat( verse ).isGreaterThan( intro * 1.8 );
		assertThat( chorus ).isGreaterThan( verse * 1.2 );
		assertThat( solo ).isGreaterThan( intro * 1.5 );
		assertThat( solo ).isLessThan( chorus );
		// the whole track is loud and heavily compressed by the distortion: RMS above -14 dBFS, and a crest factor under 12 dB
		double rms = rock.getRms();
		assertThat( 20 * Math.log10( rms ) ).isGreaterThan( -14.0 );
		assertThat( 20 * Math.log10( rock.getPeak() / rms ) ).isLessThan( 12.0 );
		assertThat( AudioMetrics.lowBandAutocorrelation( rock, beat ) ).isGreaterThan( 0.6 );
		assertThat( AudioMetrics.tailRms( rock, 0.05 ) ).isLessThan( 0.001 );
	}

	@DisplayName( "Rock bars with the same chord are identical and a chord change sounds different" )
	@Test
	void testRockChords( @TempDir Path dir ) throws Exception {
		AudioBuffer	rock	= renderAudio( dir, "rock", "11-rock/rock.bxs", Struct.of() );
		double		bar		= 4 * 60.0 / 128;
		// bars 4 and 5 are both E in the verse, bar 6 is G, bar 7 is A
		double		same	= cosine( rock, 4 * bar, 5 * bar );
		double		change	= cosine( rock, 4 * bar, 6 * bar );
		assertThat( same ).isGreaterThan( 0.99 );
		assertThat( change ).isLessThan( 0.9 );
	}

	/**
	 * The similarity of two bars of the same buffer, by shifting one onto the other
	 */
	private static double cosine( AudioBuffer audio, double barA, double barB ) {
		float[]	x		= audio.channel( 0 );
		int		rate	= audio.getSampleRate();
		int		a		= ( int ) ( barA * rate );
		int		b		= ( int ) ( barB * rate );
		int		length	= ( int ) ( ( 4 * 60.0 / 128 ) * rate );
		double	dot		= 0, na = 0, nb = 0;
		for ( int i = 0; i < length; i++ ) {
			dot	+= x[ a + i ] * x[ b + i ];
			na	+= x[ a + i ] * x[ a + i ];
			nb	+= x[ b + i ] * x[ b + i ];
		}
		return dot / Math.sqrt( na * nb );
	}

	@DisplayName( "The seed changes only the solo, the same seed repeats exactly" )
	@Test
	void testRockSeed( @TempDir Path dir ) throws Exception {
		AudioBuffer	a	= renderAudio( dir, "a", "11-rock/rock.bxs", Struct.of( "seed", 3 ) );
		AudioBuffer	b	= renderAudio( dir, "b", "11-rock/rock.bxs", Struct.of( "seed", 3 ) );
		AudioBuffer	c	= renderAudio( dir, "c", "11-rock/rock.bxs", Struct.of( "seed", 9 ) );
		double		bar	= 4 * 60.0 / 128;
		assertThat( Files.readAllBytes( dir.resolve( "b.wav" ) ) ).isEqualTo( Files.readAllBytes( dir.resolve( "a.wav" ) ) );
		// everything before the solo (bar 20) is the same music, the solo is a different line
		assertThat( similarity( a, c, 0, 19 * bar ) ).isGreaterThan( 0.999 );
		assertThat( similarity( a, c, 21 * bar, 27 * bar ) ).isLessThan( 0.9 );
		assertThat( b.getFrames() ).isEqualTo( a.getFrames() );
	}

}
