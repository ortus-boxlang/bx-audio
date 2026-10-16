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
package ortus.boxlang.modules.audio.synth;

import java.util.List;
import java.util.Locale;
import java.util.Random;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Synthesised percussion. Every voice is built from sine sweeps and shaped noise, so it needs no samples and is the same on every
 * machine. Each result is scaled so its loudest sample equals the requested level.
 */
public final class Drums {

	public static final List<String> TYPES = List.of( "kick", "snare", "hat", "openhat", "clap", "tom", "crash", "riser" );

	private Drums() {
	}

	/**
	 * @param type       One of {@link #TYPES}
	 * @param sampleRate Sample rate in Hz
	 * @param seed       Seed for the noise, the same seed always gives the same hit
	 * @param pitch      Multiplier for the tuned part of kick, snare and tom, 1 is the default tuning
	 * @param decay      Multiplier for how long the hit rings, 1 is the default and above 1 is longer
	 * @param level      Peak level of the result, from 0 to 1
	 * @param seconds    Length for the riser only, ignored by the others
	 */
	public static AudioBuffer drum( String type, int sampleRate, long seed, double pitch, double decay, double level, double seconds ) {
		String kind = type == null ? "" : type.toLowerCase( Locale.ROOT );
		if ( !TYPES.contains( kind ) ) {
			throw new BoxRuntimeException( "Unknown drum type [" + type + "]. Use one of " + TYPES );
		}
		Synth.requireFinite( pitch, "pitch" );
		Synth.requireFinite( decay, "decay" );
		Synth.requireFinite( level, "level" );
		if ( pitch <= 0 || pitch > 8 || decay <= 0 || decay > 8 ) {
			throw new BoxRuntimeException( "Pitch and decay must be above 0 and at most 8 but were " + pitch + " and " + decay );
		}
		if ( level < 0 || level > 1 ) {
			throw new BoxRuntimeException( "Level must be between 0 and 1 but was " + level );
		}
		double[] out = switch ( kind ) {
			case "kick" -> kick( sampleRate, pitch, decay );
			case "snare" -> snare( sampleRate, seed, pitch, decay );
			case "hat" -> burst( sampleRate, seed, 0.06 * decay, 70 / decay );
			case "openhat" -> burst( sampleRate, seed, 0.22 * decay, 18 / decay );
			case "clap" -> clap( sampleRate, seed, decay );
			case "tom" -> tom( sampleRate, pitch, decay );
			case "crash" -> burst( sampleRate, seed, 1.5 * decay, 3 / decay );
			default -> riser( sampleRate, seed, seconds );
		};
		// A hit that is cut off while it still rings ends in a click, so the last few milliseconds fade out (the riser fades by its own envelope)
		if ( !kind.equals( "riser" ) ) {
			int fade = Math.min( out.length / 4, Math.max( 1, ( int ) Math.round( 0.012 * sampleRate ) ) );
			for ( int i = 0; i < fade; i++ ) {
				out[ out.length - 1 - i ] *= 0.5 - 0.5 * Math.cos( Math.PI * i / fade );
			}
		}
		double peak = 0;
		for ( double v : out ) {
			peak = Math.max( peak, Math.abs( v ) );
		}
		float[] samples = new float[ out.length ];
		if ( peak > 0 ) {
			for ( int i = 0; i < out.length; i++ ) {
				samples[ i ] = ( float ) ( out[ i ] / peak * level );
			}
		}
		return new AudioBuffer( new float[][] { samples }, sampleRate );
	}

	/**
	 * A sine that falls from about 170 Hz to 50 Hz in a few milliseconds, with a fast decay
	 */
	private static double[] kick( int sampleRate, double pitch, double decay ) {
		int			frames	= Synth.frameCount( 0.35 * decay, sampleRate );
		double[]	out		= new double[ frames ];
		double		phase	= 0;
		for ( int i = 0; i < frames; i++ ) {
			double t = ( double ) i / sampleRate;
			phase		+= 2 * Math.PI * ( 50 + 120 * Math.exp( -t * 28 ) ) * pitch / sampleRate;
			out[ i ]	= Math.sin( phase ) * Math.exp( -t * 9 / decay );
		}
		return out;
	}

