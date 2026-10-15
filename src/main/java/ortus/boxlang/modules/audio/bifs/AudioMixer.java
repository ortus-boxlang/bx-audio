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
package ortus.boxlang.modules.audio.bifs;

import ortus.boxlang.modules.audio.realtime.Mixer;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;

@BoxBIF( description = "Open a real time mixer that plays many sounds at once with volume, pan, pitch, loops and buses, the way a game needs" )
public class AudioMixer extends BIF {

	private static final Key	sampleRate	= Key.of( "sampleRate" );
	private static final Key	channels	= Key.of( "channels" );
	private static final Key	output		= Key.of( "output" );
	private static final Key	buses		= Key.of( "buses" );

	/**
	 * Constructor
	 */
	public AudioMixer() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "integer", sampleRate, 44100 ),
		    new Argument( false, "integer", channels, 2 ),
		    new Argument( false, "string", output, "speakers" ),
		    new Argument( false, "struct", buses )
		};
	}

	/**
	 * Open a real time mixer that plays many sounds at once with volume, pan, pitch, loops and buses, the way a game needs
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.sampleRate Output sample rate. Defaults to 44100.
	 *
	 * @argument.channels 1 or 2. Defaults to 2.
	 *
	 * @argument.output speakers plays to the sound card. null keeps time with no sound, for servers. memory runs nothing: call render(seconds) on the
	 *                  mixer to get the audio. Defaults to speakers.
	 *
	 * @argument.buses Starting volumes of named buses, for example { music: 0.7, sfx: 1 }. A sound goes on a bus with the bus option of play().
	 *
	 * @return A mixer. Its methods: play(sound, options) returns a playback; options are bus, volume, pan, pitch, loop, loopStart, loopEnd, start and
	 *         fadeIn. A playback has stop(fadeSeconds), pause(), resume(), setVolume(v), setPan(p), setPitch(p), fadeTo(level, seconds), isPlaying() and
	 *         getPosition(). The mixer has crossfade(playback, sound, seconds, options), setVolume(v), setVolume(bus, v), stopAll(), stopAll(bus, fade),
	 *         getActiveCount(), waitUntilSilent(seconds), render(seconds) for memory mixers, and close().
	 */
	public Mixer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Mixer mixer = new Mixer( arguments.getAsInteger( sampleRate ), arguments.getAsInteger( channels ), arguments.getAsString( output ) );
		if ( arguments.get( buses ) instanceof IStruct given ) {
			given.forEach( ( k, v ) -> {
				if ( v instanceof Number n ) {
					mixer.setVolume( k.getName(), n.doubleValue() );
				}
			} );
		}
		return mixer;
	}

}
