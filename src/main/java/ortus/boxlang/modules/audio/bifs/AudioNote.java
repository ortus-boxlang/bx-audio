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

import java.util.ArrayList;
import java.util.List;

import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.synth.Sampler;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;
import ortus.boxlang.modules.audio.util.Notes;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;

@BoxBIF( description = "Play a note, or a chord, of a sampler" )
public class AudioNote extends BIF {

	private static final Key	sampler		= Key.of( "sampler" );
	private static final Key	note		= Key.of( "note" );
	private static final Key	velocity	= Key.of( "velocity" );
	private static final Key	attack		= Key.of( "attack" );
	private static final Key	release		= Key.of( "release" );

	/**
	 * Constructor
	 */
	public AudioNote() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", sampler ),
		    new Argument( true, "any", note ),
		    new Argument( false, "double", Key.seconds, 0.0 ),
		    new Argument( false, "double", velocity, 1.0 ),
		    new Argument( false, "double", attack, 0.003 ),
		    new Argument( false, "double", release, 0.05 )
		};
	}

	/**
	 * Plays one note, or a chord, of an instrument made with audioSampler(), and gives it back as audio to place on a timeline with audioAdd(). Notes are
	 * played by playing the recording faster or slower, so a higher note is also shorter unless the sampler loops.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.sampler The instrument from audioSampler().
	 *
	 * @argument.note A note name such as C4, F#3 or Bb2 (C4 is middle C), a MIDI number where 69 is A4, or an array of them for a chord. Fractions
	 *                detune.
	 *
	 * @argument.seconds How long the note is held. 0, the default, plays the recording through. The note lasts a little longer than this, for the
	 *                   release.
	 *
	 * @argument.velocity How hard the note is played, 0 to 1, which is its loudness. Defaults to 1.
	 *
	 * @argument.attack Seconds of fade in, which keeps a note from clicking. Defaults to 0.003.
	 *
	 * @argument.release Seconds the note fades out after it was held. Ignored when seconds is 0. Defaults to 0.05.
	 *
	 * @return An audio buffer. A chord is the notes added together, so give each a lower velocity when they would be too loud.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		if ( ! ( arguments.get( sampler ) instanceof Sampler instrument ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The sampler argument must be an instrument from audioSampler()" );
		}
		Object			given	= arguments.get( note );
		List<Object>	notes	= new ArrayList<>();
		if ( given instanceof Array list ) {
			notes.addAll( list );
			if ( notes.isEmpty() ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT, "The chord has no notes" );
			}
		} else {
			notes.add( given );
		}
		List<AudioBuffer> played = new ArrayList<>();
		for ( Object each : notes ) {
			played.add( instrument.note( Notes.midi( each ), arguments.getAsDouble( Key.seconds ), arguments.getAsDouble( velocity ),
			    arguments.getAsDouble( attack ), arguments.getAsDouble( release ) ) );
		}
		return played.size() == 1 ? played.get( 0 ) : Dsp.mix( played );
	}

}