	private static double[] tom( int sampleRate, double pitch, double decay ) {
		int			frames	= Synth.frameCount( 0.4 * decay, sampleRate );
		double[]	out		= new double[ frames ];
		double		phase	= 0;
		for ( int i = 0; i < frames; i++ ) {
			double t = ( double ) i / sampleRate;
			phase		+= 2 * Math.PI * ( 80 + 80 * Math.exp( -t * 14 ) ) * pitch / sampleRate;
			out[ i ]	= Math.sin( phase ) * Math.exp( -t * 7 / decay );
		}
		return out;
	}

	/**
	 * A tuned body plus a burst of white noise
	 */
	private static double[] snare( int sampleRate, long seed, double pitch, double decay ) {
		int			frames	= Synth.frameCount( 0.25 * decay, sampleRate );
		Random		rng		= new Random( seed );
		double[]	out		= new double[ frames ];
		for ( int i = 0; i < frames; i++ ) {
			double t = ( double ) i / sampleRate;
			out[ i ] = 0.6 * Math.sin( 2 * Math.PI * 185 * pitch * t ) * Math.exp( -t * 28 / decay ) + 0.8 * rng.nextGaussian() * Math.exp( -t * 16 / decay );
		}
		return out;
	}

	/**
	 * A decaying burst of high pass noise. Used for hats and cymbals, longer and slower makes it open.
	 */
	private static double[] burst( int sampleRate, long seed, double seconds, double rate ) {
		int			frames	= Synth.frameCount( seconds, sampleRate );
		Random		rng		= new Random( seed );
		double[]	out		= new double[ frames ];
		double		last	= 0;
		for ( int i = 0; i < frames; i++ ) {
			double w = rng.nextGaussian();
			// the difference of neighbouring samples removes the low end
			out[ i ]	= ( w - last ) * Math.exp( - ( ( double ) i / sampleRate ) * rate );
			last		= w;
		}
		return out;
	}

	/**
	 * Noise through two sharp bursts a few milliseconds apart, which is what makes a clap sound like several hands
	 */
	private static double[] clap( int sampleRate, long seed, double decay ) {
		int			frames	= Synth.frameCount( 0.2 * decay, sampleRate );
		int			shift	= ( int ) Math.round( 0.012 * sampleRate );
		Random		rng		= new Random( seed );
		double[]	out		= new double[ frames ];
		for ( int i = 0; i < frames; i++ ) {
			double envelope = burstEnvelope( ( double ) i / sampleRate, decay );
			if ( i >= shift ) {
				envelope += 0.6 * burstEnvelope( ( double ) ( i - shift ) / sampleRate, decay );
			}
			out[ i ] = rng.nextGaussian() * envelope;
		}
		return out;
	}

	private static double burstEnvelope( double t, double decay ) {
		return Math.min( 1.0, t / 0.001 ) * Math.exp( -t / ( 0.05 * decay ) );
	}

	/**
	 * Noise that swells towards the end, for building tension
	 */
	private static double[] riser( int sampleRate, long seed, double seconds ) {
		if ( Double.isNaN( seconds ) || seconds <= 0 ) {
			throw new BoxRuntimeException( "A riser needs a length in seconds above 0 but was " + seconds );
		}
		int			frames	= Synth.frameCount( seconds, sampleRate );
		Random		rng		= new Random( seed );
		double[]	out		= new double[ frames ];
		double		last	= 0;
		for ( int i = 0; i < frames; i++ ) {
			double	w	= rng.nextGaussian();
			double	x	= ( double ) i / frames;
			out[ i ]	= ( w - last ) * x * x;
			last		= w;
		}
		return out;
	}

}
