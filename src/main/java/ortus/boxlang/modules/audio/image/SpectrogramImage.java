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

import ortus.boxlang.modules.audio.analysis.Spectrum;
import ortus.boxlang.modules.audio.types.AudioBuffer;

/**
 * A picture of how the frequencies of a sound change over time: time across, frequency up (low at the bottom, on a log scale), loudness as color
 */
public final class SpectrogramImage {

	private SpectrogramImage() {
	}

	/**
	 * @param width   Pixels across, one slice of time each
	 * @param height  Pixels up, one band of frequencies each
	 * @param minHz   The lowest frequency
	 * @param maxHz   The highest frequency
	 * @param rangeDb How many dB below the loudest point of the picture still show
	 * @param scale   magma, viridis or gray
	 */
	public record Options( int width, int height, double minHz, double maxHz, double rangeDb, String scale ) {
	}

	public static int[] pixels( AudioBuffer audio, Options o ) {
		double[][]	grid	= Spectrum.spectrogram( audio, o.height(), o.width(), o.minHz(), o.maxHz() );
		double		loudest	= Spectrum.FLOOR_DB;
		for ( double[] column : grid ) {
			for ( double v : column ) {
				loudest = Math.max( loudest, v );
			}
		}
		// Silence has nothing to show: it stays dark instead of every point being "the loudest"
		boolean	silent	= loudest <= Spectrum.FLOOR_DB + 1;
		double	floor	= loudest - o.rangeDb();
		int[]	pixels	= new int[ o.width() * o.height() ];
		for ( int x = 0; x < o.width(); x++ ) {
			for ( int band = 0; band < o.height(); band++ ) {
				double t = silent ? 0 : ( grid[ x ][ band ] - floor ) / o.rangeDb();
				pixels[ ( o.height() - 1 - band ) * o.width() + x ] = Colors.scale( o.scale(), t );
			}
		}
		return pixels;
	}

	/**
	 * An SVG that holds the picture, so it can be placed and scaled like any other SVG. A spectrogram has a color for every point, which as vector
	 * shapes would be hundreds of thousands of them.
	 */
	public static String svg( int width, int height, byte[] png, double seconds ) {
		return "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"" + width + "\" height=\"" + height
		    + "\" viewBox=\"0 0 " + width + ' ' + height + "\" role=\"img\" aria-label=\"Spectrogram of "
		    + String.format( java.util.Locale.ROOT, "%.1f", seconds )
		    + " seconds\"><image width=\"" + width + "\" height=\"" + height + "\" preserveAspectRatio=\"none\" href=\"data:image/png;base64,"
		    + java.util.Base64.getEncoder().encodeToString( png ) + "\"/></svg>";
	}

}
