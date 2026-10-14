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

import ortus.boxlang.runtime.BoxRuntime;

/**
 * Where the text to speech engine and the voices live on disk. Both are downloaded on first use, because together they are far too big
 * for the module itself.
 * <p>
 * The folder is, in order: the {@code bxaudio.home} system property, the {@code BX_AUDIO_HOME} environment variable, or {@code bx-audio}
 * inside the BoxLang home.
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
		return BoxRuntime.getInstance().getRuntimeHome().resolve( "bx-audio" );
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

}
