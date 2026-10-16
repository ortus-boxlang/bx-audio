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
package ortus.boxlang.modules.audio.dsp;

import static com.google.common.truth.Truth.assertThat;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.types.AudioBuffer;

public class LoopsTest {

	private static double rms( float[] samples, int from, int to ) {
		double sum = 0;
		for ( int i = from; i < to; i++ ) {
			sum += ( double ) samples[ i ] * samples[ i ];
		}
		return Math.sqrt( sum / ( to - from ) );
	}

	private static double db( double ratio ) {
		return 20 * Math.log10( ratio );
	}

	@Test
	@DisplayName( "A held tone keeps its level through the join, with no bump in the middle of the crossfade" )
	void sameSoundKeepsLevel() {
		// 441 Hz at 44100 Hz repeats every 100 frames, and 88250 frames less the 22050 of the crossfade is a whole number of them, so the part cut
		// from the end is the same sound as the start
		float[] samples = new float[ 88250 ];
		for ( int i = 0; i < samples.length; i++ ) {
			samples[ i ] = ( float ) Math.sin( 2 * Math.PI * 441 * i / 44100.0 );
		}
		AudioBuffer	out		= Loops.loopify( new AudioBuffer( new float[][] { samples }, 44100 ), 0.5 );
		float[]		looped	= out.channel( 0 );
		double		steady	= rms( looped, 44100, 44100 + 4410 );
		double		middle	= rms( looped, 11025 - 2205, 11025 + 2205 );
		assertThat( db( middle / steady ) ).isWithin( 0.2 ).of( 0 );
	}

	@Test
	@DisplayName( "Two unrelated sounds keep their level through the join" )
	void unrelatedSoundsKeepLevel() {
		Random	random	= new Random( 5 );
		float[]	samples	= new float[ 44100 * 4 ];
		for ( int i = 0; i < samples.length; i++ ) {
			samples[ i ] = random.nextFloat() * 2 - 1;
		}
		AudioBuffer	out		= Loops.loopify( new AudioBuffer( new float[][] { samples }, 44100 ), 0.5 );
		float[]		looped	= out.channel( 0 );
		double		steady	= rms( looped, 44100, 44100 * 2 );
		double		middle	= rms( looped, 11025 - 2205, 11025 + 2205 );
		assertThat( db( middle / steady ) ).isWithin( 0.5 ).of( 0 );
	}

	@Test
	@DisplayName( "The loop is shorter by the crossfade, and a zero crossfade is a copy" )
	void length() {
		AudioBuffer in = new AudioBuffer( new float[][] { new float[ 44100 ] }, 44100 );
		assertThat( Loops.loopify( in, 0.25 ).getFrames() ).isEqualTo( 44100 - 11025 );
		assertThat( Loops.loopify( in, 0 ).getFrames() ).isEqualTo( 44100 );
	}

}
