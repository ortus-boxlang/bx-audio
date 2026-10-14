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

import ortus.boxlang.modules.audio.codec.WavCodec;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Renders the scripts in the examples folder with short settings, so the catalog cannot silently break
 */
public class ExamplesTest extends BaseIntegrationTest {

	private static final Path EXAMPLES = Path.of( "examples" );

	/**
	 * Run an example script with settings preset, and return audioInfo() of what it wrote
	 */
	private IStruct render( String script, IStruct overrides ) throws Exception {
		variables.put( Key.of( "overrides" ), overrides );
		runtime.executeSource( Files.readString( EXAMPLES.resolve( script ) ), context );
		variables.put( Key.of( "writtenTo" ), overrides.get( Key.of( "out" ) ) );
		runtime.executeSource( "result = audioInfo( writtenTo )", context );
		return ( IStruct ) variables.get( Key.of( "result" ) );
	}

	@DisplayName( "Pop Drive renders both styles with the right length, level and format" )
	@Test
	void testPopDrive( @TempDir Path dir ) throws Exception {
		for ( String style : new String[] { "pop", "drive" } ) {
			IStruct info = render( "01-pop-drive/pop-drive.bxs",
			    Struct.of( "out", dir.resolve( style + ".wav" ).toString(), "bars", 8, "bpm", 128, "style", style ) );
			assertThat( info.get( Key.of( "channels" ) ) ).isEqualTo( 2 );
			assertThat( info.get( Key.of( "sampleRate" ) ) ).isEqualTo( 44100 );
			// 8 bars of 1.875 s plus the 2.5 s tail
			assertThat( ( Double ) info.get( Key.of( "seconds" ) ) ).isWithin( 0.01 ).of( 17.5 );
			assertThat( ( Double ) info.get( Key.of( "peak" ) ) ).isWithin( 0.005 ).of( 0.9 );
			assertThat( ( Double ) info.get( Key.of( "rms" ) ) ).isGreaterThan( 0.05 );
			assertThat( ( Double ) info.get( Key.of( "rms" ) ) ).isLessThan( 0.4 );
		}
	}

	@DisplayName( "Rendering an example twice gives the identical file" )
	@Test
	void testRepeatable( @TempDir Path dir ) throws Exception {
		Path	first	= dir.resolve( "a.wav" );
		Path	second	= dir.resolve( "b.wav" );
		render( "01-pop-drive/pop-drive.bxs", Struct.of( "out", first.toString(), "bars", 4, "bpm", 120, "style", "pop" ) );
		render( "01-pop-drive/pop-drive.bxs", Struct.of( "out", second.toString(), "bars", 4, "bpm", 120, "style", "pop" ) );
		assertThat( Files.readAllBytes( second ) ).isEqualTo( Files.readAllBytes( first ) );
	}

	@DisplayName( "The tempo changes the length" )
	@Test
	void testTempo( @TempDir Path dir ) throws Exception {
		IStruct info = render( "01-pop-drive/pop-drive.bxs", Struct.of( "out", dir.resolve( "fast.wav" ).toString(), "bars", 4, "bpm", 240, "style", "pop" ) );
		// 4 bars at 240 bpm is 4 s, plus 2.5 s
		assertThat( ( Double ) info.get( Key.of( "seconds" ) ) ).isWithin( 0.01 ).of( 6.5 );
	}

	/**
	 * Render an example to a file with the given settings and read it back
	 */
	private AudioBuffer renderAudio( Path dir, String name, String script, IStruct settings ) throws Exception {
		Path out = dir.resolve( name + ".wav" );
		settings.put( "out", out.toString() );
		render( script, settings );
		return WavCodec.read( out );
	}

	@DisplayName( "Lofi is dark, soft, swung and has no beat on every pulse" )
	@Test
	void testLofi( @TempDir Path dir ) throws Exception {
		AudioBuffer lofi = renderAudio( dir, "lofi", "02-lofi/lofi.bxs", Struct.of( "bars", 10 ) );
		assertThat( lofi.getChannels() ).isEqualTo( 2 );
		assertThat( lofi.getSampleRate() ).isEqualTo( 44100 );
		assertThat( lofi.getPeak() ).isWithin( 0.005f ).of( 0.88f );
		// 10 bars of 4 beats at 78 BPM is 30.77 s of music, then the texture and effects ring on for about 3.5 s
		assertThat( lofi.getDurationSeconds() - 10 * 4 * 60.0 / 78 ).isWithin( 1.0 ).of( 3.5 );
		// the highs are rolled off, which is the tape and vinyl sound
		assertThat( AudioMetrics.brightnessDb( lofi ) ).isLessThan( -35.0 );
		// boom bap is not four on the floor: no pulse at the beat, a strong one at the bar
		assertThat( AudioMetrics.lowBandAutocorrelation( lofi, 60.0 / 78 ) ).isLessThan( 0.2 );
		assertThat( AudioMetrics.lowBandAutocorrelation( lofi, 60.0 / 78 * 4 ) ).isGreaterThan( 0.5 );
		// soft attacks, but not as flat as a pad
		assertThat( AudioMetrics.onsetStrength( lofi ) ).isGreaterThan( 1.2 );
		assertThat( AudioMetrics.onsetStrength( lofi ) ).isLessThan( 2.6 );
		// it fades to silence instead of stopping
		assertThat( AudioMetrics.tailRms( lofi, 0.05 ) ).isLessThan( 0.001 );
	}

