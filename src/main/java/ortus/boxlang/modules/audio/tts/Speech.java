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

import ortus.boxlang.modules.audio.tts.Downloader.Progress;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.ModuleSettings;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Text to speech as the BIFs use it: where things are, what is installed, and speaking with installation on first use.
 */
public final class Speech {

	private Speech() {
	}

	private static Object setting( String name ) {
		return ModuleSettings.get( name );
	}

	/**
	 * @return The folder with the engine and the voices: the module's {@code home} setting, else {@link TtsHome#home()}
	 */
	public static Path home() {
		Object configured = setting( "home" );
		return configured instanceof String text && !text.isBlank() ? Path.of( text ).toAbsolutePath() : TtsHome.home();
	}

	/**
	 * @return Whether {@code audioSpeak} may download the engine and the voice it needs: the module's {@code autoInstall} setting, true unless
	 *         turned off
	 */
	public static boolean autoInstall() {
		Object configured = setting( "autoInstall" );
		return ! ( configured instanceof Boolean flag ) || flag;
	}

	public static VoiceCatalog catalog() {
		return VoiceCatalog.load();
	}

	public static VoiceStore store() {
		return new VoiceStore( TtsHome.voices( home() ), catalog().getBaseUrl() );
	}

	public static EngineInstaller engine() {
		return new EngineInstaller( TtsHome.engine( home() ) );
	}

	/**
	 * Speak a text. The engine and the voice are downloaded first when {@code allowInstall} is true.
	 *
	 * @param text         What to say
	 * @param voiceName    A voice id or short name from the catalog
	 * @param allowInstall May the engine and the voice be downloaded now
	 * @param progress     Told how far the downloads have got, may be null
	 *
	 * @return Mono audio at the voice's sample rate
	 */
	public static AudioBuffer speak( String text, String voiceName, boolean allowInstall, EngineInstaller.NamedProgress progress ) {
		if ( text == null || text.isBlank() ) {
			throw new BoxRuntimeException( "There is nothing to say: the text is empty" );
		}
		VoiceInfo		voice	= catalog().find( voiceName );
		VoiceStore		store	= store();
		EngineInstaller	engine	= engine();
		if ( !engine.isInstalled() ) {
			if ( !allowInstall ) {
				throw new BoxRuntimeException( "The text to speech engine is not installed (" + megabytes( engine.downloadSize() )
				    + " MB). Run `bx-audio install engine`, or call audioVoiceInstall( \"engine\" ), or allow downloads with autoInstall=true." );
			}
			engine.install( progress );
		}
		if ( !store.isInstalled( voice ) ) {
			if ( !allowInstall ) {
				throw new BoxRuntimeException( "The voice [" + voice.id() + "] is not installed (" + megabytes( voice.downloadSize() )
				    + " MB). Run `bx-audio install " + voice.id() + "`, or call audioVoiceInstall( \"" + voice.id()
				    + "\" ), or allow downloads with autoInstall=true." );
			}
			Progress p = progress == null ? null : ( done, total ) -> progress.update( voice.id(), done, total );
			store.install( voice, p );
		}
		return PiperEngine.get( engine ).speak( voice, store, text );
	}

	/**
	 * @return Megabytes, rounded to one decimal
	 */
	public static double megabytes( long bytes ) {
		return Math.round( bytes / 1_000_000.0 * 10 ) / 10.0;
	}

}
