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

import ortus.boxlang.modules.audio.dsp.Dynamics;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

@BoxBIF( description = "Reduce the dynamic range of audio with a compressor" )
public class AudioCompress extends BIF {

	private static final Key	audio		= Key.of( "audio" );
	private static final Key	thresholdDb	= Key.of( "thresholdDb" );
	private static final Key	ratio		= Key.of( "ratio" );
	private static final Key	attackMs	= Key.of( "attackMs" );
	private static final Key	releaseMs	= Key.of( "releaseMs" );
	private static final Key	kneeDb		= Key.of( "kneeDb" );
	private static final Key	makeupDb	= Key.of( "makeupDb" );

	/**
	 * Constructor
	 */
	public AudioCompress() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( false, "double", thresholdDb, -18.0 ),
		    new Argument( false, "double", ratio, 4.0 ),
		    new Argument( false, "double", attackMs, 10.0 ),
		    new Argument( false, "double", releaseMs, 100.0 ),
		    new Argument( false, "double", kneeDb, 6.0 ),
		    new Argument( false, "any", makeupDb, "auto" )
		};
	}

	/**
	 * Reduce the dynamic range of audio with a compressor
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.thresholdDb Level above which the audio is turned down, in dB. Defaults to -18.
	 *
	 * @argument.ratio How strongly: 4 means 4 dB over the threshold becomes 1 dB. Defaults to 4.
	 *
	 * @argument.attackMs Milliseconds to turn down. Defaults to 10.
	 *
	 * @argument.releaseMs Milliseconds to turn back up. Defaults to 100.
	 *
	 * @argument.kneeDb Width in dB of the soft knee. 0 is a hard knee. Defaults to 6.
	 *
	 * @argument.makeupDb Gain added after compression in dB, or "auto" to match the loudness (RMS) of the input. Defaults to auto.
	 *
	 * @return A new audio buffer.
	 */
	public AudioBuffer _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Object		makeup	= arguments.get( makeupDb );
		AudioBuffer	input	= Audios.require( arguments.get( audio ), "audio" );
		if ( makeup instanceof Number n ) {
			return Dynamics.compress( input, arguments.getAsDouble( thresholdDb ), arguments.getAsDouble( ratio ), arguments.getAsDouble( attackMs ),
			    arguments.getAsDouble( releaseMs ), arguments.getAsDouble( kneeDb ), n.doubleValue() );
		}
		if ( "auto".equalsIgnoreCase( String.valueOf( makeup ) ) ) {
			return Dynamics.compressMatched( input, arguments.getAsDouble( thresholdDb ), arguments.getAsDouble( ratio ), arguments.getAsDouble( attackMs ),
			    arguments.getAsDouble( releaseMs ), arguments.getAsDouble( kneeDb ) );
		}
		throw new ortus.boxlang.runtime.types.exceptions.BoxRuntimeException( "makeupDb must be a number of dB or \"auto\" but was [" + makeup + "]" );
	}

}
