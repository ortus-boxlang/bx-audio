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
import ortus.boxlang.modules.audio.tts.EngineInstaller;
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

@BoxBIF( description = "Download FFmpeg for this computer into the bx-audio home, checked against a pinned checksum" )
public class AudioFfmpegInstall extends BIF {

	private static final Key onProgress = Key.of( "onProgress" );

	/**
	 * Constructor
	 */
	public AudioFfmpegInstall() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "function", onProgress )
		};
	}

	/**
	 * Download FFmpeg for this computer into the bx-audio home, checked against a pinned checksum
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.onProgress A function called as ( name, bytesDone, bytesTotal ) while files download.
	 *
	 * @return A struct with installed, version, path and probePath.
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		var								callback	= arguments.get( onProgress );
		long[]							last		= { 0 };
		EngineInstaller.NamedProgress	progress	= callback == null ? null : ( file, done, total ) -> {
														long now = System.nanoTime();
														if ( done >= total || now - last[ 0 ] > 200_000_000L ) {
															last[ 0 ] = now;
															context.invokeFunction( callback, new Object[] { file, done, total } );
														}
													};
		FfmpegInstaller					installer	= Ffmpeg.installer();
		installer.install( progress );
		return Struct.ofNonConcurrent( "installed", installer.isInstalled(), "version", installer.getVersion(), "path", installer.ffmpegPath().toString(),
		    "probePath",
		    installer.ffprobePath().toString() );
	}

}
