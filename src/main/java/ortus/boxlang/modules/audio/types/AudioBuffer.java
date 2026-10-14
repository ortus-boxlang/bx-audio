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
package ortus.boxlang.modules.audio.types;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * An in-memory block of audio: one {@code float[]} of samples per channel, in the range -1.0 to 1.0, plus the sample rate.
 * <p>
 * All channels have the same number of frames. The buffer owns the arrays it is given, so callers must not keep writing to
 * them afterwards. Use {@link #copy()} to get an independent buffer.
 */
public final class AudioBuffer {

	/**
	 * Upper bound for the channel count, far above anything real, to catch bad arguments early
	 */
	public static final int	MAX_CHANNELS	= 32;

	/**
	 * Smallest and largest sample rates we accept, in Hz
	 */
	public static final int	MIN_SAMPLE_RATE	= 1000;
	public static final int	MAX_SAMPLE_RATE	= 768000;

	private final float[][]	samples;
	private final int		sampleRate;

	/**
	 * Wrap existing sample data. The arrays are used as-is, not copied.
	 *
	 * @param samples    One array per channel, all of the same length
	 * @param sampleRate The sample rate in Hz
	 */
	public AudioBuffer( float[][] samples, int sampleRate ) {
		validateSampleRate( sampleRate );
		if ( samples == null || samples.length < 1 || samples.length > MAX_CHANNELS ) {
			throw new BoxRuntimeException( "An audio buffer needs between 1 and " + MAX_CHANNELS + " channels" );
		}
		int frames = samples[ 0 ] == null ? -1 : samples[ 0 ].length;
		for ( float[] channel : samples ) {
			if ( channel == null || channel.length != frames ) {
				throw new BoxRuntimeException( "All channels of an audio buffer must be present and have the same length" );
			}
		}
		this.samples	= samples;
		this.sampleRate	= sampleRate;
	}

	/**
	 * Create a buffer of silence
	 *
	 * @param channels   Number of channels
	 * @param frames     Number of frames (samples per channel)
	 * @param sampleRate The sample rate in Hz
	 */
	public static AudioBuffer silence( int channels, int frames, int sampleRate ) {
		if ( channels < 1 || channels > MAX_CHANNELS ) {
			throw new BoxRuntimeException( "Channels must be between 1 and " + MAX_CHANNELS + " but was " + channels );
		}
		if ( frames < 0 ) {
			throw new BoxRuntimeException( "Frames cannot be negative but was " + frames );
		}
		return new AudioBuffer( new float[ channels ][ frames ], sampleRate );
	}

	/**
	 * Create a buffer of silence with a length in seconds
	 *
	 * @param seconds    Length in seconds, rounded to the nearest frame
	 * @param channels   Number of channels
	 * @param sampleRate The sample rate in Hz
	 */
	public static AudioBuffer silenceSeconds( double seconds, int channels, int sampleRate ) {
		validateSampleRate( sampleRate );
		if ( Double.isNaN( seconds ) || Double.isInfinite( seconds ) || seconds < 0 ) {
			throw new BoxRuntimeException( "Seconds must be a finite number of zero or more but was " + seconds );
		}
		double frames = Math.rint( seconds * sampleRate );
		if ( frames * Math.max( 1, channels ) > Integer.MAX_VALUE ) {
			throw new BoxRuntimeException( "That much audio does not fit in memory: " + seconds + " seconds on " + channels + " channels" );
		}
		return silence( channels, ( int ) frames, sampleRate );
	}

	/**
	 * @return The number of channels
	 */
	public int getChannels() {
		return this.samples.length;
	}

	/**
	 * @return The number of frames (samples per channel)
	 */
	public int getFrames() {
		return this.samples[ 0 ].length;
	}

	/**
	 * @return The sample rate in Hz
	 */
	public int getSampleRate() {
		return this.sampleRate;
	}

	/**
	 * @return The length in seconds
	 */
	public double getDurationSeconds() {
		return ( double ) getFrames() / this.sampleRate;
	}

	/**
	 * Direct access to the samples of one channel. This is the live array, not a copy.
	 *
	 * @param channel Zero based channel index
	 */
	public float[] channel( int channel ) {
		if ( channel < 0 || channel >= this.samples.length ) {
			throw new BoxRuntimeException( "Channel " + channel + " is out of range, this buffer has " + this.samples.length + " channels" );
		}
		return this.samples[ channel ];
	}

	/**
	 * @return The largest absolute sample value across all channels, 0 for an empty buffer
	 */
	public float getPeak() {
		float peak = 0f;
		for ( float[] channel : this.samples ) {
			for ( float sample : channel ) {
				float abs = Math.abs( sample );
				if ( abs > peak ) {
					peak = abs;
				}
			}
		}
		return peak;
	}

	/**
	 * @return The root mean square level across all channels, 0 for an empty buffer
	 */
	public double getRms() {
		long	count	= ( long ) getChannels() * getFrames();
		double	sum		= 0;
		if ( count == 0 ) {
			return 0;
		}
		for ( float[] channel : this.samples ) {
			for ( float sample : channel ) {
				sum += ( double ) sample * sample;
			}
		}
		return Math.sqrt( sum / count );
	}

	/**
	 * @return An independent copy of this buffer
	 */
	public AudioBuffer copy() {
		float[][] copy = new float[ this.samples.length ][];
		for ( int i = 0; i < copy.length; i++ ) {
			copy[ i ] = this.samples[ i ].clone();
		}
		return new AudioBuffer( copy, this.sampleRate );
	}

	@Override
	public String toString() {
		return "AudioBuffer[" + getChannels() + " ch, " + this.sampleRate + " Hz, " + getFrames() + " frames, "
		    + String.format( "%.3f", getDurationSeconds() ) + " s]";
	}

	private static void validateSampleRate( int sampleRate ) {
		if ( sampleRate < MIN_SAMPLE_RATE || sampleRate > MAX_SAMPLE_RATE ) {
			throw new BoxRuntimeException(
			    "Sample rate must be between " + MIN_SAMPLE_RATE + " and " + MAX_SAMPLE_RATE + " Hz but was " + sampleRate );
		}
	}

}
