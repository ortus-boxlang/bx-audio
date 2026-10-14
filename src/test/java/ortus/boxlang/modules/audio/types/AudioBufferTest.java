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
package ortus.boxlang.modules.audio.types;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class AudioBufferTest {

	@DisplayName( "Silence has the requested shape and zero levels" )
	@Test
	void testSilence() {
		AudioBuffer audio = AudioBuffer.silence( 2, 44100, 44100 );
		assertThat( audio.getChannels() ).isEqualTo( 2 );
		assertThat( audio.getFrames() ).isEqualTo( 44100 );
		assertThat( audio.getSampleRate() ).isEqualTo( 44100 );
		assertThat( audio.getDurationSeconds() ).isEqualTo( 1.0 );
		assertThat( audio.getPeak() ).isEqualTo( 0f );
		assertThat( audio.getRms() ).isEqualTo( 0.0 );
	}

	@DisplayName( "Silence by seconds rounds to the nearest frame" )
	@Test
	void testSilenceSeconds() {
		AudioBuffer audio = AudioBuffer.silenceSeconds( 0.5, 1, 22050 );
		assertThat( audio.getFrames() ).isEqualTo( 11025 );
		assertThat( audio.getChannels() ).isEqualTo( 1 );
		assertThat( AudioBuffer.silenceSeconds( 0, 2, 44100 ).getFrames() ).isEqualTo( 0 );
	}

	@DisplayName( "Peak and RMS are measured across all channels" )
	@Test
	void testLevels() {
		AudioBuffer audio = new AudioBuffer( new float[][] { { 0.5f, -0.5f }, { 1f, -1f } }, 44100 );
		assertThat( audio.getPeak() ).isEqualTo( 1f );
		// mean of squares = (0.25 + 0.25 + 1 + 1) / 4 = 0.625
		assertThat( audio.getRms() ).isWithin( 1e-9 ).of( Math.sqrt( 0.625 ) );
	}

	@DisplayName( "A copy is independent of the original" )
	@Test
	void testCopy() {
		AudioBuffer	original	= AudioBuffer.silence( 1, 4, 44100 );
		AudioBuffer	copy		= original.copy();
		copy.channel( 0 )[ 0 ] = 0.75f;
		assertThat( original.channel( 0 )[ 0 ] ).isEqualTo( 0f );
		assertThat( copy.getPeak() ).isEqualTo( 0.75f );
	}

	@DisplayName( "Bad construction arguments are rejected" )
	@Test
	void testValidation() {
		assertThrows( BoxRuntimeException.class, () -> new AudioBuffer( new float[ 0 ][ 0 ], 44100 ) );
		assertThrows( BoxRuntimeException.class, () -> new AudioBuffer( new float[][] { { 0f }, { 0f, 0f } }, 44100 ) );
		assertThrows( BoxRuntimeException.class, () -> new AudioBuffer( new float[][] { { 0f } }, 10 ) );
		assertThrows( BoxRuntimeException.class, () -> AudioBuffer.silence( 0, 10, 44100 ) );
		assertThrows( BoxRuntimeException.class, () -> AudioBuffer.silence( 33, 10, 44100 ) );
		assertThrows( BoxRuntimeException.class, () -> AudioBuffer.silence( 1, -1, 44100 ) );
		assertThrows( BoxRuntimeException.class, () -> AudioBuffer.silenceSeconds( -1, 2, 44100 ) );
		assertThrows( BoxRuntimeException.class, () -> AudioBuffer.silenceSeconds( Double.NaN, 2, 44100 ) );
		assertThrows( BoxRuntimeException.class, () -> AudioBuffer.silenceSeconds( 1e9, 2, 44100 ) );
	}

	@DisplayName( "Channel index is range checked" )
	@Test
	void testChannelRange() {
		AudioBuffer audio = AudioBuffer.silence( 2, 1, 44100 );
		assertThrows( BoxRuntimeException.class, () -> audio.channel( 2 ) );
		assertThrows( BoxRuntimeException.class, () -> audio.channel( -1 ) );
	}

}
