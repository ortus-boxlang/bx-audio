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

import ortus.boxlang.modules.audio.synth.Sampler;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.modules.audio.util.Notes;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;

@BoxBIF( description = "Turn a recording into an instrument that plays any note" )
public class AudioSampler extends BIF {

	private static final Key	audio		= Key.of( "audio" );
	private static final Key	rootNote	= Key.of( "rootNote" );
	private static final Key	loop		= Key.of( "loop" );
	private static final Key	loopStart	= Key.of( "loopStart" );
	private static final Key	loopEnd		= Key.of( "loopEnd" );
	private static final Key	crossfade	= Key.of( "crossfade" );

	/**
	 * Constructor
	 */
	public AudioSampler() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( false, "any", rootNote, "C4" ),
		    new Argument( false, "boolean", loop, false ),
		    new Argument( false, "double", loopStart, 0.0 ),
		    new Argument( false, "double", loopEnd, 0.0 ),
		    new Argument( false, "double", crossfade, 0.02 )
		};
	}

	/**
	 * Makes an instrument out of a recording, such as a piano note, a vocal "ah", a guitar pluck, a drum hit or a synth pad. Ask the instrument for notes
	 * with audioNote(): it plays the recording faster for a higher note and slower for a lower one, like a tape keyboard, so the length changes with the
	 * pitch. Give several recordings of different pitches and the nearest one is used for each note, so a note is never shifted far and keeps its
	 * character.
	 *
	 * <p>
	 * A recording is as long as it is. For a note that is held longer, turn on loop: the loop (the whole recording, or the part from loopStart to
	 * loopEnd)
	 * is repeated until the note ends, with a short crossfade that hides the join. Choose a steady part of a pad, a string or an organ for the loop.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The recording, or a struct of recordings by the note they sound at, such as { C3: low, C4: middle, C5: high }. All must have the
	 *                 same
	 *                 channels and sample rate.
	 *
	 * @argument.rootNote The note the recording sounds at, a name such as C4 (middle C) or a MIDI number where 69 is A4. Fractions detune, 60.1 is ten
	 *                    cents
	 *                    above C4. Ignored when audio is a struct. Defaults to C4.
	 *
	 * @argument.loop True to repeat a loop of the recording while a note is held longer than the recording. Defaults to false.
	 *
	 * @argument.loopStart Where the loop starts in the recording, in seconds. Defaults to 0.
	 *
	 * @argument.loopEnd Where the loop ends in the recording, in seconds. 0, the default, is the end of the recording.
	 *
	 * @argument.crossfade Seconds of overlap that make the loop seamless. Defaults to 0.02.
	 *
	 * @return A sampler, to give to audioNote().
	 */
	public Sampler _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Object				given	= arguments.get( audio );
		List<Sampler.Zone>	zones	= new ArrayList<>();
		if ( given instanceof IStruct recordings ) {
			for ( Key key : recordings.keySet() ) {
				zones.add( new Sampler.Zone( Notes.midi( key.getName() ), Audios.require( recordings.get( key ), "audio" ) ) );
			}
			if ( zones.isEmpty() ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT, "The struct of recordings is empty" );
			}
		} else {
			AudioBuffer recording = Audios.require( given, "audio" );
			zones.add( new Sampler.Zone( Notes.midi( arguments.get( rootNote ) ), recording ) );
		}
		return new Sampler( zones, arguments.getAsBoolean( loop ), arguments.getAsDouble( loopStart ), arguments.getAsDouble( loopEnd ),
		    arguments.getAsDouble( crossfade ) );
	}

}
