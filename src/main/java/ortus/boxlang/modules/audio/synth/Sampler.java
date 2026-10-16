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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.dsp.Loops;
import ortus.boxlang.modules.audio.dsp.Stretch;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * A recording turned into an instrument: it plays any note by playing the recording faster or slower, like a sampler or a tape keyboard. With
 * several recordings it uses the one whose pitch is nearest, so a note is never shifted far. A sustained note holds a loop of the recording for as
 * long as it is asked to.
 */
public final class Sampler {

	/**
	 * A recording and the note it sounds at
	 *
	 * @param root  The MIDI note of the recording, fractions detune
	 * @param audio The recording
	 */
	public record Zone( double root, AudioBuffer audio ) {
	}

	/**
	 * A recording played at one pitch, ready to be played again without the work of shifting it
	 */
	private record Prepared( AudioBuffer pitched, AudioBuffer loop, int loopAt ) {
	}

	private static final double			MAX_NOTE_SECONDS	= 600;
	private static final int			MIN_LOOP_FRAMES		= 64;

	private final List<Zone>			zones;
	private final boolean				loop;
	private final double				loopStart;
	private final double				loopEnd;
	private final double				crossfade;
	private final Map<String, Prepared>	cache				= new ConcurrentHashMap<>();

	/**
	 * @param zones     The recordings, at least one
	 * @param loop      Hold a loop of the recording for a note longer than the recording
	 * @param loopStart Where the loop starts in the recording, in seconds
	 * @param loopEnd   Where it ends, in seconds, 0 for the end of the recording
	 * @param crossfade Seconds of overlap that make the loop seamless
	 */
	public Sampler( List<Zone> zones, boolean loop, double loopStart, double loopEnd, double crossfade ) {
		if ( zones == null || zones.isEmpty() ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "A sampler needs at least one recording" );
		}
		AudioBuffer first = zones.get( 0 ).audio();
		for ( Zone zone : zones ) {
			if ( zone.audio().getChannels() != first.getChannels() || zone.audio().getSampleRate() != first.getSampleRate() ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT, "The recordings of a sampler must all have the same channels and sample rate, but "
				    + first + " and " + zone.audio() + " differ. Use audioChannels() and audioResample() to match them first" );
			}
			if ( zone.audio().getFrames() < 2 ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT, "A recording of a sampler must have some sound in it" );
			}
		}
		if ( loopStart < 0 || Double.isNaN( loopStart ) || Double.isNaN( loopEnd ) || loopEnd < 0 || loopEnd != 0 && loopEnd <= loopStart || crossfade < 0
		    || Double.isNaN( crossfade ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "The loop must start at zero or later and end after it starts (0 for the end of the recording), and the crossfade cannot be negative" );
		}
		if ( loop ) {
			for ( Zone zone : zones ) {
				double end = loopEnd == 0 ? zone.audio().getDurationSeconds() : loopEnd;
				if ( loopStart >= zone.audio().getDurationSeconds() || end > zone.audio().getDurationSeconds() + 1e-9
				    || ( end - loopStart ) * zone.audio().getSampleRate() < MIN_LOOP_FRAMES ) {
					throw new AudioException( AudioException.INVALID_ARGUMENT,
					    "The loop from " + loopStart + " to " + end + " seconds does not fit in a recording of "
					        + zone.audio().getDurationSeconds() + " seconds, or is shorter than " + MIN_LOOP_FRAMES + " samples" );
				}
			}
		}
		this.zones = new ArrayList<>( zones );
		this.zones.sort( Comparator.comparingDouble( Zone::root ) );
		this.loop		= loop;
		this.loopStart	= loopStart;
		this.loopEnd	= loopEnd;
		this.crossfade	= crossfade;
	}

	public int getChannels() {
		return this.zones.get( 0 ).audio().getChannels();
	}

	public int getSampleRate() {
		return this.zones.get( 0 ).audio().getSampleRate();
	}

	/**
	 * The recording nearest to a note. Of two equally near, the lower one is used, so the note is a little faster instead of a little slower.
	 */
	private int nearest( double midi ) {
		int		best			= 0;
		double	bestDistance	= Double.MAX_VALUE;
		for ( int i = 0; i < this.zones.size(); i++ ) {
			double d = Math.abs( midi - this.zones.get( i ).root() );
			if ( d < bestDistance - 1e-9 ) {
				bestDistance	= d;
				best			= i;
			}
		}
		return best;
	}

	private Prepared prepare( int zoneIndex, double midi ) {
		Zone	zone	= this.zones.get( zoneIndex );
		long	cents	= Math.round( ( midi - zone.root() ) * 100 );
		double	factor	= Math.pow( 2, cents / 1200.0 );
		if ( factor < 0.125 || factor > 8 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "The note is more than three octaves from the nearest recording of the sampler (which sounds at "
			        + zone.root() + "). Add a recording nearer to it." );
		}
		return this.cache.computeIfAbsent( zoneIndex + ":" + cents, k -> {
			AudioBuffer	pitched	= Stretch.varispeed( zone.audio(), factor );
			AudioBuffer	block	= null;
			int			at		= 0;
			if ( this.loop ) {
				int	rate	= pitched.getSampleRate();
				int	from	= ( int ) Math.round( this.loopStart / factor * rate );
				int	to		= this.loopEnd == 0 ? pitched.getFrames() : Math.min( pitched.getFrames(), ( int ) Math.round( this.loopEnd / factor * rate ) );
				from = Math.min( from, Math.max( 0, to - MIN_LOOP_FRAMES ) );
				AudioBuffer	region	= Dsp.trim( pitched, ( double ) from / rate, ( double ) to / rate );
				double		fade	= Math.min( this.crossfade, region.getDurationSeconds() / 2.5 );
				block	= Loops.loopify( region, fade );
				at		= from;
			}
			return new Prepared( pitched, block, at );
		} );
	}

	/**
	 * Play a note
	 *
	 * @param midi     The note, fractions detune
	 * @param seconds  How long the note is held. 0 plays the recording through.
	 * @param velocity Loudness, 0 to 1
	 * @param attack   Seconds of fade in, which keeps a note from clicking
	 * @param release  Seconds the note fades out after it was held, ignored when seconds is 0
	 *
	 * @return The note, as long as it is held and released
	 */
	public AudioBuffer note( double midi, double seconds, double velocity, double attack, double release ) {
		if ( Double.isNaN( midi ) || midi < 0 || midi > 135 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The note must be a MIDI number from 0 to 135 or a note name from C-1 to G9" );
		}
		if ( ! ( velocity >= 0 && velocity <= 1 ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The velocity must be between 0 and 1 but was " + velocity );
		}
		if ( ! ( seconds >= 0 && seconds <= MAX_NOTE_SECONDS ) || ! ( attack >= 0 && attack <= 60 ) || ! ( release >= 0 && release <= 60 ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "The seconds must be between 0 and " + MAX_NOTE_SECONDS + " and the attack and release between 0 and 60, but they were " + seconds + ", "
			        + attack + " and " + release );
		}
		Prepared	prepared	= prepare( nearest( midi ), midi );
		AudioBuffer	pitched		= prepared.pitched();
		int			rate		= pitched.getSampleRate();
		double		gate		= seconds > 0 ? seconds : pitched.getDurationSeconds();
		double		fadeOut		= seconds > 0 ? release : 0;
		int			gateFrames	= ( int ) Math.round( gate * rate );
		int			total		= gateFrames + ( int ) Math.round( fadeOut * rate );
		int			channels	= pitched.getChannels();
		float[][]	out			= new float[ channels ][ total ];
		for ( int ch = 0; ch < channels; ch++ ) {
			float[]	in	= pitched.channel( ch );
			float[]	dst	= out[ ch ];
			if ( prepared.loop() == null || total <= in.length ) {
				System.arraycopy( in, 0, dst, 0, Math.min( in.length, total ) );
			} else {
				int head = Math.min( prepared.loopAt(), in.length );
				System.arraycopy( in, 0, dst, 0, Math.min( head, total ) );
				float[] block = prepared.loop().channel( ch );
				for ( int i = head, k = 0; i < total; i++, k++ ) {
					dst[ i ] = block[ k % block.length ];
				}
			}
		}
		int	attackFrames	= ( int ) Math.round( attack * rate );
		int	releaseFrames	= total - gateFrames;
		for ( int ch = 0; ch < channels; ch++ ) {
			float[] dst = out[ ch ];
			for ( int i = 0; i < total; i++ ) {
				double g = velocity;
				if ( i < attackFrames ) {
					g *= Math.sin( Math.PI / 2 * ( i + 0.5 ) / attackFrames );
				}
				if ( i >= gateFrames && releaseFrames > 0 ) {
					g *= Math.cos( Math.PI / 2 * ( i - gateFrames + 0.5 ) / releaseFrames );
				}
				dst[ i ] *= ( float ) g;
			}
		}
		return new AudioBuffer( out, rate );
	}

}
