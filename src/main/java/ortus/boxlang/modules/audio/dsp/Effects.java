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

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Time based effects: echo, reverb and chorus. Every effect returns a new buffer. Echo and reverb make the result longer than the input
 * so the tail rings out instead of being cut off.
 */
public final class Effects {

	/**
	 * Longest tail added when it is worked out automatically, in seconds
	 */
	public static final double	MAX_AUTO_TAIL_SECONDS	= 10;

	/**
	 * Longest tail that can be requested, in seconds
	 */
	public static final double	MAX_TAIL_SECONDS		= 60;

	// Freeverb tuning, lengths in samples at 44.1 kHz (Jezar at Dreampoint, public domain)
	private static final int[]	COMB_TUNING				= { 1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617 };
	private static final int[]	ALLPASS_TUNING			= { 556, 441, 341, 225 };
	private static final int	STEREO_SPREAD			= 23;
	private static final double	FIXED_GAIN				= 0.015;
	private static final double	WET_SCALE				= 3.0;
	private static final double	ALLPASS_FEEDBACK		= 0.5;

	/**
	 * Base delay of the chorus in seconds, the modulation swings around it
	 */
	private static final double	CHORUS_BASE_SECONDS		= 0.02;

	private Effects() {
	}

	/**
	 * Echo with feedback.
	 *
	 * @param audio       The input
	 * @param time        Seconds between repeats, from 0.001 to 5
	 * @param feedback    How much of each repeat comes back, from 0 (one echo) to 0.95
	 * @param mix         Dry/wet balance, 0 is only the input and 1 is only the echoes
	 * @param pingPong    Bounce the repeats between left and right. Needs stereo input.
	 * @param tailSeconds Extra length for the repeats to ring out, or null to run until they are 60 dB down (at most 10 s)
	 */
	public static AudioBuffer delay( AudioBuffer audio, double time, double feedback, double mix, boolean pingPong, Double tailSeconds ) {
		requireFinite( time, "time" );
		requireFinite( feedback, "feedback" );
		requireFinite( mix, "mix" );
		if ( time < 0.001 || time > 5 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The delay time must be between 0.001 and 5 seconds but was " + time );
		}
		if ( feedback < 0 || feedback > 0.95 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Feedback must be between 0 and 0.95 but was " + feedback );
		}
		requireMix( mix );
		if ( pingPong && audio.getChannels() != 2 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "Ping pong needs stereo audio but this buffer has " + audio.getChannels() + " channel(s)" );
		}
		int	rate	= audio.getSampleRate();
		int	d		= ( int ) Math.max( 1, Math.round( time * rate ) );
		int	tail;
		if ( tailSeconds != null ) {
			tail = tailFrames( tailSeconds, rate );
		} else if ( feedback == 0 ) {
			tail = d;
		} else {
			double repeats = Math.ceil( Math.log( 0.001 ) / Math.log( feedback ) );
			tail = ( int ) Math.max( d, Math.min( repeats * d, MAX_AUTO_TAIL_SECONDS * rate ) );
		}
		int			frames	= outputFrames( audio, tail );
		int			ch		= audio.getChannels();
		float[][]	out		= new float[ ch ][ frames ];
		float		dry		= ( float ) ( 1 - mix );
		float		wetGain	= ( float ) mix;

