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

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.codec.AudioFiles;
import ortus.boxlang.modules.audio.codec.WavCodec;
import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.types.AudioBuffer;

class PerformanceTest {

	private static AudioBuffer sine( double hz, int rate, int frames ) {
		float[] data = new float[ frames ];
		for ( int i = 0; i < frames; i++ ) {
			data[ i ] = ( float ) ( 0.5 * Math.sin( 2 * Math.PI * hz * i / rate ) );
		}
		return new AudioBuffer( new float[][] { data }, rate );
	}

	@Test
	@DisplayName( "resampling keeps a tone's pitch and level for a common and an awkward ratio" )
	void resampleKeepsTone() {
		for ( int target : new int[] { 48000, 22050, 44101 } ) {
			AudioBuffer out = Dsp.resample( sine( 440, 44100, 44100 ), target );
			assertThat( out.getFrames() ).isEqualTo( ( int ) Math.round( target ) );
			// a rising-edge count over the middle second tells the frequency
			float[]	d			= out.channel( 0 );
			int		crossings	= 0;
			for ( int i = target / 4; i < target * 3 / 4; i++ ) {
				if ( d[ i - 1 ] < 0 && d[ i ] >= 0 ) {
					crossings++;
				}
			}
			assertThat( crossings ).isWithin( 2 ).of( 220 );
			assertThat( ( double ) out.getPeak() ).isWithin( 0.02 ).of( 0.5 );
		}
	}

	@Test
	@DisplayName( "a ten minute resample finishes in a few seconds" )
	void longResampleIsFast() {
		AudioBuffer	big		= sine( 440, 44100, 44100 * 600 );
		long		start	= System.nanoTime();
		Dsp.resample( big, 48000 );
		assertThat( ( System.nanoTime() - start ) / 1_000_000_000.0 ).isLessThan( 30.0 );
	}

	@Test
	@DisplayName( "WAV info comes from the header and a written file reads back streamed" )
	void wavInfoAndStreamedRead( @TempDir Path dir ) {
		Path file = dir.resolve( "tone.wav" );
		WavCodec.write( sine( 440, 44100, 44100 * 3 ), file, 16 );
		WavCodec.Info info = WavCodec.info( file );
		assertThat( info.frames() ).isEqualTo( 44100L * 3 );
		assertThat( info.sampleRate() ).isEqualTo( 44100 );
		assertThat( info.channels() ).isEqualTo( 1 );
		AudioBuffer back = AudioFiles.read( file );
		assertThat( back.getFrames() ).isEqualTo( 44100 * 3 );
		assertThat( ( double ) back.getPeak() ).isWithin( 0.001 ).of( 0.5 );
	}

}
