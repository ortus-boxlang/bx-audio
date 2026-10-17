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

import java.util.List;
import java.util.Locale;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * A parametric equalizer made of RBJ biquads
 */
public final class Eq {

	public static final List<String> BAND_TYPES = List.of( "peak", "lowshelf", "highshelf", "lowpass", "highpass", "notch" );

	private Eq() {
	}

	/**
	 * One band
	 *
	 * @param type   peak, lowshelf, highshelf, lowpass, highpass or notch
	 * @param freq   Center or corner frequency in Hz
	 * @param gainDb Boost or cut in dB, used by peak and the shelves
	 * @param q      Width: higher is narrower. 0.707 is a gentle shelf or a flat pass filter.
	 */
	public record Band( String type, double freq, double gainDb, double q ) {
	}

	/**
	 * Run the audio through the bands in order
	 */
	public static AudioBuffer apply( AudioBuffer audio, List<Band> bands ) {
		AudioBuffer current = audio;
		for ( Band band : bands ) {
			current = apply( current, band );
		}
		return bands.isEmpty() ? audio.copy() : current;
	}

	private static AudioBuffer apply( AudioBuffer audio, Band band ) {
		Dsp.requireFinite( band.freq(), "freq" );
		Dsp.requireFinite( band.q(), "q" );
		Dsp.requireDb( band.gainDb(), "gainDb" );
		String type = band.type().toLowerCase( Locale.ROOT );
		if ( !BAND_TYPES.contains( type ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Unknown EQ band type [" + band.type() + "]. Use one of " + BAND_TYPES );
		}
		int rate = audio.getSampleRate();
		if ( band.freq() <= 0 || band.freq() >= rate / 2.0 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "The band frequency must be above 0 and below " + rate / 2.0 + " Hz but was " + band.freq() );
		}
		if ( band.q() <= 0 || band.q() > 100 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Q must be above 0 and at most 100 but was " + band.q() );
		}
		if ( Math.abs( band.gainDb() ) > 36 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The band gain must be between -36 and 36 dB but was " + band.gainDb() );
		}
		double	w0		= 2 * Math.PI * band.freq() / rate;
		double	cos		= Math.cos( w0 );
		double	sin		= Math.sin( w0 );
		double	alpha	= sin / ( 2 * band.q() );
		double	a		= Math.pow( 10, band.gainDb() / 40 );
		double	b0, b1, b2, a0, a1, a2;
		switch ( type ) {
			case "peak" -> {
				b0	= 1 + alpha * a;
				b1	= -2 * cos;
				b2	= 1 - alpha * a;
				a0	= 1 + alpha / a;
				a1	= -2 * cos;
				a2	= 1 - alpha / a;
			}
			case "lowshelf" -> {
				double s = 2 * Math.sqrt( a ) * alpha;
				b0	= a * ( ( a + 1 ) - ( a - 1 ) * cos + s );
				b1	= 2 * a * ( ( a - 1 ) - ( a + 1 ) * cos );
				b2	= a * ( ( a + 1 ) - ( a - 1 ) * cos - s );
				a0	= ( a + 1 ) + ( a - 1 ) * cos + s;
				a1	= -2 * ( ( a - 1 ) + ( a + 1 ) * cos );
				a2	= ( a + 1 ) + ( a - 1 ) * cos - s;
			}
			case "highshelf" -> {
				double s = 2 * Math.sqrt( a ) * alpha;
				b0	= a * ( ( a + 1 ) + ( a - 1 ) * cos + s );
				b1	= -2 * a * ( ( a - 1 ) + ( a + 1 ) * cos );
				b2	= a * ( ( a + 1 ) + ( a - 1 ) * cos - s );
				a0	= ( a + 1 ) - ( a - 1 ) * cos + s;
				a1	= 2 * ( ( a - 1 ) - ( a + 1 ) * cos );
				a2	= ( a + 1 ) - ( a - 1 ) * cos - s;
			}
			case "lowpass" -> {
				b0	= ( 1 - cos ) / 2;
				b1	= 1 - cos;
				b2	= ( 1 - cos ) / 2;
				a0	= 1 + alpha;
				a1	= -2 * cos;
				a2	= 1 - alpha;
			}
			case "highpass" -> {
				b0	= ( 1 + cos ) / 2;
				b1	= - ( 1 + cos );
				b2	= ( 1 + cos ) / 2;
				a0	= 1 + alpha;
				a1	= -2 * cos;
				a2	= 1 - alpha;
			}
			default -> {
				b0	= 1;
				b1	= -2 * cos;
				b2	= 1;
				a0	= 1 + alpha;
				a1	= -2 * cos;
				a2	= 1 - alpha;
			}
		}
		b0	/= a0;
		b1	/= a0;
		b2	/= a0;
		a1	/= a0;
		a2	/= a0;
		float[][] out = new float[ audio.getChannels() ][ audio.getFrames() ];
		for ( int ch = 0; ch < out.length; ch++ ) {
			float[]	in	= audio.channel( ch );
			double	x1	= 0, x2 = 0, y1 = 0, y2 = 0;
			for ( int i = 0; i < in.length; i++ ) {
				double	x	= in[ i ];
				double	y	= b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
				x2				= x1;
				x1				= x;
				y2				= y1;
				y1				= y;
				out[ ch ][ i ]	= ( float ) y;
			}
		}
		return new AudioBuffer( out, rate );
	}

}
