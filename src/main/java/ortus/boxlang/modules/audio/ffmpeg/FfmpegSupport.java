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
package ortus.boxlang.modules.audio.ffmpeg;

import ortus.boxlang.modules.audio.tts.EngineInstaller.NamedProgress;
import ortus.boxlang.modules.audio.tts.Speech;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Getting hold of FFmpeg when a format needs it: use what is there, download it when that is allowed, or say how to get it
 */
public final class FfmpegSupport {

	private FfmpegSupport() {
	}

	/**
	 * @param why      What needs FFmpeg, for the message, for example "Reading .ogg files"
	 * @param install  May FFmpeg be downloaded now when it is missing
	 * @param progress Told about the download, may be null
	 *
	 * @throws BoxRuntimeException when there is no FFmpeg and none can be installed
	 */
	public static Ffmpeg require( String why, boolean install, NamedProgress progress ) {
		Ffmpeg found = Ffmpeg.locate();
		if ( found != null ) {
			return found;
		}
		FfmpegInstaller installer = Ffmpeg.installer();
		if ( install && installer.isSupported() ) {
			installer.install( progress );
			found = Ffmpeg.locate();
			if ( found != null ) {
				return found;
			}
		}
		throw new AudioException( AudioException.FFMPEG_MISSING,
		    why + " needs FFmpeg, and FFmpeg is not installed on this computer (it was not found in the bx-audio home or on the PATH). "
		        + ( installer.isSupported()
		            ? "Run `bx-audio ffmpeg install` or audioFfmpegInstall() to download it (" + Speech.megabytes( installer.downloadSize() )
		                + " MB, kept in " + Speech.home() + "), or allow downloads with autoInstall=true."
		            : "There is no download for this computer, so install FFmpeg so it is on the PATH." ) );
	}

	/**
	 * Same, downloading only when the module's autoInstall setting allows it
	 */
	public static Ffmpeg require( String why ) {
		return require( why, Speech.autoInstall(), null );
	}

}
