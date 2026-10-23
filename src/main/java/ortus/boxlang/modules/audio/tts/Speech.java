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
import java.util.Map;

import ortus.boxlang.modules.audio.tts.Downloader.Progress;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.ModuleSettings;
import ortus.boxlang.modules.audio.util.AudioException;

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

	private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * Say once per JVM that a voice has a license to check
	 */
	private static void warn( VoiceInfo voice ) {
		if ( !voice.commercial() && WARNED.add( voice.id() ) ) {
			try {
				ortus.boxlang.runtime.logging.LoggingService.getInstance().RUNTIME_LOGGER.warn( voice.warning() );
			} catch ( Throwable e ) {
				System.err.println( "Warning: " + voice.warning() );
			}
		}
	}

	/**
	 * An engine and a voice that are installed and ready to speak
	 */
	public record Ready( VoiceInfo voice, VoiceStore store, PiperEngine engine ) {

		public AudioBuffer say( String text, double speed ) {
			return say( text, speed, null );
		}

		/**
		 * @param pronunciations Caller supplied respellings, word to spoken form, see {@link Pronunciation}. May be null.
		 */
		public AudioBuffer say( String text, double speed, Map<String, String> pronunciations ) {
			if ( speed < 0.25 || speed > 4 ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT, "The speed must be between 0.25 and 4 but was " + speed );
			}
			return this.engine.speak( this.voice, this.store, Pronunciation.apply( text, pronunciations ), speed );
		}
	}

	/**
	 * Install what is missing, when that is allowed, and load the engine
	 *
	 * @param voiceName    A voice id or short name from the catalog
	 * @param allowInstall May the engine and the voice be downloaded now
	 * @param progress     Told how far the downloads have got, may be null
	 */
	public static Ready prepare( String voiceName, boolean allowInstall, EngineInstaller.NamedProgress progress ) {
		VoiceInfo		voice	= catalog().find( voiceName );
		VoiceStore		store	= store();
		EngineInstaller	engine	= engine();
		warn( voice );
		if ( !engine.isInstalled() ) {
			if ( !allowInstall ) {
				throw new AudioException( AudioException.ENGINE_NOT_INSTALLED,
				    "The text to speech engine is not installed (" + megabytes( engine.downloadSize() )
				        + " MB). Run `bx-audio install engine`, or call audioVoiceInstall( \"engine\" ), or allow downloads with autoInstall=true." );
			}
			engine.install( progress );
		}
		if ( !store.isInstalled( voice ) ) {
			if ( !allowInstall ) {
				throw new AudioException( AudioException.VOICE_NOT_INSTALLED,
				    "The voice [" + voice.id() + "] is not installed (" + megabytes( voice.downloadSize() )
				        + " MB). Run `bx-audio install " + voice.id() + "`, or call audioVoiceInstall( \"" + voice.id()
				        + "\" ), or allow downloads with autoInstall=true." );
			}
			Progress p = progress == null ? null : ( done, total ) -> progress.update( voice.id(), done, total );
			store.install( voice, p );
		}
		return new Ready( voice, store, PiperEngine.get( engine ) );
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
		return speak( text, voiceName, allowInstall, progress, 1.0 );
	}

	/**
	 * Speak a text at a speed, see {@link #speak(String, String, boolean, EngineInstaller.NamedProgress)}
	 */
	public static AudioBuffer speak( String text, String voiceName, boolean allowInstall, EngineInstaller.NamedProgress progress, double speed ) {
		return speak( text, voiceName, allowInstall, progress, speed, null );
	}

	/**
	 * Speak a text at a speed with the caller's own pronunciations, see {@link Pronunciation}
	 */
	public static AudioBuffer speak( String text, String voiceName, boolean allowInstall, EngineInstaller.NamedProgress progress, double speed,
	    Map<String, String> pronunciations ) {
		checkSayable( text, speed );
		return prepare( voiceName, allowInstall, progress ).say( text, speed, pronunciations );
	}

	/**
	 * Speak a text piece by piece, with chosen pauses, and report when each caption line is spoken
	 */
	public static Narration.Result narrate( String text, String voiceName, boolean allowInstall, EngineInstaller.NamedProgress progress, double speed,
	    Narration.Pauses pauses, boolean clauses, int maxChars ) {
		return narrate( text, voiceName, allowInstall, progress, speed, pauses, clauses, maxChars, null );
	}

	/**
	 * Speak a text piece by piece with the caller's own pronunciations. The captions keep the original words.
	 */
	public static Narration.Result narrate( String text, String voiceName, boolean allowInstall, EngineInstaller.NamedProgress progress, double speed,
	    Narration.Pauses pauses, boolean clauses, int maxChars, Map<String, String> pronunciations ) {
		checkSayable( text, speed );
		Ready ready = prepare( voiceName, allowInstall, progress );
		return Narration.narrate( text, piece -> ready.say( piece, speed, pronunciations ), pauses, clauses, maxChars );
	}

	/**
	 * Checked before anything is downloaded: the speed is in range and the text holds something the English voices can say
	 */
	private static void checkSayable( String text, double speed ) {
		if ( text == null || text.isBlank() ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "There is nothing to say: the text is empty" );
		}
		if ( Double.isNaN( speed ) || speed < 0.25 || speed > 4 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The speed must be between 0.25 and 4 but was " + speed );
		}
		boolean sayable = text.codePoints()
		    .anyMatch( c -> Character.isDigit( c ) && c < 128 || Character.isLetter( c ) && Character.UnicodeScript.of( c ) == Character.UnicodeScript.LATIN );
		if ( !sayable ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "The text has no words the voice can say. The voices speak US English, so letters of other scripts, symbols and emoji are not spoken." );
		}
	}

	/**
	 * @return Megabytes, rounded to one decimal
	 */
	public static double megabytes( long bytes ) {
		return Math.round( bytes / 1_000_000.0 * 10 ) / 10.0;
	}

}
