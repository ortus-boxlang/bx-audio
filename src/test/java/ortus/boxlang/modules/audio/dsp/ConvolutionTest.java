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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

public class ConvolutionTest {

	private static AudioBuffer random( int frames, int channels, int rate, long seed ) {
		Random		r	= new Random( seed );
		float[][]	d	= new float[ channels ][ frames ];
		for ( float[] ch : d ) {
			for ( int i = 0; i < frames; i++ ) {
				ch[ i ] = r.nextFloat() * 2 - 1;
			}
		}
		return new AudioBuffer( d, rate );
	}

	private static String type( Runnable r ) {
		return assertThrows( AudioException.class, r::run ).getType();
	}

	@Test
	@DisplayName( "The wet signal equals direct convolution with the impulse response at unit energy, across several blocks" )
	void matchesDirect() {
		AudioBuffer	x	= random( 10000, 1, 44100, 1 );
		AudioBuffer	h	= random( 3000, 1, 44100, 2 );
		AudioBuffer	y	= Convolution.convolve( x, h, 1 );
		assertThat( y.getFrames() ).isEqualTo( 12999 );
		double energy = 0;
		for ( float v : h.channel( 0 ) ) {
			energy += v * v;
		}
		double	scale	= 1 / Math.sqrt( energy );
		int[]	probes	= { 0, 1, 2999, 3000, 5192, 5193, 9999, 12998 };
		for ( int n : probes ) {
			double sum = 0;
			for ( int k = 0; k < 3000; k++ ) {
				int i = n - k;
				if ( i >= 0 && i < 10000 ) {
					sum += x.channel( 0 )[ i ] * h.channel( 0 )[ k ];
				}
			}
			assertThat( ( double ) y.channel( 0 )[ n ] ).isWithin( 1e-4 ).of( sum * scale );
		}
	}

	@Test
	@DisplayName( "A single spike keeps the sound, and mix 0 is the dry sound followed by silence" )
	void spikeAndDry() {
		AudioBuffer	x		= random( 5000, 1, 44100, 3 );
		AudioBuffer	spike	= new AudioBuffer( new float[][] { new float[] { 1f, 0f, 0f } }, 44100 );
		AudioBuffer	y		= Convolution.convolve( x, spike, 1 );
		for ( int n : new int[] { 0, 17, 4999 } ) {
			assertThat( y.channel( 0 )[ n ] ).isWithin( 1e-5f ).of( x.channel( 0 )[ n ] );
		}
		AudioBuffer dry = Convolution.convolve( x, random( 2000, 1, 44100, 4 ), 0 );
		assertThat( dry.getFrames() ).isEqualTo( 6999 );
		assertThat( dry.channel( 0 )[ 100 ] ).isEqualTo( x.channel( 0 )[ 100 ] );
		assertThat( dry.channel( 0 )[ 6000 ] ).isEqualTo( 0f );
	}

	@Test
	@DisplayName( "Channels: mono with a stereo room is stereo, stereo with a mono room is stereo, stereo with stereo keeps the sides apart" )
	void channels() {
		AudioBuffer	mono	= random( 3000, 1, 44100, 5 );
		AudioBuffer	stereo	= random( 3000, 2, 44100, 6 );
		AudioBuffer	room1	= random( 500, 1, 44100, 7 );
		AudioBuffer	room2	= random( 500, 2, 44100, 8 );
		assertThat( Convolution.convolve( mono, room2, 0.5 ).getChannels() ).isEqualTo( 2 );
		assertThat( Convolution.convolve( stereo, room1, 0.5 ).getChannels() ).isEqualTo( 2 );
		AudioBuffer	both	= Convolution.convolve( stereo, room2, 1 );
		AudioBuffer	left	= Convolution.convolve( new AudioBuffer( new float[][] { stereo.channel( 0 ) }, 44100 ),
		    new AudioBuffer( new float[][] { room2.channel( 0 ) }, 44100 ), 1 );
		assertThat( ( double ) both.channel( 0 )[ 1000 ] ).isWithin( 0.2 ).of( left.channel( 0 )[ 1000 ] * Math.sqrt( 1 ) );
		assertThat( both.channel( 0 )[ 1000 ] ).isNotEqualTo( both.channel( 1 )[ 1000 ] );
	}

	@Test
	@DisplayName( "An impulse response at another sample rate is resampled" )
	void resampled() {
		AudioBuffer	x	= random( 4000, 1, 44100, 9 );
		AudioBuffer	h	= random( 4800, 1, 48000, 10 );
		AudioBuffer	y	= Convolution.convolve( x, h, 1 );
		assertThat( y.getSampleRate() ).isEqualTo( 44100 );
		assertThat( y.getFrames() ).isWithin( 3 ).of( 4000 + 4410 - 1 );
	}

	@Test
	@DisplayName( "Bad input is a typed error" )
	void errors() {
		AudioBuffer	x		= random( 1000, 1, 44100, 11 );
		AudioBuffer	h		= random( 100, 1, 44100, 12 );
		AudioBuffer	empty	= new AudioBuffer( new float[][] { new float[ 0 ] }, 44100 );
		AudioBuffer	surr	= random( 100, 3, 44100, 13 );
		AudioBuffer	huge	= new AudioBuffer( new float[][] { new float[ 44100 * 21 ] }, 44100 );
		assertThat( type( () -> Convolution.convolve( x, h, 1.5 ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> Convolution.convolve( x, empty, 0.5 ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> Convolution.convolve( surr, h, 0.5 ) ) ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( type( () -> Convolution.convolve( x, huge, 0.5 ) ) ).isEqualTo( AudioException.TOO_LARGE );
	}

}
