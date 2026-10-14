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

}
