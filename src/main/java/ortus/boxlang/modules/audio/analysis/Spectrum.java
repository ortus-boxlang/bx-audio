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
package ortus.boxlang.modules.audio.analysis;

import ortus.boxlang.modules.audio.dsp.Fft;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Frequency content of audio: raw FFT magnitudes, levels in log spaced bands, and a spectrogram of those bands
 */
public final class Spectrum {

	public static final int		DEFAULT_SIZE	= 4096;
	public static final double	FLOOR_DB		= -120;

	private Spectrum() {
	}

	/**
	 * @param frequencies Center frequency of each bin
	 * @param magnitudes  Amplitude of each bin, 1 for a full scale sine
	 */
	public record Bins( double[] frequencies, double[] magnitudes ) {
	}

	/**
	 * The spectrum of one window of the mixed down signal, Hann weighted
	 *
	 * @param size  FFT size, a power of two
	 * @param start Where the window starts, in seconds
	 */
	public static Bins fft( AudioBuffer audio, int size, double start ) {
		Fft			fft		= new Fft( size );
		float[]		mono	= mono( audio );
		int			offset	= ( int ) Math.round( start * audio.getSampleRate() );
		double[]	mags	= fft.magnitudes( mono, offset, Fft.hann( size ) );
		double[]	freqs	= new double[ mags.length ];
		for ( int i = 0; i < freqs.length; i++ ) {
			freqs[ i ] = ( double ) i * audio.getSampleRate() / size;
		}
		return new Bins( freqs, mags );
	}

	/**
	 * Average level in dB of log spaced bands over a stretch of the audio
	 *
	 * @param bands   How many bands
	 * @param minHz   Lower edge of the first band
	 * @param maxHz   Upper edge of the last band, capped at Nyquist
	 * @param start   Start in seconds
	 * @param seconds Length in seconds, 0 for the rest of the audio
	 *
	 * @return One level per band in dB, {@value #FLOOR_DB} for nothing
	 */
	public static double[] bands( AudioBuffer audio, int bands, double minHz, double maxHz, double start, double seconds ) {
		validate( bands, minHz, maxHz, audio );
		Analysis a = new Analysis( audio, bands, minHz, maxHz );
		return a.levels( start, seconds );
	}

	/**
	 * What the band analysis needs and can reuse for many stretches: the mixed down signal, the FFT, its window and the band edges. Making
	 * these once matters for a spectrogram, which asks for hundreds of stretches of the same audio.
	 */
	private static final class Analysis {

		private final AudioBuffer	audio;
		private final int			bands;
		private final float[]		mono;
		private final Fft			fft		= new Fft( DEFAULT_SIZE );
		private final double[]		window	= Fft.hann( DEFAULT_SIZE );
		private final int[]			edges;

		Analysis( AudioBuffer audio, int bands, double minHz, double maxHz ) {
			this.audio	= audio;
			this.bands	= bands;
			this.mono	= mono( audio );
			this.edges	= edges( audio.getSampleRate(), bands, minHz, Math.min( maxHz, audio.getSampleRate() / 2.0 ) );
		}

		double[] levels( double start, double seconds ) {
			int			rate	= this.audio.getSampleRate();
			int			first	= ( int ) Math.round( start * rate );
			int			last	= seconds <= 0 ? this.audio.getFrames() : Math.min( this.audio.getFrames(), first + ( int ) Math.round( seconds * rate ) );
			double[]	power	= new double[ this.bands ];
			int			frames	= 0;
			for ( int pos = first; pos < Math.max( last, first + 1 ); pos += DEFAULT_SIZE / 2 ) {
				double[] mags = this.fft.magnitudes( this.mono, pos, this.window );
				for ( int b = 0; b < this.bands; b++ ) {
					double sum = 0;
					for ( int bin = this.edges[ b ]; bin < this.edges[ b + 1 ]; bin++ ) {
						sum += mags[ bin ] * mags[ bin ];
					}
					power[ b ] += sum;
				}
				frames++;
			}
			double[] out = new double[ this.bands ];
			for ( int b = 0; b < this.bands; b++ ) {
				double p = frames == 0 ? 0 : power[ b ] / frames;
				out[ b ] = p <= 1e-12 ? FLOOR_DB : Math.max( FLOOR_DB, 10 * Math.log10( p ) );
			}
			return out;
		}
	}

	/**
	 * Band levels over time
	 *
	 * @param frames How many time slices the audio is cut into
	 *
	 * @return frames x bands levels in dB
	 */
	public static double[][] spectrogram( AudioBuffer audio, int bands, int frames, double minHz, double maxHz ) {
		validate( bands, minHz, maxHz, audio );
		if ( frames < 1 || frames > 10000 ) {
			throw new BoxRuntimeException( "Frames must be between 1 and 10000 but was " + frames );
		}
		Analysis	analysis	= new Analysis( audio, bands, minHz, maxHz );
		double[][]	out			= new double[ frames ][];
		double		slice		= audio.getDurationSeconds() / frames;
		for ( int f = 0; f < frames; f++ ) {
			out[ f ] = analysis.levels( f * slice, Math.max( slice, 1.0 / audio.getSampleRate() ) );
		}
		return out;
	}

	private static int[] edges( int rate, int bands, double minHz, double maxHz ) {
		int[]	edges	= new int[ bands + 1 ];
		double	binHz	= ( double ) rate / DEFAULT_SIZE;
		for ( int b = 0; b <= bands; b++ ) {
			double hz = minHz * Math.pow( maxHz / minHz, ( double ) b / bands );
			edges[ b ] = ( int ) Math.min( DEFAULT_SIZE / 2, Math.max( b == 0 ? 0 : edges[ b - 1 ], Math.round( hz / binHz ) ) );
		}
		// Every band holds at least one bin so narrow bands at the low end are not empty
		for ( int b = 1; b <= bands; b++ ) {
			if ( edges[ b ] <= edges[ b - 1 ] ) {
				edges[ b ] = Math.min( DEFAULT_SIZE / 2 + 1, edges[ b - 1 ] + 1 );
			}
		}
		return edges;
	}

	private static void validate( int bands, double minHz, double maxHz, AudioBuffer audio ) {
		if ( bands < 1 || bands > 512 ) {
			throw new BoxRuntimeException( "Bands must be between 1 and 512 but was " + bands );
		}
		if ( minHz <= 0 || maxHz <= minHz ) {
			throw new BoxRuntimeException( "Need 0 < minHz < maxHz but got " + minHz + " and " + maxHz );
		}
		if ( minHz >= audio.getSampleRate() / 2.0 ) {
			throw new BoxRuntimeException( "minHz must be below half the sample rate, " + audio.getSampleRate() / 2.0 );
		}
	}

	static float[] mono( AudioBuffer audio ) {
		float[] mono = new float[ audio.getFrames() ];
		for ( int ch = 0; ch < audio.getChannels(); ch++ ) {
			float[] in = audio.channel( ch );
			for ( int i = 0; i < mono.length; i++ ) {
				mono[ i ] += in[ i ] / audio.getChannels();
			}
		}
		return mono;
	}

}
