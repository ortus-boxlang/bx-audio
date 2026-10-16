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
package ortus.boxlang.modules.audio.image;

import java.util.Locale;

import ortus.boxlang.modules.audio.types.AudioBuffer;

/**
 * A picture of the loudness of a sound over time: for each column of the picture the highest and lowest sample (the peaks, drawn pale) and the
 * average level (the RMS, drawn solid). As an SVG it scales to any size, as a PNG it can go where SVG cannot.
 */
public final class WaveformImage {

	private static final double PEAK_OPACITY = 0.4;

	private WaveformImage() {
	}

	/**
	 * What to draw
	 *
	 * @param width      Pixels across
	 * @param height     Pixels down
	 * @param color      The waveform, 0xAARRGGBB
	 * @param background Behind it, 0xAARRGGBB, fully transparent for none
	 * @param bars       Draw separate bars instead of a filled shape
	 * @param split      Give each channel its own lane instead of drawing them over one another
	 */
	public record Options( int width, int height, int color, int background, boolean bars, boolean split ) {
	}

	/**
	 * The minimum and maximum sample and the root mean square for each column
	 */
	private record Lane( float[] min, float[] max, float[] rms ) {
	}

	private static Lane[] lanes( AudioBuffer audio, int columns, boolean split ) {
		int		channels	= audio.getChannels();
		int		count		= split ? channels : 1;
		int		frames		= audio.getFrames();
		Lane[]	lanes		= new Lane[ count ];
		for ( int lane = 0; lane < count; lane++ ) {
			float[]	min	= new float[ columns ];
			float[]	max	= new float[ columns ];
			float[]	rms	= new float[ columns ];
			for ( int c = 0; c < columns; c++ ) {
				int	from	= ( int ) ( ( long ) c * frames / columns );
				int	to		= Math.max( from + 1, ( int ) ( ( long ) ( c + 1 ) * frames / columns ) );
				to = Math.min( to, frames );
				float	lo	= 0;
				float	hi	= 0;
				double	sum	= 0;
				int		n	= 0;
				for ( int ch = split ? lane : 0; ch < ( split ? lane + 1 : channels ); ch++ ) {
					float[] data = audio.channel( ch );
					for ( int i = from; i < to; i++ ) {
						float v = data[ i ];
						if ( v < lo ) {
							lo = v;
						}
						if ( v > hi ) {
							hi = v;
						}
						sum += ( double ) v * v;
						n++;
					}
				}
				min[ c ]	= lo;
				max[ c ]	= hi;
				rms[ c ]	= n == 0 ? 0 : ( float ) Math.sqrt( sum / n );
			}
			lanes[ lane ] = new Lane( min, max, rms );
		}
		return lanes;
	}

