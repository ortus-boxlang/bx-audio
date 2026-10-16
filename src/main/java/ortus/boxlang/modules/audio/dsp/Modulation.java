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

import java.util.Locale;

import ortus.boxlang.modules.audio.curve.Curve;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Changing a sound while it plays: its volume, its place in the stereo field, the cutoff of a filter or its pitch follows a {@link Curve}. With an
 * LFO as the curve these are tremolo, auto pan, a wah and vibrato. With points they are a fade to any shape, a sweep or a glide.
 */
public final class Modulation {

	public static final java.util.List<String>	PARAMS		= java.util.List.of( "gainDb", "pan", "cutoff", "pitch" );

	private static final double					MIN_DB		= -120;
	private static final double					MAX_DB		= 24;
	private static final int					BLOCK		= 16;
	private static final double					MAX_SECONDS	= 600;

	private Modulation() {
	}

	/**
	 * Apply a curve to a parameter of the audio
	 *
	 * @param param  gainDb (the curve is the gain in dB), pan (-1 to 1), cutoff (a filter's cutoff in Hz) or pitch (semitones)
	 * @param filter For cutoff: lowpass, highpass, bandpass or notch
	 * @param q      For cutoff: the resonance
	 */
	public static AudioBuffer apply( AudioBuffer audio, String param, Curve curve, String filter, double q ) {
		String kind = param == null ? "" : param.trim();
		return switch ( kind.toLowerCase( Locale.ROOT ) ) {
			case "gaindb" -> gain( audio, curve );
			case "pan" -> pan( audio, curve );
			case "cutoff" -> cutoff( audio, curve, filter, q );
			case "pitch" -> pitch( audio, curve );
			default -> throw new AudioException( AudioException.INVALID_ARGUMENT, "Unknown parameter [" + param + "]. Use one of " + PARAMS );
		};
	}

	/**
	 * @return The linear gain for a value in dB, clamped to a sane range so a curve cannot make a speaker blowing level or an infinity
	 */
	public static double linear( double db ) {
		if ( Double.isNaN( db ) ) {
			return 1;
		}
		return Math.pow( 10, Math.max( MIN_DB, Math.min( MAX_DB, db ) ) / 20 );
	}

	/**
	 * Equal power left and right gains for a pan position, for a mono source
	 */
	public static float[] panGains( double pan ) {
		double angle = ( Math.max( -1, Math.min( 1, Double.isNaN( pan ) ? 0 : pan ) ) + 1 ) * Math.PI / 4;
		return new float[] { ( float ) Math.cos( angle ), ( float ) Math.sin( angle ) };
	}

	/**
	 * Left and right gains of a balance control for a stereo source: the far side is turned down, the near side is left alone
	 */
	public static float[] balanceGains( double pan ) {
		double p = Math.max( -1, Math.min( 1, Double.isNaN( pan ) ? 0 : pan ) );
		return new float[] { p > 0 ? ( float ) Math.cos( p * Math.PI / 2 ) : 1f, p < 0 ? ( float ) Math.cos( -p * Math.PI / 2 ) : 1f };
	}

	private static AudioBuffer gain( AudioBuffer audio, Curve curve ) {
		int			rate	= audio.getSampleRate();
		float[][]	out		= new float[ audio.getChannels() ][ audio.getFrames() ];
		for ( int i = 0; i < audio.getFrames(); i++ ) {
			float g = ( float ) linear( curve.at( ( double ) i / rate ) );
			for ( int ch = 0; ch < out.length; ch++ ) {
				out[ ch ][ i ] = audio.channel( ch )[ i ] * g;
			}
		}
		return new AudioBuffer( out, rate );
	}

	private static AudioBuffer pan( AudioBuffer audio, Curve curve ) {
		int rate = audio.getSampleRate();
		if ( audio.getChannels() == 1 ) {
			float[][]	out	= new float[ 2 ][ audio.getFrames() ];
			float[]		in	= audio.channel( 0 );
			for ( int i = 0; i < in.length; i++ ) {
				float[] g = panGains( curve.at( ( double ) i / rate ) );
				out[ 0 ][ i ]	= in[ i ] * g[ 0 ];
				out[ 1 ][ i ]	= in[ i ] * g[ 1 ];
			}
			return new AudioBuffer( out, rate );
		}
		if ( audio.getChannels() == 2 ) {
			float[][] out = new float[ 2 ][ audio.getFrames() ];
			for ( int i = 0; i < audio.getFrames(); i++ ) {
				float[] g = balanceGains( curve.at( ( double ) i / rate ) );
				out[ 0 ][ i ]	= audio.channel( 0 )[ i ] * g[ 0 ];
				out[ 1 ][ i ]	= audio.channel( 1 )[ i ] * g[ 1 ];
			}
			return new AudioBuffer( out, rate );
		}
		throw new AudioException( AudioException.INVALID_ARGUMENT,
		    "Pan works on mono or stereo audio but this buffer has " + audio.getChannels() + " channels" );
	}

