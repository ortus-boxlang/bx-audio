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
package ortus.boxlang.modules.audio.tts;

import java.nio.file.Path;

/**
 * Where the downloaded assets live on disk: the text to speech engine, the voices, FFmpeg and caches. They are downloaded on request,
 * because together they are far too big for the module itself, and they are kept in the user's home so every BoxLang install shares them.
 * <p>
 * The folder is, in order: the module's {@code home} setting (see {@link Speech#home()}), the {@code bxaudio.home} system property, the
 * {@code BX_AUDIO_HOME} environment variable, or {@code .bxaudio} in the user's home folder.
 */
public final class TtsHome {

	private TtsHome() {
	}

	/**
	 * @return The folder that holds the engine and the voices
	 */
	public static Path home() {
		String configured = System.getProperty( "bxaudio.home" );
		if ( configured == null || configured.isBlank() ) {
			configured = System.getenv( "BX_AUDIO_HOME" );
		}
		if ( configured != null && !configured.isBlank() ) {
			return Path.of( configured ).toAbsolutePath();
		}
		return Path.of( System.getProperty( "user.home" ) ).resolve( ".bxaudio" ).toAbsolutePath();
	}

	/**
	 * @return The folder with one subfolder per installed voice
	 */
	public static Path voices( Path home ) {
		return home.resolve( "voices" );
	}

	/**
	 * @return The folder with one subfolder per engine version
	 */
	public static Path engine( Path home ) {
		return home.resolve( "engine" );
	}

	/**
	 * @return The folder with the FFmpeg download
	 */
	public static Path ffmpeg( Path home ) {
		return home.resolve( "ffmpeg" );
	}

	/**
	 * @return The folder for things that can be rebuilt, such as the explorer's rendered samples
	 */
	public static Path cache( Path home ) {
		return home.resolve( "cache" );
	}

}
