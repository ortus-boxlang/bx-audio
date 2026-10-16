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
package ortus.boxlang.modules.audio.curve;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ortus.boxlang.modules.audio.util.AudioException;

/**
 * The curves a script can make: a low frequency oscillator, or a line through points
 */
public final class Curves {

	public static final List<String>	LFO_SHAPES		= List.of( "sine", "triangle", "saw", "square", "random" );
	public static final List<String>	INTERPOLATIONS	= List.of( "linear", "smooth", "step" );

	private Curves() {
	}

	/**
	 * A point of a line: the time and the value there
	 */
	public record Point( double time, double value ) {
	}

	private static void finite( double v, String what ) {
		if ( Double.isNaN( v ) || Double.isInfinite( v ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The " + what + " must be a finite number but was " + v );
		}
	}

	/**
	 * A low frequency oscillator: {@code center + depth * wave}, which swings between center - depth and center + depth. Every shape starts at the
	 * center (a square starts high) and a cycle is one over the rate seconds.
	 *
	 * @param shape  sine, triangle, saw (a ramp up that jumps back), square or random (a new random value each cycle, held)
	 * @param rate   Cycles per second, above 0 and at most 1000
	 * @param depth  How far it swings from the center
	 * @param center The value it swings around
	 * @param phase  Where in the cycle it starts, 0 to 1
	 * @param fadeIn Seconds it takes the swing to grow from nothing to its depth, 0 for no fade
	 * @param seed   For random: the same seed gives the same values
	 */
	public static Curve lfo( String shape, double rate, double depth, double center, double phase, double fadeIn, long seed ) {
		String kind = shape == null ? "" : shape.toLowerCase( Locale.ROOT );
		if ( !LFO_SHAPES.contains( kind ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Unknown LFO shape [" + shape + "]. Use one of " + LFO_SHAPES );
		}
		finite( rate, "rate" );
		finite( depth, "depth" );
		finite( center, "center" );
		finite( phase, "phase" );
		finite( fadeIn, "fadeIn" );
		if ( rate <= 0 || rate > 1000 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The rate must be above 0 and at most 1000 cycles per second but was " + rate );
		}
		if ( phase < 0 || phase > 1 || fadeIn < 0 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The phase must be between 0 and 1 and the fade in cannot be negative" );
		}
		return seconds -> {
			double	cycles	= phase + rate * Math.max( 0, seconds );
			double	whole	= Math.floor( cycles );
			double	x		= cycles - whole;
			double	wave	= switch ( kind ) {
								case "sine" -> Math.sin( 2 * Math.PI * x );
								case "triangle" -> x < 0.25 ? 4 * x : x < 0.75 ? 2 - 4 * x : 4 * x - 4;
								case "saw" -> x < 0.5 ? 2 * x : 2 * x - 2;
								case "square" -> x < 0.5 ? 1 : -1;
								default -> random( seed, ( long ) whole );
							};
			double	grow	= fadeIn > 0 ? Math.min( 1, Math.max( 0, seconds ) / fadeIn ) : 1;
			return center + depth * wave * grow;
		};
	}

	/**
	 * A value between -1 and 1 that depends only on the seed and the cycle, so a random LFO is the same every time
	 */
	private static double random( long seed, long cycle ) {
		long z = seed * 0x9E3779B97F4A7C15L + cycle * 0xBF58476D1CE4E5B9L + 0x94D049BB133111EBL;
		z	= ( z ^ ( z >>> 30 ) ) * 0xBF58476D1CE4E5B9L;
		z	= ( z ^ ( z >>> 27 ) ) * 0x94D049BB133111EBL;
		z	= z ^ ( z >>> 31 );
		return ( ( z >>> 11 ) / ( double ) ( 1L << 53 ) ) * 2 - 1;
	}

	/**
	 * A line through points. Before the first point it has the first value, after the last it keeps the last.
	 *
	 * @param points        The points, in any order, with different times
	 * @param interpolation linear (straight lines), smooth (an eased curve between points) or step (holds each value until the next point)
	 */
	public static Curve points( List<Point> points, String interpolation ) {
		String kind = interpolation == null ? "" : interpolation.toLowerCase( Locale.ROOT );
		if ( !INTERPOLATIONS.contains( kind ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Unknown interpolation [" + interpolation + "]. Use one of " + INTERPOLATIONS );
		}
		if ( points == null || points.isEmpty() ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "A curve needs at least one point" );
		}
		List<Point> sorted = new ArrayList<>( points );
		for ( Point p : sorted ) {
			finite( p.time(), "time of a point" );
			finite( p.value(), "value of a point" );
			if ( p.time() < 0 ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT, "The time of a point cannot be negative but was " + p.time() );
			}
		}
		sorted.sort( java.util.Comparator.comparingDouble( Point::time ) );
		for ( int i = 1; i < sorted.size(); i++ ) {
			if ( sorted.get( i ).time() == sorted.get( i - 1 ).time() ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT, "Two points are at the same time, " + sorted.get( i ).time() + " seconds" );
			}
		}
		double[]	times	= sorted.stream().mapToDouble( Point::time ).toArray();
		double[]	values	= sorted.stream().mapToDouble( Point::value ).toArray();
		return seconds -> {
			if ( seconds <= times[ 0 ] ) {
				return values[ 0 ];
			}
			int last = times.length - 1;
			if ( seconds >= times[ last ] ) {
				return values[ last ];
			}
			int lo = 0, hi = last;
			while ( hi - lo > 1 ) {
				int mid = ( lo + hi ) >>> 1;
				if ( times[ mid ] <= seconds ) {
					lo = mid;
				} else {
					hi = mid;
				}
			}
			if ( kind.equals( "step" ) ) {
				return values[ lo ];
			}
			double f = ( seconds - times[ lo ] ) / ( times[ hi ] - times[ lo ] );
			if ( kind.equals( "smooth" ) ) {
				f = f * f * ( 3 - 2 * f );
			}
			return values[ lo ] + ( values[ hi ] - values[ lo ] ) * f;
		};
	}

}
