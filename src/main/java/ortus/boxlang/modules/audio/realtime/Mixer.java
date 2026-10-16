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
package ortus.boxlang.modules.audio.realtime;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Plays many sounds at once, the way a game does: each sound has its own volume, pan and pitch, sounds belong to buses (music, effects,
 * voice) with their own volume, and a master volume sits on top. The mixing is done in the process, in blocks, by one method that either a
 * thread feeding the sound card calls, or the script itself calls through {@link #render} to get the audio as a buffer.
 */
public final class Mixer implements AutoCloseable {

	public static final int						BLOCK		= 512;

	private final int							rate;
	private final int							channels;
	private final String						output;
	private final List<Playback>				voices		= new CopyOnWriteArrayList<>();
	private final Map<String, Double>			buses		= new ConcurrentHashMap<>();
	private final Map<AudioBuffer, float[][]>	converted	= Collections.synchronizedMap( new WeakHashMap<>() );
	private volatile double						master		= 1.0;
	private volatile boolean					running;
	private Thread								device;
	private SourceDataLine						line;

	/**
	 * @param rate     Sample rate of the output in Hz
	 * @param channels 1 or 2
	 * @param output   speakers (the sound card), null (a real time clock and no sound, for servers) or memory (nothing runs: the script pulls audio
	 *                 with {@link #render})
	 */
	public Mixer( int rate, int channels, String output ) {
		if ( rate < 8000 || rate > 192000 ) {
			throw new BoxRuntimeException( "The mixer sample rate must be between 8000 and 192000 Hz but was " + rate );
		}
		if ( channels < 1 || channels > 2 ) {
			throw new BoxRuntimeException( "The mixer has 1 or 2 channels but was asked for " + channels );
		}
		String mode = output == null ? "speakers" : output.toLowerCase( Locale.ROOT );
		if ( !List.of( "speakers", "null", "memory" ).contains( mode ) ) {
			throw new BoxRuntimeException( "Unknown mixer output [" + output + "]. Use speakers, null or memory." );
		}
		this.rate		= rate;
		this.channels	= channels;
		this.output		= mode;
		if ( !mode.equals( "memory" ) ) {
			start();
		}
	}

	public int getSampleRate() {
		return this.rate;
	}

	public int getChannels() {
		return this.channels;
	}

	public String getOutput() {
		return this.output;
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Playing
	// ---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Play a sound with the defaults: bus "main", full volume, centered, normal pitch, once
	 */
	public Playback play( AudioBuffer sound ) {
		return play( sound, Map.of() );
	}

	/**
	 * Play a sound
	 *
	 * @param sound   The audio. It is converted to the mixer's rate once and kept for next time.
	 * @param options bus (text), volume, pan (-1 to 1), pitch (speed factor), loop (true or false), loopStart and loopEnd (seconds inside the sound, to
	 *                loop only a part), start (seconds into the sound to begin at) and fadeIn (seconds)
	 */
	public Playback play( AudioBuffer sound, Map<?, ?> options ) {
		if ( this.closed ) {
			throw new BoxRuntimeException( "The mixer is closed" );
		}
		float[][]	data		= prepare( sound );
		int			frames		= data[ 0 ].length;
		boolean		loop		= bool( options, "loop", false );
		double		loopStart	= Math.max( 0, number( options, "loopStart", 0 ) * this.rate );
		double		loopEnd		= number( options, "loopEnd", 0 ) > 0 ? Math.min( frames, number( options, "loopEnd", 0 ) * this.rate ) : frames;
		if ( loop && loopEnd - loopStart < 64 ) {
			throw new BoxRuntimeException( "The loop must be at least 64 frames long: check loopStart and loopEnd" );
		}
		double start = number( options, "start", 0 );
		if ( start < 0 ) {
			throw new BoxRuntimeException( "The play option [start] cannot be negative but was " + start );
		}
		double pitch = number( options, "pitch", 1 );
		if ( pitch <= 0 || pitch > 16 ) {
			throw new BoxRuntimeException( "The pitch must be above 0 and at most 16 but was " + pitch );
		}
		Object		given	= lookup( options, "bus" );
		String		bus		= given == null ? "main" : String.valueOf( given );
		Playback	p		= new Playback( data, bus, Math.max( 0, number( options, "volume", 1 ) ),
		    Math.max( -1, Math.min( 1, number( options, "pan", 0 ) ) ), pitch, loop,
		    loopStart, loopEnd, Math.min( frames, start * this.rate ) );
		p.rate = this.rate;
		double fadeIn = number( options, "fadeIn", 0 );
		if ( fadeIn > 0 ) {
			p.setFadeLevel( 0 );
			p.fadeTo( 1, fadeIn );
		}
		this.voices.add( p );
		return p;
	}

	private float[][] prepare( AudioBuffer sound ) {
		return this.converted.computeIfAbsent( sound, s -> {
			AudioBuffer a = s.getSampleRate() == this.rate ? s : Dsp.resample( s, this.rate );
			if ( a.getChannels() > 2 ) {
				a = Dsp.toChannels( a, 1 );
			}
			return a.getChannels() == 1 ? new float[][] { a.channel( 0 ) } : new float[][] { a.channel( 0 ), a.channel( 1 ) };
		} );
	}

	/**
	 * Fade a playing sound out and start another in over the same time
	 *
	 * @return The new playback
	 */
	public Playback crossfade( Playback from, AudioBuffer to, double seconds, Map<?, ?> options ) {
		if ( from != null ) {
			from.stop( seconds );
		}
		java.util.Map<Object, Object> merged = new java.util.HashMap<>( options );
		merged.put( "fadeIn", seconds );
		return play( to, merged );
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Volume
	// ---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Set the master volume
	 */
	public Mixer setVolume( double volume ) {
		this.master = Math.max( 0, volume );
		return this;
	}

	/**
	 * Set the volume of a bus
	 */
	public Mixer setVolume( String bus, double volume ) {
		this.buses.put( bus, Math.max( 0, volume ) );
		return this;
	}

	public double getVolume() {
		return this.master;
	}

	public double getVolume( String bus ) {
		return this.buses.getOrDefault( bus, 1.0 );
	}

	/**
	 * Stop everything
	 */
	public void stopAll() {
		this.voices.forEach( Playback::stop );
	}

	/**
	 * Stop every sound on a bus, with a fade
	 */
	public void stopAll( String bus, double fadeSeconds ) {
		this.voices.stream().filter( v -> v.bus.equals( bus ) ).forEach( v -> v.stop( fadeSeconds ) );
	}

	/**
	 * @return How many sounds are playing or paused
	 */
	public int getActiveCount() {
		return ( int ) this.voices.stream().filter( v -> !v.finished ).count();
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Mixing
	// ---------------------------------------------------------------------------------------------------------------------------

	/**
	 * Mix the next block of audio into {@code out}, which is overwritten
	 *
	 * @param out    One array per output channel, each at least {@code frames} long
	 * @param frames How many frames to produce
	 */
	synchronized void mix( float[][] out, int frames ) {
		for ( float[] channel : out ) {
			java.util.Arrays.fill( channel, 0, frames, 0f );
		}
		for ( Playback v : this.voices ) {
			if ( v.finished ) {
				this.voices.remove( v );
				continue;
			}
			if ( v.paused ) {
				continue;
			}
			try {
				mixVoice( v, out, frames );
			} catch ( RuntimeException e ) {
				// One sound that cannot be mixed must not stop the rest, nor the thread that keeps time: drop it and say why
				v.finished = true;
				logError( "A sound was dropped from the mixer: " + e );
			}
		}
		for ( float[] channel : out ) {
			for ( int i = 0; i < frames; i++ ) {
				float x = channel[ i ];
				channel[ i ] = x > 1f ? 1f : x < -1f ? -1f : x;
			}
		}
	}

	private void mixVoice( Playback v, float[][] out, int frames ) {
		double	pan				= v.pan;
		double	angle			= ( pan + 1 ) * Math.PI / 4;
		boolean	stereoSource	= v.data.length == 2;
		// A stereo sound keeps its level in the middle, a mono one loses 3 dB there like any constant power pan law
		double	norm			= stereoSource ? Math.sqrt( 2 ) : 1;
		double	left			= Math.cos( angle ) * norm;
		double	right			= Math.sin( angle ) * norm;
		double	bus				= this.buses.getOrDefault( v.bus, 1.0 ) * this.master;
		double	gain0			= v.currentGain < 0 ? v.volume * bus : v.currentGain;
		double	target			= v.volume * bus;
		float[]	l				= v.data[ 0 ];
		float[]	r				= stereoSource ? v.data[ 1 ] : l;
		double	step			= v.pitch;
		for ( int i = 0; i < frames; i++ ) {
			double position = v.position;
			if ( v.loop && position >= v.loopEnd ) {
				position	-= ( v.loopEnd - v.loopStart ) * Math.floor( ( position - v.loopStart ) / ( v.loopEnd - v.loopStart ) );
				v.position	= position;
			}
			if ( position >= v.frames - 1 ) {
				if ( !v.loop ) {
					v.finished = true;
					break;
				}
			}
			int		index	= ( int ) position;
			double	frac	= position - index;
			int		next	= index + 1;
			if ( next >= v.loopEnd && v.loop ) {
				next = ( int ) v.loopStart + ( next - ( int ) v.loopEnd );
			}
			if ( next >= v.frames ) {
				next = v.frames - 1;
			}
			double	sl		= l[ index ] + ( l[ next ] - l[ index ] ) * frac;
			double	sr		= r[ index ] + ( r[ next ] - r[ index ] ) * frac;
			double	gain	= ( gain0 + ( target - gain0 ) * ( i + 1 ) / frames ) * v.advanceFade();
			if ( this.channels == 1 ) {
				out[ 0 ][ i ] += ( float ) ( ( sl + sr ) * 0.5 * gain );
			} else {
				out[ 0 ][ i ]	+= ( float ) ( sl * left * gain );
				out[ 1 ][ i ]	+= ( float ) ( sr * right * gain );
			}
			v.position = position + step;
		}
		v.currentGain = target;
	}

	/**
	 * Mix audio without a sound card: the sounds playing now are mixed for a length of time and returned. Only for a mixer opened with output
	 * memory, since the others are already being pulled by their own clock.
	 */
	public AudioBuffer render( double seconds ) {
		if ( !this.output.equals( "memory" ) ) {
			throw new BoxRuntimeException( "render() is for a mixer opened with output=\"memory\"; this one plays to " + this.output );
		}
		if ( seconds < 0 || seconds > 3600 ) {
			throw new BoxRuntimeException( "Seconds must be between 0 and 3600 but was " + seconds );
		}
		int			total	= ( int ) Math.round( seconds * this.rate );
		float[][]	result	= new float[ this.channels ][ total ];
		float[][]	block	= new float[ this.channels ][ BLOCK ];
		for ( int done = 0; done < total; done += BLOCK ) {
			int n = Math.min( BLOCK, total - done );
			mix( block, n );
			for ( int ch = 0; ch < this.channels; ch++ ) {
				System.arraycopy( block[ ch ], 0, result[ ch ], done, n );
			}
		}
		return new AudioBuffer( result, this.rate );
	}

	// ---------------------------------------------------------------------------------------------------------------------------
	// Device
	// ---------------------------------------------------------------------------------------------------------------------------

	private volatile boolean closed;

	private void start() {
		if ( this.output.equals( "speakers" ) ) {
			try {
				AudioFormat format = new AudioFormat( this.rate, 16, this.channels, true, false );
				this.line = AudioSystem.getSourceDataLine( format );
				this.line.open( format, BLOCK * this.channels * 2 * 6 );
				this.line.start();
			} catch ( LineUnavailableException | IllegalArgumentException | SecurityException e ) {
				throw new BoxRuntimeException( "There is no audio output device to play to (" + e.getMessage()
				    + "). On a server without a sound card use output=\"null\" to keep time without sound, or output=\"memory\" and render() to get the audio.",
				    e );
			}
		}
		this.running	= true;
		this.device		= new Thread( this::run, "bxaudio-mixer" );
		this.device.setDaemon( true );
		this.device.start();
	}

	private static void logError( String message ) {
		try {
			ortus.boxlang.runtime.logging.LoggingService.getInstance().RUNTIME_LOGGER.error( message );
		} catch ( RuntimeException | LinkageError e ) {
			// no logger available, as in a plain Java program: the failure has already been handled
		}
	}

	private void run() {
		float[][]	block	= new float[ this.channels ][ BLOCK ];
		byte[]		bytes	= new byte[ BLOCK * this.channels * 2 ];
		long		next	= System.nanoTime();
		long		period	= ( long ) ( 1e9 * BLOCK / this.rate );
		while ( this.running ) {
			try {
				mix( block, BLOCK );
			} catch ( RuntimeException e ) {
				logError( "The mixer could not mix a block of audio: " + e );
				java.util.Arrays.fill( block[ 0 ], 0f );
			}
			if ( this.line != null ) {
				int k = 0;
				for ( int i = 0; i < BLOCK; i++ ) {
					for ( int ch = 0; ch < this.channels; ch++ ) {
						int s = Math.round( block[ ch ][ i ] * 32767f );
						bytes[ k++ ]	= ( byte ) s;
						bytes[ k++ ]	= ( byte ) ( s >> 8 );
					}
				}
				this.line.write( bytes, 0, bytes.length );
			} else {
				next += period;
				long wait = next - System.nanoTime();
				if ( wait > 0 ) {
					try {
						Thread.sleep( wait / 1_000_000, ( int ) ( wait % 1_000_000 ) );
					} catch ( InterruptedException e ) {
						return;
					}
				}
			}
		}
	}

	/**
	 * Wait until nothing is playing any more, or the time runs out
	 *
	 * @return True if everything finished
	 */
	public boolean waitUntilSilent( double timeoutSeconds ) {
		long end = System.nanoTime() + ( long ) ( timeoutSeconds * 1e9 );
		while ( getActiveCount() > 0 && System.nanoTime() < end ) {
			try {
				Thread.sleep( 5 );
			} catch ( InterruptedException e ) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return getActiveCount() == 0;
	}

	@Override
	public void close() {
		this.closed		= true;
		this.running	= false;
		if ( this.device != null ) {
			this.device.interrupt();
			try {
				this.device.join( 1000 );
			} catch ( InterruptedException e ) {
				Thread.currentThread().interrupt();
			}
		}
		if ( this.line != null ) {
			this.line.drain();
			this.line.close();
			this.line = null;
		}
		this.voices.clear();
	}

	/**
	 * Find an option by name whatever its case, since a BoxLang struct keeps keys as written
	 */
	private static Object lookup( Map<?, ?> options, String key ) {
		for ( Map.Entry<?, ?> entry : options.entrySet() ) {
			if ( String.valueOf( entry.getKey() ).equalsIgnoreCase( key ) ) {
				return entry.getValue();
			}
		}
		return null;
	}

	private static double number( Map<?, ?> options, String key, double fallback ) {
		if ( lookup( options, key ) instanceof Number n ) {
			double value = n.doubleValue();
			if ( Double.isNaN( value ) || Double.isInfinite( value ) ) {
				throw new BoxRuntimeException( "The play option [" + key + "] must be a finite number but was " + value );
			}
			return value;
		}
		return fallback;
	}

	private static boolean bool( Map<?, ?> options, String key, boolean fallback ) {
		return lookup( options, key ) instanceof Boolean b ? b : fallback;
	}

}
