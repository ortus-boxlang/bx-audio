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

/**
 * One sound being played by a {@link Mixer}. A script holds it to change the sound while it plays or to stop it. All changes are smooth: they
 * are applied across the next block of audio, so there are no clicks.
 */
public final class Playback {

	final float[][]				data;
	final int					frames;
	final String				bus;
	final boolean				loop;
	final double				loopStart;
	final double				loopEnd;

	volatile double				volume;
	volatile double				pan;
	volatile double				pitch;
	volatile boolean			paused;
	volatile boolean			finished;

	// Written by the audio thread only
	double						position;
	double						currentGain;
	private volatile double		fadeTarget	= -1;
	private volatile int		fadeFramesLeft;
	private volatile boolean	stopWhenFaded;
	private double				fadeLevel	= 1;

	Playback( float[][] data, String bus, double volume, double pan, double pitch, boolean loop, double loopStart, double loopEnd, double startFrame ) {
		this.data			= data;
		this.frames			= data[ 0 ].length;
		this.bus			= bus;
		this.volume			= volume;
		this.pan			= pan;
		this.pitch			= pitch;
		this.loop			= loop;
		this.loopStart		= loopStart;
		this.loopEnd		= loopEnd;
		this.position		= startFrame;
		this.currentGain	= -1;
	}

	public boolean isPlaying() {
		return !this.finished && !this.paused;
	}

	public boolean isFinished() {
		return this.finished;
	}

	public boolean isPaused() {
		return this.paused;
	}

	public String getBus() {
		return this.bus;
	}

	public double getVolume() {
		return this.volume;
	}

	public double getPan() {
		return this.pan;
	}

	public double getPitch() {
		return this.pitch;
	}

	/**
	 * @return How far into the sound the playback is, in seconds of the sound at normal speed
	 */
	public double getPosition() {
		return this.position / this.rate;
	}

	volatile int rate = 44100;

	public Playback setVolume( double volume ) {
		this.volume = Math.max( 0, volume );
		return this;
	}

	/**
	 * @param pan -1 for the left speaker, 0 for the middle, 1 for the right
	 */
	public Playback setPan( double pan ) {
		this.pan = Math.max( -1, Math.min( 1, pan ) );
		return this;
	}

	/**
	 * @param pitch Playback speed: 1 is normal, 2 is an octave up and twice as fast, 0.5 an octave down and half as fast
	 */
	public Playback setPitch( double pitch ) {
		if ( pitch <= 0 || pitch > 16 ) {
			throw new ortus.boxlang.runtime.types.exceptions.BoxRuntimeException( "The pitch must be above 0 and at most 16 but was " + pitch );
		}
		this.pitch = pitch;
		return this;
	}

	public Playback pause() {
		this.paused = true;
		return this;
	}

	public Playback resume() {
		this.paused = false;
		return this;
	}

	/**
	 * Stop at once
	 */
	public void stop() {
		this.finished = true;
	}

	/**
	 * Fade out over a time, then stop
	 */
	public void stop( double fadeSeconds ) {
		if ( fadeSeconds <= 0 ) {
			stop();
			return;
		}
		fadeTo( 0, fadeSeconds );
		this.stopWhenFaded = true;
	}

	/**
	 * Move the volume to a level over a time, on top of the volume setting
	 */
	public Playback fadeTo( double level, double seconds ) {
		this.stopWhenFaded	= false;
		this.fadeTarget		= Math.max( 0, level );
		this.fadeFramesLeft	= Math.max( 1, ( int ) Math.round( seconds * this.rate ) );
		return this;
	}

	/**
	 * Called by the mixer for each frame to get the fade level
	 */
	double advanceFade() {
		if ( this.fadeTarget >= 0 && this.fadeFramesLeft > 0 ) {
			this.fadeLevel += ( this.fadeTarget - this.fadeLevel ) / this.fadeFramesLeft;
			if ( --this.fadeFramesLeft == 0 ) {
				this.fadeLevel	= this.fadeTarget;
				this.fadeTarget	= -1;
				if ( this.stopWhenFaded ) {
					this.finished = true;
				}
			}
		}
		return this.fadeLevel;
	}

	void setFadeLevel( double level ) {
		this.fadeLevel = level;
	}

}
