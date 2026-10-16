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

import ortus.boxlang.modules.audio.analysis.Loudness;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

@BoxBIF( description = "Measure the loudness of audio as EBU R128 does: integrated LUFS, loudness range, true peak" )
public class AudioLoudness extends BIF {

	private static final Key audio = Key.of( "audio" );

	/**
	 * Constructor
	 */
	public AudioLoudness() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio )
		};
	}

	/**
	 * Measure the loudness of audio as EBU R128 does: integrated LUFS, loudness range, true peak
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @return A struct with integrated (LUFS, gated, the number streaming services use), range (LU), truePeakDb and samplePeakDb (dBFS), momentaryMax and
	 *         shortTermMax (LUFS). Silence or audio shorter than 400 ms reads -120.
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Loudness.Result r = Loudness.measure( Audios.require( arguments.get( audio ), "audio" ) );
		return Struct.ofNonConcurrent( "integrated", r.integrated(), "range", r.range(), "truePeakDb", r.truePeak(), "samplePeakDb", r.samplePeak(),
		    "momentaryMax",
		    r.momentaryMax(), "shortTermMax", r.shortTermMax() );
	}

}
