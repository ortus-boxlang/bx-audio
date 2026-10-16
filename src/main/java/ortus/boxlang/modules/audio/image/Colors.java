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

import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Colors for the images: CSS style hex colors, and the color scales of a spectrogram
 */
public final class Colors {

	private static final int[]	MAGMA	= { 0x000004, 0x3b0f70, 0x8c2981, 0xde4968, 0xfe9f6d, 0xfcfdbf };
	private static final int[]	VIRIDIS	= { 0x440154, 0x3b528b, 0x21918c, 0x5ec962, 0xfde725 };

	private Colors() {
	}

	/**
	 * @param text #rgb, #rrggbb or #rrggbbaa, with or without the #
	 * @param what The name of the argument, for the message
	 *
	 * @return The color as 0xAARRGGBB
	 */
	public static int parse( String text, String what ) {
		String hex = text.trim().toLowerCase( Locale.ROOT );
		if ( hex.startsWith( "#" ) ) {
			hex = hex.substring( 1 );
		}
		if ( !hex.matches( "[0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8}" ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "The " + what + " must be a hex color such as #3b82f6, #fff or #3b82f680 but was [" + text + "]" );
		}
		if ( hex.length() == 3 ) {
			hex = "" + hex.charAt( 0 ) + hex.charAt( 0 ) + hex.charAt( 1 ) + hex.charAt( 1 ) + hex.charAt( 2 ) + hex.charAt( 2 );
		}
		int	rgb		= Integer.parseInt( hex.substring( 0, 6 ), 16 );
		int	alpha	= hex.length() == 8 ? Integer.parseInt( hex.substring( 6 ), 16 ) : 255;
		return alpha << 24 | rgb;
	}

	/**
	 * @return The color as #rrggbb
	 */
	public static String hex( int argb ) {
		return String.format( "#%06x", argb & 0xFFFFFF );
	}

	/**
	 * @return The opacity of a color, 0 to 1
	 */
	public static double opacity( int argb ) {
		return ( argb >>> 24 ) / 255.0;
	}

	/**
	 * A point of a color scale
	 *
	 * @param scale magma, viridis or gray
	 * @param t     0 for the quietest, 1 for the loudest
	 *
	 * @return The color as 0xFFRRGGBB
	 */
	public static int scale( String scale, double t ) {
		double	v			= Math.max( 0, Math.min( 1, t ) );
		int[]	stops		= switch ( scale ) {
								case "viridis" -> VIRIDIS;
								case "gray" -> new int[] { 0x000000, 0xFFFFFF };
								default -> MAGMA;
							};
		double	position	= v * ( stops.length - 1 );
		int		i			= Math.min( stops.length - 2, ( int ) position );
		double	f			= position - i;
		int		a			= stops[ i ];
		int		b			= stops[ i + 1 ];
		int		r			= ( int ) Math.round( ( ( a >> 16 ) & 255 ) * ( 1 - f ) + ( ( b >> 16 ) & 255 ) * f );
		int		g			= ( int ) Math.round( ( ( a >> 8 ) & 255 ) * ( 1 - f ) + ( ( b >> 8 ) & 255 ) * f );
		int		bl			= ( int ) Math.round( ( a & 255 ) * ( 1 - f ) + ( b & 255 ) * f );
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

}