	private static double y( double amplitude, double center, double half ) {
		return center - Math.max( -1, Math.min( 1, amplitude ) ) * half;
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// SVG
	// ---------------------------------------------------------------------------------------------------------------------------

	private static String number( double v ) {
		String s = String.format( Locale.ROOT, "%.2f", v );
		if ( s.indexOf( '.' ) >= 0 ) {
			s = s.replaceAll( "0+$", "" ).replaceAll( "\\.$", "" );
		}
		return s;
	}

	public static String svg( AudioBuffer audio, Options o ) {
		Lane[]			lanes	= lanes( audio, o.bars() ? Math.max( 1, o.width() / 4 ) : o.width(), o.split() );
		StringBuilder	svg		= new StringBuilder( 4096 );
		svg.append( "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" ).append( o.width() ).append( "\" height=\"" ).append( o.height() )
		    .append( "\" viewBox=\"0 0 " ).append( o.width() ).append( ' ' ).append( o.height() ).append( "\" role=\"img\" aria-label=\"Waveform of " )
		    .append( number( audio.getDurationSeconds() ) ).append( " seconds\">" );
		if ( Colors.opacity( o.background() ) > 0 ) {
			svg.append( "<rect width=\"100%\" height=\"100%\" fill=\"" ).append( Colors.hex( o.background() ) ).append( "\" fill-opacity=\"" )
			    .append( number( Colors.opacity( o.background() ) ) ).append( "\"/>" );
		}
		String	fill	= Colors.hex( o.color() );
		double	alpha	= Colors.opacity( o.color() );
		double	laneH	= ( double ) o.height() / lanes.length;
		for ( int l = 0; l < lanes.length; l++ ) {
			double	center	= l * laneH + laneH / 2;
			double	half	= laneH / 2 - 0.5;
			Lane	lane	= lanes[ l ];
			int		n		= lane.min().length;
			if ( o.bars() ) {
				svg.append( "<g fill=\"" ).append( fill ).append( "\"" ).append( alpha < 1 ? " fill-opacity=\"" + number( alpha ) + "\"" : "" ).append( ">" );
				for ( int c = 0; c < n; c++ ) {
					double	amp	= Math.max( Math.abs( lane.min()[ c ] ), Math.abs( lane.max()[ c ] ) );
					double	h	= Math.max( 2, Math.min( 1, amp ) * half * 2 );
					svg.append( "<rect x=\"" ).append( c * 4 ).append( "\" y=\"" ).append( number( center - h / 2 ) ).append( "\" width=\"3\" height=\"" )
					    .append( number( h ) ).append( "\"/>" );
				}
				svg.append( "</g>" );
			} else {
				svg.append( "<path fill=\"" ).append( fill ).append( "\" fill-opacity=\"" ).append( number( PEAK_OPACITY * alpha ) ).append( "\" d=\"" );
				envelope( svg, lane.max(), lane.min(), center, half, n, true );
				svg.append( "\"/><path fill=\"" ).append( fill ).append( "\"" ).append( alpha < 1 ? " fill-opacity=\"" + number( alpha ) + "\"" : "" )
				    .append( " d=\"" );
				float[] negative = new float[ n ];
				for ( int c = 0; c < n; c++ ) {
					negative[ c ] = -lane.rms()[ c ];
				}
				envelope( svg, lane.rms(), negative, center, half, n, false );
				svg.append( "\"/>" );
			}
		}
		return svg.append( "</svg>" ).toString();
	}

	/**
	 * A closed shape along the upper edge and back along the lower one
	 */
	private static void envelope( StringBuilder svg, float[] top, float[] bottom, double center, double half, int n, boolean atLeastAPixel ) {
		double[]	yt	= new double[ n ];
		double[]	yb	= new double[ n ];
		for ( int c = 0; c < n; c++ ) {
			yt[ c ]	= y( top[ c ], center, half );
			yb[ c ]	= y( bottom[ c ], center, half );
			if ( atLeastAPixel && yb[ c ] - yt[ c ] < 1 ) {
				double mid = ( yt[ c ] + yb[ c ] ) / 2;
				yt[ c ]	= mid - 0.5;
				yb[ c ]	= mid + 0.5;
			}
		}
		for ( int c = 0; c < n; c++ ) {
			svg.append( c == 0 ? 'M' : 'L' ).append( number( c + 0.5 ) ).append( ' ' ).append( number( yt[ c ] ) );
		}
		for ( int c = n - 1; c >= 0; c-- ) {
			svg.append( 'L' ).append( number( c + 0.5 ) ).append( ' ' ).append( number( yb[ c ] ) );
		}
		svg.append( 'Z' );
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// PNG
	// ---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Draw a pixel over what is there
	 */
	private static void blend( int[] pixels, int index, int color, double opacity ) {
		double sa = ( ( color >>> 24 ) / 255.0 ) * opacity;
		if ( sa <= 0 ) {
			return;
		}
		int		dst	= pixels[ index ];
		double	da	= ( dst >>> 24 ) / 255.0;
		double	oa	= sa + da * ( 1 - sa );
		int		r	= ( int ) Math.round( ( ( ( color >> 16 ) & 255 ) * sa + ( ( dst >> 16 ) & 255 ) * da * ( 1 - sa ) ) / oa );
		int		g	= ( int ) Math.round( ( ( ( color >> 8 ) & 255 ) * sa + ( ( dst >> 8 ) & 255 ) * da * ( 1 - sa ) ) / oa );
		int		b	= ( int ) Math.round( ( ( color & 255 ) * sa + ( dst & 255 ) * da * ( 1 - sa ) ) / oa );
		pixels[ index ] = ( int ) Math.round( oa * 255 ) << 24 | r << 16 | g << 8 | b;
	}

	/**
	 * Fill a vertical run of one column from y0 to y1, where the first and last pixel are covered by the fraction of them the run touches
	 */
	private static void span( int[] pixels, int width, int height, int x, double y0, double y1, int color, double opacity ) {
		double	from	= Math.max( 0, Math.min( height, y0 ) );
		double	to		= Math.max( 0, Math.min( height, y1 ) );
		for ( int row = ( int ) from; row < Math.ceil( to ); row++ ) {
			double coverage = Math.min( to, row + 1 ) - Math.max( from, row );
			if ( coverage > 0 ) {
				blend( pixels, row * width + x, color, opacity * coverage );
			}
		}
	}

	public static int[] pixels( AudioBuffer audio, Options o ) {
		int[] pixels = new int[ o.width() * o.height() ];
		if ( Colors.opacity( o.background() ) > 0 ) {
			java.util.Arrays.fill( pixels, o.background() );
		}
		Lane[]	lanes	= lanes( audio, o.bars() ? Math.max( 1, o.width() / 4 ) : o.width(), o.split() );
		double	laneH	= ( double ) o.height() / lanes.length;
		for ( int l = 0; l < lanes.length; l++ ) {
			double	center	= l * laneH + laneH / 2;
			double	half	= laneH / 2 - 0.5;
			Lane	lane	= lanes[ l ];
			if ( o.bars() ) {
				for ( int c = 0; c < lane.min().length; c++ ) {
					double	amp	= Math.max( Math.abs( lane.min()[ c ] ), Math.abs( lane.max()[ c ] ) );
					double	h	= Math.max( 2, Math.min( 1, amp ) * half * 2 );
					for ( int dx = 0; dx < 3 && c * 4 + dx < o.width(); dx++ ) {
						span( pixels, o.width(), o.height(), c * 4 + dx, center - h / 2, center + h / 2, o.color(), 1 );
					}
				}
			} else {
				for ( int x = 0; x < o.width(); x++ ) {
					double	top		= y( lane.max()[ x ], center, half );
					double	bottom	= y( lane.min()[ x ], center, half );
					if ( bottom - top < 1 ) {
						double mid = ( top + bottom ) / 2;
						top		= mid - 0.5;
						bottom	= mid + 0.5;
					}
					span( pixels, o.width(), o.height(), x, top, bottom, o.color(), PEAK_OPACITY );
					double r = lane.rms()[ x ];
					span( pixels, o.width(), o.height(), x, y( r, center, half ), y( -r, center, half ), o.color(), 1 );
				}
			}
		}
		return pixels;
	}

}
