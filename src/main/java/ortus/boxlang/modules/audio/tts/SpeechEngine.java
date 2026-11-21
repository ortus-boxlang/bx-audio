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

import ortus.boxlang.modules.audio.types.AudioBuffer;

/**
 * Something that turns text into audio with an installed voice. {@link PiperEngine} speaks Piper voices and {@link KokoroEngine} Kokoro
 * voices; {@link Speech} picks by {@link VoiceInfo#engine()}.
 */
public interface SpeechEngine {

	/**
	 * Speak a text
	 *
	 * @param voice The voice, which must be installed in the store
	 * @param store Where the voices are
	 * @param text  What to say
	 * @param speed 1 is the voice's own pace, 2 twice as fast, 0.5 half as fast
	 *
	 * @return Mono audio at the voice's sample rate
	 */
	AudioBuffer speak( VoiceInfo voice, VoiceStore store, String text, double speed );

}
