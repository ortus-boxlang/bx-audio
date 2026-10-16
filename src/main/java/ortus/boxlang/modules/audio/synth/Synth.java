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
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Sound generators: note names, oscillators, pitch sweeps, noise and envelopes. Every generator returns a mono
 * {@link AudioBuffer}.
 */
public final class Synth {

	/**
	 * Oscillator shapes. The saw, square and triangle are band limited by adding sine harmonics up to the Nyquist frequency, so they do
	 * not alias. The pulse is the naive digital kind, which is what chiptune hardware did.
	 */
	public static final List<String>	SHAPES			= List.of( "sine", "saw", "square", "triangle", "pulse", "additive" );

	public static final List<String>	NOISE_COLORS	= List.of( "white", "pink", "brown", "blue" );

	/**
	 * Longest sound a single generator call will make, in seconds
	 */
	public static final int				MAX_SECONDS		= 600;

	/**
	 * RMS level of generated noise, about -12 dBFS
	 */
	public static final double			NOISE_RMS		= 0.25;

	private Synth() {
	}

	/**
	 * @param midi A MIDI note number, 69 is A4. Fractions are allowed for detuning, 0.1 is ten cents.
	 *
	 * @return The frequency in Hz with A4 tuned to 440
	 */
	public static double midiToHz( double midi ) {
		requireFinite( midi, "note" );
		return 440.0 * Math.pow( 2.0, ( midi - 69.0 ) / 12.0 );
	}

