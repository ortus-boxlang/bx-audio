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

import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.types.AudioBuffer;

/**
 * Measurements used by the example tests to describe what a piece sounds like without listening to it: how bright it is, whether it has a
 * beat, how percussive it is and how it ends.
 */
final class AudioMetrics {

	private AudioMetrics() {
	}

	/**
	 * Mono mix of all channels
	 */
	static float[] mono( AudioBuffer audio ) {
		float[] out = new float[ audio.getFrames() ];
		for ( int ch = 0; ch < audio.getChannels(); ch++ ) {
			float[] in = audio.channel( ch );
			for ( int i = 0; i < out.length; i++ ) {
				out[ i ] += in[ i ] / audio.getChannels();
			}
		}
		return out;
	}

	static double rms( float[] data ) {
		double sum = 0;
		for ( float v : data ) {
			sum += ( double ) v * v;
		}
		return data.length == 0 ? 0 : Math.sqrt( sum / data.length );
	}

	/**
	 * Level of everything above 5 kHz relative to the whole signal, in decibels. About -20 is a bright, hat heavy track and below -35 is dark.
	 */
	static double brightnessDb( AudioBuffer audio ) {
		AudioBuffer	high	= Dsp.filter( Dsp.filter( audio, "highpass", 5000, 0.7071 ), "highpass", 5000, 0.7071 );
		double		total	= rms( mono( audio ) );
		return 20 * Math.log10( Math.max( 1e-9, rms( mono( high ) ) ) / total );
	}

	/**
	 * RMS in consecutive windows
	 */
	static double[] envelope( float[] data, int sampleRate, double windowSeconds ) {
		int			window	= ( int ) ( windowSeconds * sampleRate );
		double[]	out		= new double[ data.length / window ];
		for ( int w = 0; w < out.length; w++ ) {
			double sum = 0;
			for ( int i = w * window; i < ( w + 1 ) * window; i++ ) {
				sum += ( double ) data[ i ] * data[ i ];
			}
			out[ w ] = Math.sqrt( sum / window );
		}
		return out;
	}

	/**
	 * How strongly the low end repeats at a given lag, from -1 to 1: the envelope of everything below 150 Hz is compared with itself shifted
	 * by the lag. A kick on every beat gives a high value at the beat length.
	 */
	static double lowBandAutocorrelation( AudioBuffer audio, double lagSeconds ) {
		AudioBuffer	low		= Dsp.filter( Dsp.filter( audio, "lowpass", 150, 0.7071 ), "lowpass", 150, 0.7071 );
		double[]	env		= envelope( mono( low ), audio.getSampleRate(), 0.01 );
		int			lag		= ( int ) Math.round( lagSeconds / 0.01 );
		double		mean	= 0;
		for ( double v : env ) {
			mean += v / env.length;
		}
		double num = 0, den = 0;
		for ( int i = 0; i < env.length; i++ ) {
			den += ( env[ i ] - mean ) * ( env[ i ] - mean );
			if ( i + lag < env.length ) {
				num += ( env[ i ] - mean ) * ( env[ i + lag ] - mean );
			}
		}
		return num / den;
	}

	/**
	 * The biggest rise in loudness between neighbouring 20 ms windows, relative to the average loudness. Percussive music has sharp rises
	 * and gives a high value, pads and drones give a low one.
	 */
	static double onsetStrength( AudioBuffer audio ) {
		double[]	env		= envelope( mono( audio ), audio.getSampleRate(), 0.02 );
		double		mean	= 0, maxRise = 0;
		for ( int i = 0; i < env.length; i++ ) {
			mean += env[ i ] / env.length;
			if ( i > 0 ) {
				maxRise = Math.max( maxRise, env[ i ] - env[ i - 1 ] );
			}
		}
		return maxRise / mean;
	}

	/**
	 * RMS of the last stretch of the audio, to check that a piece fades out instead of stopping
	 */
	static double tailRms( AudioBuffer audio, double seconds ) {
		float[]	data	= mono( audio );
		int		from	= Math.max( 0, data.length - ( int ) ( seconds * audio.getSampleRate() ) );
		return rms( java.util.Arrays.copyOfRange( data, from, data.length ) );
	}

}
