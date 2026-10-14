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
package ortus.boxlang.modules.audio.analysis;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class WaveformTest {

	@DisplayName( "Each slice reports its loudest absolute sample, negative peaks included" )
	@Test
	void testPeaksPerSlice() {
		float[] samples = new float[ 8 ];
		samples[ 1 ]	= 0.5f;
		samples[ 6 ]	= -0.9f;
		double[] peaks = Waveform.peaks( new AudioBuffer( new float[][] { samples }, 8000 ), 2 );
		assertThat( peaks ).hasLength( 2 );
		assertThat( peaks[ 0 ] ).isWithin( 1e-6 ).of( 0.5 );
		assertThat( peaks[ 1 ] ).isWithin( 1e-6 ).of( 0.9 );
	}

	@DisplayName( "The loudest channel wins" )
	@Test
	void testChannelsCombined() {
		float[][]	samples	= { { 0.1f, 0.1f, 0.1f, 0.1f }, { 0f, 0f, -0.7f, 0f } };
		double[]	peaks	= Waveform.peaks( new AudioBuffer( samples, 8000 ), 2 );
		assertThat( peaks[ 0 ] ).isWithin( 1e-6 ).of( 0.1 );
		assertThat( peaks[ 1 ] ).isWithin( 1e-6 ).of( 0.7 );
	}

	@DisplayName( "Silence and empty audio give zeros, and more points than frames never leaves a gap" )
	@Test
	void testEdges() {
		assertThat( Waveform.peaks( AudioBuffer.silence( 1, 100, 8000 ), 4 ) ).isEqualTo( new double[ 4 ] );
		assertThat( Waveform.peaks( AudioBuffer.silence( 1, 0, 8000 ), 3 ) ).isEqualTo( new double[ 3 ] );
		double[] peaks = Waveform.peaks( new AudioBuffer( new float[][] { { 0.2f, 0.4f } }, 8000 ), 6 );
		assertThat( peaks ).hasLength( 6 );
		for ( double peak : peaks ) {
			assertThat( peak ).isGreaterThan( 0.0 );
		}
		assertThat( peaks[ 5 ] ).isWithin( 1e-6 ).of( 0.4 );
	}

	@DisplayName( "Bad point counts are rejected" )
	@Test
	void testErrors() {
		AudioBuffer audio = AudioBuffer.silence( 1, 10, 8000 );
		assertThrows( BoxRuntimeException.class, () -> Waveform.peaks( audio, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Waveform.peaks( audio, Waveform.MAX_POINTS + 1 ) );
	}

}