	/**
	 * Parse a note name such as C4, F#3, Bb2 or C-1 into a MIDI note number. C4 is 60 and A4 is 69.
	 */
	public static int noteNameToMidi( String name ) {
		String s = name == null ? "" : name.trim();
		if ( s.length() < 2 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "Not a note name: [" + name + "]. Use a letter, an optional # or b, and an octave, like C4 or F#3" );
		}
		int base;
		switch ( Character.toUpperCase( s.charAt( 0 ) ) ) {
			case 'C' -> base = 0;
			case 'D' -> base = 2;
			case 'E' -> base = 4;
			case 'F' -> base = 5;
			case 'G' -> base = 7;
			case 'A' -> base = 9;
			case 'B' -> base = 11;
			default -> throw new AudioException( AudioException.INVALID_ARGUMENT, "Not a note name: [" + name + "]. Notes start with a letter from A to G" );
		}
		int pos = 1;
		if ( s.charAt( pos ) == '#' ) {
			base++;
			pos++;
		} else if ( s.charAt( pos ) == 'b' ) {
			base--;
			pos++;
		}
		int octave;
		try {
			octave = Integer.parseInt( s.substring( pos ) );
		} catch ( NumberFormatException e ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Not a note name: [" + name + "]. The octave must be a whole number, like C4" );
		}
		int midi = ( octave + 1 ) * 12 + base;
		if ( midi < 0 || midi > 127 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The note [" + name + "] is outside the MIDI range C-1 to G9" );
		}
		return midi;
	}

	/**
	 * Generate a tone.
	 *
	 * @param shape      One of {@link #SHAPES}
	 * @param freq       Fundamental frequency in Hz, above 0 and below the Nyquist frequency
	 * @param seconds    Length in seconds
	 * @param sampleRate Sample rate in Hz
	 * @param harmonics  For saw, square and triangle: the most harmonics to add, fewer are used if they would pass the Nyquist frequency
	 * @param partials   For additive: pairs of [frequency multiple, amplitude]. Amplitudes are used as given and are not normalised.
	 * @param width      For pulse: the fraction of each cycle spent high, between 0 and 1
	 * @param phase      Where in the cycle to start, from 0 to 1
	 *
	 * @return A mono buffer whose ideal peak is 1 for sine, saw, square, triangle and pulse
	 */
	public static AudioBuffer oscillator( String shape, double freq, double seconds, int sampleRate, int harmonics, double[][] partials, double width,
	    double phase ) {
		String kind = shape == null ? "" : shape.toLowerCase( Locale.ROOT );
		if ( !SHAPES.contains( kind ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Unknown shape [" + shape + "]. Use one of " + SHAPES );
		}
		int		frames	= frameCount( seconds, sampleRate );
		double	nyquist	= sampleRate / 2.0;
		requireFinite( freq, "freq" );
		requireFinite( phase, "phase" );
		if ( freq <= 0 || freq >= nyquist ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "The frequency must be above 0 and below " + nyquist + " Hz (half the sample rate) but was " + freq );
		}
		if ( harmonics < 1 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Harmonics must be at least 1 but was " + harmonics );
		}
		double[] out = new double[ frames ];

		switch ( kind ) {
			case "sine" -> addPartial( out, freq, 1.0, phase, sampleRate );
			case "saw" -> {
				for ( int h = 1; h <= harmonics && h * freq < nyquist; h++ ) {
					addPartial( out, freq * h, ( 2.0 / Math.PI ) / h, phase * h, sampleRate );
				}
			}
			case "square" -> {
				for ( int h = 1; h <= harmonics && h * freq < nyquist; h += 2 ) {
					addPartial( out, freq * h, ( 4.0 / Math.PI ) / h, phase * h, sampleRate );
				}
			}
			case "triangle" -> {
				for ( int h = 1; h <= harmonics && h * freq < nyquist; h += 2 ) {
					double sign = ( ( h - 1 ) / 2 ) % 2 == 0 ? 1 : -1;
					addPartial( out, freq * h, sign * ( 8.0 / ( Math.PI * Math.PI ) ) / ( ( double ) h * h ), phase * h, sampleRate );
				}
			}
			case "pulse" -> {
				if ( Double.isNaN( width ) || width <= 0 || width >= 1 ) {
					throw new AudioException( AudioException.INVALID_ARGUMENT, "Pulse width must be between 0 and 1 but was " + width );
				}
				for ( int i = 0; i < frames; i++ ) {
					double cycle = ( freq * i / sampleRate + phase ) % 1.0;
					out[ i ] = cycle < width ? 1.0 : -1.0;
				}
			}
			default -> {
				if ( partials == null || partials.length == 0 ) {
					throw new AudioException( AudioException.INVALID_ARGUMENT, "The additive shape needs partials, for example [ [ 1, 1.0 ], [ 2, 0.5 ] ]" );
				}
				for ( double[] partial : partials ) {
					if ( partial == null || partial.length != 2 || partial[ 0 ] <= 0 || Double.isNaN( partial[ 1 ] ) || Double.isNaN( partial[ 0 ] ) ) {
						throw new AudioException( AudioException.INVALID_ARGUMENT, "Each partial must be a pair of [ frequency multiple above 0, amplitude ]" );
					}
					if ( freq * partial[ 0 ] < nyquist ) {
						addPartial( out, freq * partial[ 0 ], partial[ 1 ], phase * partial[ 0 ], sampleRate );
					}
				}
			}
		}
		return toBuffer( out, sampleRate );
	}

	/**
	 * Glide a sine from one pitch to another. The frequency moves exponentially, {@code end + ( start - end ) * e^( -speed * t )}, and
	 * the phase is accumulated so there are no clicks. With a start well above the end and a high speed this is the thump of a kick drum.
	 *
	 * @param startHz    Frequency at time 0
	 * @param endHz      Frequency it settles towards
	 * @param seconds    Length in seconds
	 * @param speed      How fast it settles, in 1/seconds. Higher is quicker. 0 holds the start frequency.
	 * @param sampleRate Sample rate in Hz
	 */
	public static AudioBuffer sweep( double startHz, double endHz, double seconds, double speed, int sampleRate ) {
		int frames = frameCount( seconds, sampleRate );
		requireFinite( startHz, "startHz" );
		requireFinite( endHz, "endHz" );
		requireFinite( speed, "speed" );
		double nyquist = sampleRate / 2.0;
		if ( startHz <= 0 || endHz <= 0 || startHz >= nyquist || endHz >= nyquist ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Sweep frequencies must be above 0 and below " + nyquist + " Hz" );
		}
		if ( speed < 0 ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "Sweep speed cannot be negative" );
		}
		double[]	out		= new double[ frames ];
		double		phase	= 0;
		for ( int i = 0; i < frames; i++ ) {
			double t = ( double ) i / sampleRate;
			phase		+= 2 * Math.PI * ( endHz + ( startHz - endHz ) * Math.exp( -speed * t ) ) / sampleRate;
			out[ i ]	= Math.sin( phase );
		}
		return toBuffer( out, sampleRate );
	}

	/**
	 * Generate noise with a fixed seed, so the same call always makes the same sound. The result is scaled to an RMS of
	 * {@link #NOISE_RMS}.
	 *
	 * @param seconds    Length in seconds
	 * @param sampleRate Sample rate in Hz
	 * @param seed       Seed for the random generator
	 * @param color      white (flat), pink (softer highs), brown (rumble) or blue (hissy highs)
	 */
	public static AudioBuffer noise( double seconds, int sampleRate, long seed, String color ) {
		int		frames	= frameCount( seconds, sampleRate );
		String	kind	= color == null ? "" : color.toLowerCase( Locale.ROOT );
		if ( !NOISE_COLORS.contains( kind ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Unknown noise color [" + color + "]. Use one of " + NOISE_COLORS );
		}
		Random		rng	= new Random( seed );
		double[]	out	= new double[ frames ];
		double		b0	= 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0, b6 = 0, last = 0, walk = 0;
		for ( int i = 0; i < frames; i++ ) {
			double w = rng.nextGaussian();
			switch ( kind ) {
				case "white" -> out[ i ] = w;
				case "pink" -> {
					// Paul Kellet's refined filter
					b0			= 0.99886 * b0 + w * 0.0555179;
					b1			= 0.99332 * b1 + w * 0.0750759;
					b2			= 0.96900 * b2 + w * 0.1538520;
					b3			= 0.86650 * b3 + w * 0.3104856;
					b4			= 0.55000 * b4 + w * 0.5329522;
					b5			= -0.7616 * b5 - w * 0.0168980;
					out[ i ]	= b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362;
					b6			= w * 0.115926;
				}
				case "brown" -> {
					walk		= ( walk + 0.02 * w ) / 1.02;
					out[ i ]	= walk;
				}
				default -> {
					out[ i ]	= w - last;
					last		= w;
				}
			}
		}
		double sum = 0;
		for ( double v : out ) {
			sum += v * v;
		}
		double rms = frames == 0 ? 0 : Math.sqrt( sum / frames );
		if ( rms > 0 ) {
			double scale = NOISE_RMS / rms;
			for ( int i = 0; i < frames; i++ ) {
				out[ i ] *= scale;
			}
		}
		return toBuffer( out, sampleRate );
	}

	/**
	 * Shape the volume of a sound: a straight line up over the attack, then an exponential fall.
	 * {@code gain = min( 1, t / attack ) * e^( -t / decay )}
	 *
	 * @param audio  The input
	 * @param attack Seconds to reach full volume, 0 for instantly
	 * @param decay  The decay time constant in seconds: the level has dropped to 37% after one decay time. 0 means no decay.
	 */
	public static AudioBuffer envelope( AudioBuffer audio, double attack, double decay ) {
		requireFinite( attack, "attack" );
		requireFinite( decay, "decay" );
		if ( attack < 0 || decay < 0 ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "Attack and decay cannot be negative" );
		}
		int			rate	= audio.getSampleRate();
		float[][]	out		= new float[ audio.getChannels() ][ audio.getFrames() ];
		for ( int i = 0; i < audio.getFrames(); i++ ) {
			double	t		= ( double ) i / rate;
			double	gain	= ( attack > 0 ? Math.min( 1.0, t / attack ) : 1.0 ) * ( decay > 0 ? Math.exp( -t / decay ) : 1.0 );
			for ( int ch = 0; ch < out.length; ch++ ) {
				out[ ch ][ i ] = ( float ) ( audio.channel( ch )[ i ] * gain );
			}
		}
		return new AudioBuffer( out, rate );
	}

	/**
	 * Number of frames for a length in seconds, with the safety checks every generator shares
	 */
	static int frameCount( double seconds, int sampleRate ) {
		if ( sampleRate < AudioBuffer.MIN_SAMPLE_RATE || sampleRate > AudioBuffer.MAX_SAMPLE_RATE ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "Sample rate must be between " + AudioBuffer.MIN_SAMPLE_RATE + " and " + AudioBuffer.MAX_SAMPLE_RATE
			        + " Hz but was " + sampleRate );
		}
		requireFinite( seconds, "seconds" );
		if ( seconds < 0 || seconds > MAX_SECONDS ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Length must be between 0 and " + MAX_SECONDS + " seconds but was " + seconds );
		}
		return ( int ) Math.round( seconds * sampleRate );
	}

	static AudioBuffer toBuffer( double[] samples, int sampleRate ) {
		float[] out = new float[ samples.length ];
		for ( int i = 0; i < out.length; i++ ) {
			out[ i ] = ( float ) samples[ i ];
		}
		return new AudioBuffer( new float[][] { out }, sampleRate );
	}

	static void requireFinite( double value, String name ) {
		if ( Double.isNaN( value ) || Double.isInfinite( value ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The " + name + " argument must be a finite number but was " + value );
		}
	}

	private static void addPartial( double[] out, double freq, double amplitude, double phaseCycles, int sampleRate ) {
		double	step	= 2 * Math.PI * freq / sampleRate;
		double	start	= 2 * Math.PI * phaseCycles;
		for ( int i = 0; i < out.length; i++ ) {
			out[ i ] += amplitude * Math.sin( start + step * i );
		}
	}

}
