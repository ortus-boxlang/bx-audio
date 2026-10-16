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

import java.nio.file.Files;
import java.nio.file.Path;

import ortus.boxlang.modules.audio.codec.AudioFiles;
import ortus.boxlang.modules.audio.codec.WavCodec;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.util.FileSystemUtil;

@BoxBIF( description = "Get the format and level information of an audio buffer or file" )
public class AudioInfo extends BIF {

	/**
	 * Constructor
	 */
	public AudioInfo() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", KeyDictionary.source ),
		    new Argument( false, "boolean", Key.of( "levels" ), false )
		};
	}

	/**
	 * Describes some audio. A WAV file is described from its header, so a file of any size answers at once; the other formats are decoded.
	 * The peak and rms of a file are measured, which reads all of it, only when levels is true. A buffer always has them.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.source An audio buffer, or the path of an audio file.
	 *
	 * @argument.levels Measure peak and rms of a file too. Reads the whole file.
	 *
	 * @return A struct with channels, sampleRate, frames and seconds, and peak (0 to 1 for undistorted audio) and rms when they were measured.
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Object		source	= arguments.get( KeyDictionary.source );
		AudioBuffer	audio;
		if ( source instanceof AudioBuffer buffer ) {
			audio = buffer;
		} else if ( source instanceof String pathString ) {
			Path path = FileSystemUtil.expandPath( context, pathString ).absolutePath();
			if ( !Files.isRegularFile( path ) ) {
				throw new BoxRuntimeException( "The audio file [" + path + "] does not exist" );
			}
			if ( !arguments.getAsBoolean( Key.of( "levels" ) ) ) {
				WavCodec.Info header = WavCodec.info( path );
				if ( header != null ) {
					return Struct.ofNonConcurrent(
					    KeyDictionary.channels, header.channels(),
					    KeyDictionary.sampleRate, header.sampleRate(),
					    KeyDictionary.frames, header.frames(),
					    Key.seconds, header.frames() / ( double ) header.sampleRate()
					);
				}
			}
			audio = AudioFiles.read( path );
		} else {
			throw new BoxRuntimeException( "The source argument must be an audio buffer or the path of an audio file" );
		}
		return Struct.ofNonConcurrent(
		    KeyDictionary.channels, audio.getChannels(),
		    KeyDictionary.sampleRate, audio.getSampleRate(),
		    KeyDictionary.frames, audio.getFrames(),
		    Key.seconds, audio.getDurationSeconds(),
		    KeyDictionary.peak, ( double ) audio.getPeak(),
		    KeyDictionary.rms, audio.getRms()
		);
	}

}
