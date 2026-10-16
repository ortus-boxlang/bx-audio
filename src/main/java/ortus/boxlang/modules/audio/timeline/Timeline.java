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
package ortus.boxlang.modules.audio.timeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ortus.boxlang.modules.audio.curve.Curve;
import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.dsp.Modulation;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * A multi track mixing board with a tempo grid.
 * <p>
 * Sounds are placed on named tracks at a time offset with a gain and a pan. Nothing is mixed down until {@link #render(double)}
 * is called, so ducking and track gains are applied non-destructively at that point. Tracks grow as needed.
 * <p>
 * A timeline is mutable and not thread safe.
 */
public final class Timeline {

	/**
	 * Longest audio a timeline will hold, to stop a typo like an offset in samples from allocating gigabytes
	 */
	public static final int				MAX_SECONDS		= 1800;

	/**
	 * Name of the track used when none is given
	 */
	public static final String			DEFAULT_TRACK	= "main";

	private final double				bpm;
	private final int					beatsPerBar;
	private final int					sampleRate;
	private final int					channels;
	private final Map<String, Track>	tracks			= new LinkedHashMap<>();

	/**
	 * @param bpm         Tempo in beats per minute
	 * @param beatsPerBar Beats in one bar, for example 4
	 * @param sampleRate  Sample rate of the mix in Hz
	 * @param channels    1 for mono or 2 for stereo
	 */
	public Timeline( double bpm, int beatsPerBar, int sampleRate, int channels ) {
		if ( Double.isNaN( bpm ) || Double.isInfinite( bpm ) || bpm < 1 || bpm > 999 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Tempo must be between 1 and 999 bpm but was " + bpm );
		}
		if ( beatsPerBar < 1 || beatsPerBar > 64 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Beats per bar must be between 1 and 64 but was " + beatsPerBar );
		}
		if ( sampleRate < AudioBuffer.MIN_SAMPLE_RATE || sampleRate > AudioBuffer.MAX_SAMPLE_RATE ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "Sample rate must be between " + AudioBuffer.MIN_SAMPLE_RATE + " and " + AudioBuffer.MAX_SAMPLE_RATE
			        + " Hz but was " + sampleRate );
		}
		if ( channels != 1 && channels != 2 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "A timeline is mono (1) or stereo (2) but channels was " + channels );
		}
		this.bpm			= bpm;
		this.beatsPerBar	= beatsPerBar;
		this.sampleRate		= sampleRate;
		this.channels		= channels;
	}

	public double getBpm() {
		return this.bpm;
	}

	public int getBeatsPerBar() {
		return this.beatsPerBar;
	}

	public int getSampleRate() {
		return this.sampleRate;
	}

	public int getChannels() {
		return this.channels;
	}

	/**
	 * @return The names of the tracks created so far, in creation order
	 */
	public List<String> getTrackNames() {
		return new ArrayList<>( this.tracks.keySet() );
	}

	/**
	 * @return The length of the content so far in seconds, before any tail
	 */
	public double getDurationSeconds() {
		return ( double ) usedFrames() / this.sampleRate;
	}

	/**
	 * Convert a beat number to seconds. Beat 0 is the start, beat 1 is one beat in.
	 */
	public double beat( double beats ) {
		return beats * 60.0 / this.bpm;
	}

	/**
	 * Convert a bar number to seconds. Bar 0 is the start, bar 1 is one bar in.
	 */
	public double bar( double bars ) {
		return beat( bars * this.beatsPerBar );
	}

	/**
	 * Place a sound on a track. The sound is converted to the timeline sample rate if needed, and to the timeline channel count:
	 * mono into a stereo timeline is panned, stereo into a stereo timeline is balanced, stereo into a mono timeline is averaged.
	 *
	 * @param sound     The sound to place
	 * @param atSeconds Where it starts, in seconds from the start of the timeline
	 * @param track     The track name, created on first use
	 * @param db        Gain in decibels
	 * @param pan       -1 left, 0 center, 1 right. Ignored on a mono timeline.
	 *
	 * @return This timeline, for chaining
	 */
	public Timeline add( AudioBuffer sound, double atSeconds, String track, double db, double pan ) {
		if ( Double.isNaN( atSeconds ) || Double.isInfinite( atSeconds ) || atSeconds < 0 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "A sound must start at zero or later but the start was " + atSeconds );
		}
		if ( Double.isNaN( db ) || Double.isInfinite( db ) || Double.isNaN( pan ) || pan < -1 || pan > 1 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Gain must be a finite number of decibels and pan between -1 and 1" );
		}
		if ( track == null || track.isBlank() ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "A track name cannot be empty" );
		}
		if ( atSeconds > MAX_SECONDS ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "A start of " + atSeconds + " seconds is past the " + MAX_SECONDS + " second limit of a timeline" );
		}
		AudioBuffer source = sound.getSampleRate() == this.sampleRate ? sound : Dsp.resample( sound, this.sampleRate );
		if ( source.getChannels() > 2 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "Only mono and stereo sounds can be placed on a timeline but this one has " + source.getChannels() );
		}
		long	start	= Math.round( atSeconds * this.sampleRate );
		long	end		= start + source.getFrames();
		if ( end > ( long ) MAX_SECONDS * this.sampleRate ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The sound would end past the " + MAX_SECONDS + " second limit of a timeline" );
		}
		Track t = trackFor( track );
		t.ensureCapacity( ( int ) end );

		double	gain	= Dsp.dbToLinear( db );
		int		from	= ( int ) start;
		int		frames	= source.getFrames();
		if ( this.channels == 2 && source.getChannels() == 1 ) {
			double	angle	= ( pan + 1 ) * Math.PI / 4;
			float	left	= ( float ) ( gain * Math.cos( angle ) );
			float	right	= ( float ) ( gain * Math.sin( angle ) );
			float[]	in		= source.channel( 0 );
			for ( int i = 0; i < frames; i++ ) {
				t.data[ 0 ][ from + i ]	+= in[ i ] * left;
				t.data[ 1 ][ from + i ]	+= in[ i ] * right;
			}
		} else if ( this.channels == 2 ) {
			float	left	= ( float ) ( gain * ( pan > 0 ? Math.cos( pan * Math.PI / 2 ) : 1 ) );
			float	right	= ( float ) ( gain * ( pan < 0 ? Math.cos( -pan * Math.PI / 2 ) : 1 ) );
			for ( int i = 0; i < frames; i++ ) {
				t.data[ 0 ][ from + i ]	+= source.channel( 0 )[ i ] * left;
				t.data[ 1 ][ from + i ]	+= source.channel( 1 )[ i ] * right;
			}
		} else {
			float scale = ( float ) ( gain / source.getChannels() );
			for ( int ch = 0; ch < source.getChannels(); ch++ ) {
				float[] in = source.channel( ch );
				for ( int i = 0; i < frames; i++ ) {
					t.data[ 0 ][ from + i ] += in[ i ] * scale;
				}
			}
		}
		t.used = Math.max( t.used, ( int ) end );
		return this;
	}

	/**
	 * Set the volume of a whole track. It applies at render time, on top of the gain given to each sound.
	 */
	public Timeline setTrackGain( String track, double db ) {
		if ( Double.isNaN( db ) || Double.isInfinite( db ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Track gain must be a finite number of decibels but was " + db );
		}
		trackFor( track ).gain = ( float ) Dsp.dbToLinear( db );
		return this;
	}

	/**
	 * Duck a track at given moments, for example under every kick drum. At each time the gain drops straight to the amount and then
	 * recovers in a straight line. Overlapping events multiply.
	 *
	 * @param track           The track to duck
	 * @param timesSeconds    When each dip starts
	 * @param amountDb        How far the gain drops, a negative number of decibels
	 * @param recoverySeconds How long the gain takes to return to normal
	 */
	public Timeline pump( String track, List<Double> timesSeconds, double amountDb, double recoverySeconds ) {
		if ( Double.isNaN( amountDb ) || Double.isInfinite( amountDb ) || amountDb > 0 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The ducking amount must be zero or a negative number of decibels but was " + amountDb );
		}
		if ( Double.isNaN( recoverySeconds ) || Double.isInfinite( recoverySeconds ) || recoverySeconds <= 0 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The recovery time must be more than zero seconds but was " + recoverySeconds );
		}
		int[] starts = new int[ timesSeconds.size() ];
		for ( int i = 0; i < starts.length; i++ ) {
			double time = timesSeconds.get( i );
			if ( Double.isNaN( time ) || Double.isInfinite( time ) || time < 0 ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT, "Every ducking time must be zero or later but one was " + time );
			}
			starts[ i ] = ( int ) Math.min( Integer.MAX_VALUE, Math.round( time * this.sampleRate ) );
		}
		trackFor( track ).ducks
		    .add( new PumpDuck( starts, ( float ) Dsp.dbToLinear( amountDb ), ( int ) Math.max( 1, Math.round( recoverySeconds * this.sampleRate ) ) ) );
		return this;
	}

	/**
	 * Duck a track while another track is loud, for example music under a voice-over. The trigger track's level is followed with an
	 * attack and release, and the target is lowered by the amount whenever that level is above the threshold.
	 *
	 * @param track          The track to duck
	 * @param trigger        The track whose level controls the ducking. It must already exist.
	 * @param amountDb       How far to lower the target, a negative number of decibels
	 * @param thresholdDb    The trigger level above which ducking starts, relative to full scale
	 * @param attackSeconds  How quickly the target is lowered
	 * @param releaseSeconds How quickly the target comes back
	 */
	public Timeline duck( String track, String trigger, double amountDb, double thresholdDb, double attackSeconds, double releaseSeconds ) {
		if ( Double.isNaN( amountDb ) || Double.isInfinite( amountDb ) || amountDb > 0 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The ducking amount must be zero or a negative number of decibels but was " + amountDb );
		}
		if ( Double.isNaN( thresholdDb ) || Double.isInfinite( thresholdDb ) || Double.isNaN( attackSeconds ) || attackSeconds < 0
		    || Double.isNaN( releaseSeconds ) || releaseSeconds < 0 ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "The threshold must be finite and the attack and release times cannot be negative" );
		}
		if ( !this.tracks.containsKey( trigger ) ) {
			throw new AudioException( AudioException.FILE_NOT_FOUND, "The trigger track [" + trigger + "] does not exist, add some sound to it first" );
		}
		if ( track.equals( trigger ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "A track cannot duck itself" );
		}
		trackFor( track ).ducks.add( new SidechainDuck( trigger, ( float ) Dsp.dbToLinear( amountDb ), ( float ) Dsp.dbToLinear( thresholdDb ),
		    attackSeconds, releaseSeconds ) );
		return this;
	}

	/**
	 * Make a track's volume or balance follow a curve over the time of the timeline. A second call for the same parameter of the track replaces the
	 * first. The volume follows on top of the track gain and the ducks.
	 *
	 * @param track The track
	 * @param param gainDb (the curve is a gain in dB) or pan (-1 left to 1 right, applied as a balance control, so the timeline must be stereo)
	 * @param curve The curve, in seconds of the timeline
	 */
	public Timeline automate( String track, String param, Curve curve ) {
		String kind = param == null ? "" : param.trim();
		if ( !kind.equalsIgnoreCase( "gainDb" ) && !kind.equalsIgnoreCase( "pan" ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "A track can automate gainDb or pan, not [" + param + "]" );
		}
		if ( kind.equalsIgnoreCase( "pan" ) && this.channels != 2 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "Pan can only be automated on a stereo timeline, this one has " + this.channels + " channel(s)" );
		}
		if ( curve == null ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The curve cannot be empty" );
		}
		trackFor( track ).automation.put( kind.equalsIgnoreCase( "pan" ) ? "pan" : "gainDb", curve );
		return this;
	}

	/**
	 * Mix all tracks down into one buffer.
	 *
	 * @param tailSeconds Extra silence to leave after the last sound, for example to let a fade out finish
	 */
	public AudioBuffer render( double tailSeconds ) {
		if ( Double.isNaN( tailSeconds ) || Double.isInfinite( tailSeconds ) || tailSeconds < 0 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The tail must be zero or more seconds but was " + tailSeconds );
		}
		long frames = ( long ) usedFrames() + Math.round( tailSeconds * this.sampleRate );
		if ( frames > ( long ) MAX_SECONDS * this.sampleRate ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The rendered audio would pass the " + MAX_SECONDS + " second limit of a timeline" );
		}
		float[][] out = new float[ this.channels ][ ( int ) frames ];
		for ( Track t : this.tracks.values() ) {
			float[]	curve	= duckCurve( t );
			Curve	volume	= t.automation.get( "gainDb" );
			Curve	pan		= t.automation.get( "pan" );
			if ( volume != null && t.used > 0 ) {
				if ( curve == null ) {
					curve = new float[ t.used ];
					Arrays.fill( curve, 1f );
				}
				for ( int i = 0; i < t.used; i++ ) {
					curve[ i ] *= ( float ) Modulation.linear( volume.at( ( double ) i / this.sampleRate ) );
				}
			}
			// the balance of each sample, worked out once for both channels
			float[][] balance = null;
			if ( pan != null && t.used > 0 ) {
				balance = new float[ 2 ][ t.used ];
				for ( int i = 0; i < t.used; i++ ) {
					float[] g = Modulation.balanceGains( pan.at( ( double ) i / this.sampleRate ) );
					balance[ 0 ][ i ]	= g[ 0 ];
					balance[ 1 ][ i ]	= g[ 1 ];
				}
			}
			for ( int ch = 0; ch < this.channels; ch++ ) {
				float[]	src	= t.data[ ch ];
				float[]	dst	= out[ ch ];
				if ( curve == null && balance == null ) {
					for ( int i = 0; i < t.used; i++ ) {
						dst[ i ] += src[ i ] * t.gain;
					}
				} else {
					for ( int i = 0; i < t.used; i++ ) {
						float g = t.gain * ( curve == null ? 1f : curve[ i ] );
						if ( balance != null ) {
							g *= balance[ ch ][ i ];
						}
						dst[ i ] += src[ i ] * g;
					}
				}
			}
		}
		return new AudioBuffer( out, this.sampleRate );
	}

	/**
	 * Combined gain of all the ducks on a track, or null if it has none
	 */
	private float[] duckCurve( Track t ) {
		if ( t.ducks.isEmpty() || t.used == 0 ) {
			return null;
		}
		float[] curve = new float[ t.used ];
		Arrays.fill( curve, 1f );
		for ( Duck duck : t.ducks ) {
			if ( duck instanceof PumpDuck pump ) {
				for ( int start : pump.starts() ) {
					int end = ( int ) Math.min( t.used, ( long ) start + pump.recoveryFrames() );
					for ( int i = start; i < end; i++ ) {
						curve[ i ] *= pump.amount() + ( 1f - pump.amount() ) * ( ( float ) ( i - start ) / pump.recoveryFrames() );
					}
				}
			} else if ( duck instanceof SidechainDuck side ) {
				applySidechain( curve, side, this.tracks.get( side.trigger() ) );
			}
		}
		return curve;
	}

	private void applySidechain( float[] curve, SidechainDuck side, Track trigger ) {
		double	attack		= 1 - Math.exp( -1.0 / ( Math.max( side.attack(), 1e-6 ) * this.sampleRate ) );
		double	release		= 1 - Math.exp( -1.0 / ( Math.max( side.release(), 1e-6 ) * this.sampleRate ) );
		double	envelope	= 0;
		double	gain		= 1;
		for ( int i = 0; i < curve.length; i++ ) {
			double level = 0;
			if ( i < trigger.used ) {
				for ( int ch = 0; ch < this.channels; ch++ ) {
					level = Math.max( level, Math.abs( trigger.data[ ch ][ i ] ) * trigger.gain );
				}
			}
			// A quick rise and a slower fall keeps the detector from chattering on the waveform's zero crossings
			envelope += ( level - envelope ) * ( level > envelope ? 0.5 : 1 - Math.exp( -1.0 / ( 0.005 * this.sampleRate ) ) );
			double target = envelope > side.threshold() ? side.amount() : 1;
			gain		+= ( target - gain ) * ( target < gain ? attack : release );
			curve[ i ]	*= ( float ) gain;
		}
	}

	private int usedFrames() {
		int used = 0;
		for ( Track t : this.tracks.values() ) {
			used = Math.max( used, t.used );
		}
		return used;
	}

	private Track trackFor( String name ) {
		return this.tracks.computeIfAbsent( name, n -> new Track( this.channels ) );
	}

	/**
	 * One named track: growable sample storage, a volume and its ducks
	 */
	private static final class Track {

		float[][]			data;
		int					used		= 0;
		float				gain		= 1f;
		List<Duck>			ducks		= new ArrayList<>();
		Map<String, Curve>	automation	= new LinkedHashMap<>();

		Track( int channels ) {
			this.data = new float[ channels ][ 0 ];
		}

		void ensureCapacity( int frames ) {
			if ( frames <= this.data[ 0 ].length ) {
				return;
			}
			int capacity = ( int ) Math.min( Integer.MAX_VALUE - 8, Math.max( ( long ) frames, ( long ) this.data[ 0 ].length * 3 / 2 ) );
			for ( int ch = 0; ch < this.data.length; ch++ ) {
				this.data[ ch ] = Arrays.copyOf( this.data[ ch ], capacity );
			}
		}
	}

	private sealed interface Duck permits PumpDuck, SidechainDuck {
	}

	private record PumpDuck( int[] starts, float amount, int recoveryFrames ) implements Duck {
	}

	private record SidechainDuck( String trigger, float amount, float threshold, double attack, double release ) implements Duck {
	}

}