	@DisplayName( "Lofi is the same for the same seed, different for another, and short pieces are lengthened" )
	@Test
	void testLofiSeed( @TempDir Path dir ) throws Exception {
		Path	a	= dir.resolve( "a.wav" );
		Path	b	= dir.resolve( "b.wav" );
		Path	c	= dir.resolve( "c.wav" );
		render( "02-lofi/lofi.bxs", Struct.of( "out", a.toString(), "bars", 6, "seed", 7 ) );
		render( "02-lofi/lofi.bxs", Struct.of( "out", b.toString(), "bars", 6, "seed", 7 ) );
		render( "02-lofi/lofi.bxs", Struct.of( "out", c.toString(), "bars", 6, "seed", 8 ) );
		assertThat( Files.readAllBytes( b ) ).isEqualTo( Files.readAllBytes( a ) );
		assertThat( Files.readAllBytes( c ) ).isNotEqualTo( Files.readAllBytes( a ) );
		// 1 bar is below the minimum of 6, so it is rendered as 6
		Path d = dir.resolve( "d.wav" );
		render( "02-lofi/lofi.bxs", Struct.of( "out", d.toString(), "bars", 1, "seed", 7 ) );
		assertThat( Files.readAllBytes( d ) ).isEqualTo( Files.readAllBytes( a ) );
	}

	@DisplayName( "House has a kick on every beat, bright hats, a punchy attack and a fade out" )
	@Test
	void testHouse( @TempDir Path dir ) throws Exception {
		AudioBuffer house = renderAudio( dir, "house", "03-house/house.bxs", Struct.of( "bars", 16 ) );
		assertThat( house.getChannels() ).isEqualTo( 2 );
		assertThat( house.getPeak() ).isWithin( 0.005f ).of( 0.9f );
		// 16 bars at 124 BPM is 30.97 s of music, then the pad reverb, delays and riser ring on for about 8.8 s
		assertThat( house.getDurationSeconds() - 16 * 4 * 60.0 / 124 ).isWithin( 1.0 ).of( 8.8 );
		double beat = 60.0 / 124;
		// four on the floor: the low end repeats strongly every beat
		assertThat( AudioMetrics.lowBandAutocorrelation( house, beat ) ).isGreaterThan( 0.7 );
		// hats, claps and shakers make it bright
		assertThat( AudioMetrics.brightnessDb( house ) ).isGreaterThan( -28.0 );
		assertThat( AudioMetrics.onsetStrength( house ) ).isGreaterThan( 2.0 );
		assertThat( AudioMetrics.tailRms( house, 0.05 ) ).isLessThan( 0.001 );
	}

	@DisplayName( "House is the same for the same settings and the tempo changes its length" )
	@Test
	void testHouseRepeatableAndTempo( @TempDir Path dir ) throws Exception {
		Path	a	= dir.resolve( "a.wav" );
		Path	b	= dir.resolve( "b.wav" );
		render( "03-house/house.bxs", Struct.of( "out", a.toString(), "bars", 16, "bpm", 128 ) );
		render( "03-house/house.bxs", Struct.of( "out", b.toString(), "bars", 16, "bpm", 128 ) );
		assertThat( Files.readAllBytes( b ) ).isEqualTo( Files.readAllBytes( a ) );
		// 128 BPM is faster than 124 BPM, so the 16 bars are 30.0 s of music instead of 30.97 s, with the same tail
		assertThat( WavCodec.read( a ).getDurationSeconds() - 16 * 4 * 60.0 / 128 ).isWithin( 1.0 ).of( 8.8 );
	}

	@DisplayName( "Ambient is dark and has almost no transients" )
	@Test
	void testAmbient( @TempDir Path dir ) throws Exception {
		AudioBuffer ambient = renderAudio( dir, "ambient", "04-ambient/ambient.bxs", Struct.of( "bars", 4 ) );
		assertThat( ambient.getChannels() ).isEqualTo( 2 );
		assertThat( ambient.getPeak() ).isWithin( 0.005f ).of( 0.85f );
		// 4 bars of 5 s is 20 s of music, then the very long reverb tails ring on for about 18.6 s
		assertThat( ambient.getDurationSeconds() - 4 * 4 * 60.0 / 48 ).isWithin( 3.0 ).of( 18.6 );
		assertThat( AudioMetrics.brightnessDb( ambient ) ).isLessThan( -35.0 );
		// no attack in the piece is sharper than a gentle swell: well below the other examples (1.8 and above)
		assertThat( AudioMetrics.onsetStrength( ambient ) ).isLessThan( 1.2 );
		assertThat( AudioMetrics.tailRms( ambient, 0.05 ) ).isLessThan( 0.001 );
	}

	@DisplayName( "Ambient is the same for the same seed and different for another" )
	@Test
	void testAmbientSeed( @TempDir Path dir ) throws Exception {
		Path	a	= dir.resolve( "a.wav" );
		Path	b	= dir.resolve( "b.wav" );
		Path	c	= dir.resolve( "c.wav" );
		render( "04-ambient/ambient.bxs", Struct.of( "out", a.toString(), "bars", 4, "seed", 5 ) );
		render( "04-ambient/ambient.bxs", Struct.of( "out", b.toString(), "bars", 4, "seed", 5 ) );
		render( "04-ambient/ambient.bxs", Struct.of( "out", c.toString(), "bars", 4, "seed", 6 ) );
		assertThat( Files.readAllBytes( b ) ).isEqualTo( Files.readAllBytes( a ) );
		assertThat( Files.readAllBytes( c ) ).isNotEqualTo( Files.readAllBytes( a ) );
	}

}
