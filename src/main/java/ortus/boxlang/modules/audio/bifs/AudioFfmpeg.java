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

import ortus.boxlang.modules.audio.ffmpeg.Ffmpeg;
import ortus.boxlang.modules.audio.ffmpeg.FfmpegInstaller;
import ortus.boxlang.modules.audio.tts.Speech;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

@BoxBIF( description = "Find FFmpeg on this computer and say what it can do: OGG, Opus, AAC and video files need it" )
public class AudioFfmpeg extends BIF {

	/**
	 * Constructor
	 */
	public AudioFfmpeg() {
		super();
		declaredArguments = new Argument[] {

		};
	}

	/**
	 * Find FFmpeg on this computer and say what it can do: OGG, Opus, AAC and video files need it
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * 
	 * @return A struct with available, source (configured, installed or system), path, probePath, version, encoders (libvorbis, libopus, aac, libmp3lame,
	 *         flac that it has), platform, installable (is there a download for this computer), downloadSizeMB and installDir.
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Ffmpeg			found		= Ffmpeg.locate();
		FfmpegInstaller	installer	= Ffmpeg.installer();
		IStruct			result		= Struct.ofNonConcurrent( "available", found != null, "platform", installer.getPlatform(), "installable",
		    installer.isSupported(), "downloadSizeMB",
		    Speech.megabytes( installer.downloadSize() ), "installDir", installer.getDir().toString() );
		if ( found != null ) {
			result.put( Key.of( "source" ), found.getSource() );
			result.put( Key.of( "path" ), found.getFfmpeg().toString() );
			result.put( Key.of( "probePath" ), found.getFfprobe().toString() );
			result.put( Key.of( "version" ), found.version() );
			result.put( Key.of( "encoders" ), Array.fromList( found.encoders() ) );
		}
		return result;
	}

}