		if ( pingPong ) {
			float[]	wetL	= new float[ frames ];
			float[]	wetR	= new float[ frames ];
			float[]	left	= audio.channel( 0 );
			float[]	right	= audio.channel( 1 );
			for ( int n = d; n < frames; n++ ) {
				int		src	= n - d;
				float	m	= src < left.length ? ( left[ src ] + right[ src ] ) * 0.5f : 0f;
				wetR[ n ]	= m + ( float ) feedback * wetL[ src ];
				wetL[ n ]	= ( float ) feedback * wetR[ src ];
			}
			for ( int n = 0; n < frames; n++ ) {
				out[ 0 ][ n ]	= ( n < left.length ? left[ n ] * dry : 0f ) + wetL[ n ] * wetGain;
				out[ 1 ][ n ]	= ( n < right.length ? right[ n ] * dry : 0f ) + wetR[ n ] * wetGain;
			}
		} else {
			for ( int c = 0; c < ch; c++ ) {
				float[]	in	= audio.channel( c );
				float[]	wet	= new float[ frames ];
				for ( int n = d; n < frames; n++ ) {
					int src = n - d;
					wet[ n ] = ( src < in.length ? in[ src ] : 0f ) + ( float ) feedback * wet[ src ];
				}
				for ( int n = 0; n < frames; n++ ) {
					out[ c ][ n ] = ( n < in.length ? in[ n ] * dry : 0f ) + wet[ n ] * wetGain;
				}
			}
		}
		return new AudioBuffer( out, rate );
	}

	/**
	 * Reverb using the Freeverb design: eight parallel damped feedback combs into four series allpass filters. A stereo input gets a
	 * stereo-spread tail, a mono input stays mono.
	 *
	 * @param audio       The input, mono or stereo
	 * @param room        Size of the room from 0 (small) to 1 (very large, long tail)
	 * @param damp        High frequency absorption from 0 (bright) to 1 (dark and soft)
	 * @param mix         Dry/wet balance, 0 is only the input and 1 is only the reverb
	 * @param tailSeconds Extra length for the tail to ring out, or null to pick one from the room size
	 */
	public static AudioBuffer reverb( AudioBuffer audio, double room, double damp, double mix, Double tailSeconds ) {
		requireFinite( room, "room" );
		requireFinite( damp, "damp" );
		requireFinite( mix, "mix" );
		if ( room < 0 || room > 1 || damp < 0 || damp > 1 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Room and damp must be between 0 and 1 but were " + room + " and " + damp );
		}
		requireMix( mix );
		if ( audio.getChannels() > 2 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "Reverb works on mono or stereo audio but this buffer has " + audio.getChannels() + " channels" );
		}
		int			rate	= audio.getSampleRate();
		int			tail	= tailSeconds != null ? tailFrames( tailSeconds, rate ) : ( int ) Math.round( ( 0.5 + 6 * room ) * rate );
		int			frames	= outputFrames( audio, tail );
		int			ch		= audio.getChannels();

		// Mono feeds the sum of the channels in, as the original does
		double[]	input	= new double[ frames ];
		for ( int c = 0; c < ch; c++ ) {
			float[] in = audio.channel( c );
			for ( int n = 0; n < in.length; n++ ) {
				input[ n ] += in[ n ] * FIXED_GAIN * ( ch == 1 ? 2 : 1 );
			}
		}
		double		feedback	= room * 0.28 + 0.7;
		double		damping		= damp * 0.4;
		float[][]	out			= new float[ ch ][ frames ];
		float		dry			= ( float ) ( 1 - mix );
		for ( int c = 0; c < ch; c++ ) {
			double[]	wet	= freeverbChannel( input, rate, c == 0 ? 0 : STEREO_SPREAD, feedback, damping );
			float[]		in	= audio.channel( c );
			for ( int n = 0; n < frames; n++ ) {
				out[ c ][ n ] = ( n < in.length ? in[ n ] * dry : 0f ) + ( float ) ( wet[ n ] * WET_SCALE * mix );
			}
		}
		return new AudioBuffer( out, rate );
	}

	/**
	 * Chorus: mixes in a copy that has been through a short delay swung slowly up and down, which thickens and widens the sound. On
	 * stereo input the two sides swing a quarter cycle apart.
	 *
	 * @param audio The input
	 * @param rate  How fast the delay swings, in Hz, from 0.01 to 20
	 * @param depth How far the delay swings, in seconds, from 0 to 0.01
	 * @param mix   Dry/wet balance, 0 is only the input
	 */
	public static AudioBuffer chorus( AudioBuffer audio, double rate, double depth, double mix ) {
		requireFinite( rate, "rate" );
		requireFinite( depth, "depth" );
		requireFinite( mix, "mix" );
		if ( rate < 0.01 || rate > 20 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The chorus rate must be between 0.01 and 20 Hz but was " + rate );
		}
		if ( depth < 0 || depth > 0.01 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The chorus depth must be between 0 and 0.01 seconds but was " + depth );
		}
		requireMix( mix );
		int			sr		= audio.getSampleRate();
		int			frames	= audio.getFrames();
		float[][]	out		= new float[ audio.getChannels() ][ frames ];
		double		base	= CHORUS_BASE_SECONDS * sr;
		double		swing	= depth * sr;
		for ( int c = 0; c < out.length; c++ ) {
			float[]	in		= audio.channel( c );
			double	phase	= c * Math.PI / 2;
			for ( int n = 0; n < frames; n++ ) {
				double	pos	= n - ( base + swing * Math.sin( 2 * Math.PI * rate * n / sr + phase ) );
				double	wet	= 0;
				if ( pos >= 0 ) {
					int		i	= ( int ) pos;
					double	f	= pos - i;
					double	a	= in[ i ];
					double	b	= i + 1 < frames ? in[ i + 1 ] : 0;
					wet = a + ( b - a ) * f;
				}
				out[ c ][ n ] = ( float ) ( in[ n ] * ( 1 - mix ) + wet * mix );
			}
		}
		return new AudioBuffer( out, sr );
	}

	/**
	 * One side of Freeverb: the input through eight damped combs added together, then four allpass filters in a row
	 */
	private static double[] freeverbChannel( double[] input, int sampleRate, int spread, double feedback, double damping ) {
		double		scale		= sampleRate / 44100.0;
		double[][]	combBuf		= new double[ COMB_TUNING.length ][];
		int[]		combPos		= new int[ COMB_TUNING.length ];
		double[]	combStore	= new double[ COMB_TUNING.length ];
		for ( int i = 0; i < combBuf.length; i++ ) {
			combBuf[ i ] = new double[ Math.max( 1, ( int ) Math.round( ( COMB_TUNING[ i ] + spread ) * scale ) ) ];
		}
		double[][]	apBuf	= new double[ ALLPASS_TUNING.length ][];
		int[]		apPos	= new int[ ALLPASS_TUNING.length ];
		for ( int i = 0; i < apBuf.length; i++ ) {
			apBuf[ i ] = new double[ Math.max( 1, ( int ) Math.round( ( ALLPASS_TUNING[ i ] + spread ) * scale ) ) ];
		}
		double[] out = new double[ input.length ];
		for ( int n = 0; n < input.length; n++ ) {
			double sum = 0;
			for ( int i = 0; i < combBuf.length; i++ ) {
				double[]	buf	= combBuf[ i ];
				double		y	= buf[ combPos[ i ] ];
				combStore[ i ]		= y * ( 1 - damping ) + combStore[ i ] * damping;
				buf[ combPos[ i ] ]	= input[ n ] + combStore[ i ] * feedback;
				combPos[ i ]		= ( combPos[ i ] + 1 ) % buf.length;
				sum					+= y;
			}
			for ( int i = 0; i < apBuf.length; i++ ) {
				double[]	buf		= apBuf[ i ];
				double		held	= buf[ apPos[ i ] ];
				double		y		= -sum + held;
				buf[ apPos[ i ] ]	= sum + held * ALLPASS_FEEDBACK;
				apPos[ i ]			= ( apPos[ i ] + 1 ) % buf.length;
				sum					= y;
			}
			out[ n ] = sum;
		}
		return out;
	}

	private static int tailFrames( double seconds, int sampleRate ) {
		requireFinite( seconds, "tail" );
		if ( seconds < 0 || seconds > MAX_TAIL_SECONDS ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The tail must be between 0 and " + MAX_TAIL_SECONDS + " seconds but was " + seconds );
		}
		return ( int ) Math.round( seconds * sampleRate );
	}

	private static int outputFrames( AudioBuffer audio, int tail ) {
		long frames = ( long ) audio.getFrames() + tail;
		if ( frames * audio.getChannels() > Integer.MAX_VALUE - 8 ) {
			throw new AudioException( AudioException.TOO_LARGE, "The result would be too long to hold in memory" );
		}
		return ( int ) frames;
	}

	private static void requireMix( double mix ) {
		if ( mix < 0 || mix > 1 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Mix must be between 0 and 1 but was " + mix );
		}
	}

	private static void requireFinite( double value, String name ) {
		if ( Double.isNaN( value ) || Double.isInfinite( value ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The " + name + " argument must be a finite number but was " + value );
		}
	}

}