	/**
	 * A filter whose cutoff follows the curve. The coefficients are worked out every few samples, and the filter is the transposed form, which stays
	 * stable and does not click while its coefficients change.
	 */
	private static AudioBuffer cutoff( AudioBuffer audio, Curve curve, String filter, double q ) {
		String kind = filter == null ? "" : filter.toLowerCase( Locale.ROOT );
		if ( !Dsp.FILTER_TYPES.contains( kind ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Unknown filter type [" + filter + "]. Use one of " + Dsp.FILTER_TYPES );
		}
		if ( ! ( q > 0 && q <= 100 ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Q must be above 0 and at most 100 but was " + q );
		}
		int			rate			= audio.getSampleRate();
		double		top				= rate * 0.45;
		float[][]	out				= new float[ audio.getChannels() ][ audio.getFrames() ];
		double[][]	coefficients	= new double[ ( audio.getFrames() + BLOCK - 1 ) / BLOCK ][];
		for ( int b = 0; b < coefficients.length; b++ ) {
			double hz = curve.at( ( double ) ( b * BLOCK ) / rate );
			hz					= Double.isNaN( hz ) ? 1000 : Math.max( 10, Math.min( top, hz ) );
			coefficients[ b ]	= Dsp.biquad( kind, hz, q, rate );
		}
		for ( int ch = 0; ch < out.length; ch++ ) {
			float[]	in	= audio.channel( ch );
			double	z1	= 0, z2 = 0;
			for ( int i = 0; i < in.length; i++ ) {
				double[]	c	= coefficients[ i / BLOCK ];
				double		x	= in[ i ];
				double		y	= c[ 0 ] * x + z1;
				z1				= c[ 1 ] * x - c[ 3 ] * y + z2;
				z2				= c[ 2 ] * x - c[ 4 ] * y;
				out[ ch ][ i ]	= ( float ) y;
			}
		}
		return new AudioBuffer( out, rate );
	}

	/**
	 * Playback whose speed follows the curve (in semitones, 12 is twice as fast), like a tape or a record. With a curve that swings around 0 that is
	 * vibrato. A curve that is above 0 for a while makes the sound shorter and below 0 longer.
	 */
	private static AudioBuffer pitch( AudioBuffer audio, Curve curve ) {
		int	rate	= audio.getSampleRate();
		int	frames	= audio.getFrames();
		if ( frames < 4 ) {
			return audio.copy();
		}
		long		cap			= ( long ) ( MAX_SECONDS * rate );
		// the read position for every output sample: each step moves by the playback speed at that moment
		double[]	positions	= new double[ ( int ) Math.min( cap, frames * 8L + 16 ) ];
		int			count		= 0;
		double		pos			= 0;
		while ( pos <= frames - 1 && count < positions.length ) {
			positions[ count++ ] = pos;
			double semitones = curve.at( ( double ) count / rate );
			pos += Math.pow( 2, Math.max( -36, Math.min( 36, Double.isNaN( semitones ) ? 0 : semitones ) ) / 12 );
		}
		if ( pos <= frames - 1 ) {
			throw new AudioException( AudioException.TOO_LARGE,
			    "The pitch curve slows the sound so much that it would be longer than " + ( int ) MAX_SECONDS + " seconds" );
		}
		float[][] out = new float[ audio.getChannels() ][ count ];
		for ( int ch = 0; ch < out.length; ch++ ) {
			float[] in = audio.channel( ch );
			for ( int i = 0; i < count; i++ ) {
				out[ ch ][ i ] = cubic( in, positions[ i ] );
			}
		}
		return new AudioBuffer( out, rate );
	}

	/**
	 * Catmull-Rom interpolation between samples, so a read position between two samples does not add the harshness of a straight line
	 */
	private static float cubic( float[] in, double position ) {
		int		i	= ( int ) Math.floor( position );
		double	t	= position - i;
		double	p0	= in[ Math.max( 0, i - 1 ) ];
		double	p1	= in[ Math.min( in.length - 1, i ) ];
		double	p2	= in[ Math.min( in.length - 1, i + 1 ) ];
		double	p3	= in[ Math.min( in.length - 1, i + 2 ) ];
		return ( float ) ( 0.5 * ( 2 * p1 + ( -p0 + p2 ) * t + ( 2 * p0 - 5 * p1 + 4 * p2 - p3 ) * t * t + ( -p0 + 3 * p1 - 3 * p2 + p3 ) * t * t * t ) );
	}

}
