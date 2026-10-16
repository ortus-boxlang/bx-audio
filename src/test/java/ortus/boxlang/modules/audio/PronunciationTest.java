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
package ortus.boxlang.modules.audio;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.tts.Pronunciation;

/**
 * The respellings that make the voice say the BoxLang family names properly
 */
public class PronunciationTest {

	@DisplayName( "BoxLang and BxAudio are left for the voice's dictionary, which says them as one word" )
	@Test
	void testDictionaryWords() {
		assertThat( Pronunciation.apply( "Welcome to BoxLang and BxAudio.", null ) ).isEqualTo( "Welcome to BoxLang and BxAudio." );
		assertThat( Pronunciation.apply( "bx-audio and BoxLang+", null ) ).isEqualTo( "BxAudio and BoxLang Plus" );
	}

	@DisplayName( "A longer word that contains a name is left alone" )
	@Test
	void testWholeWordsOnly() {
		assertThat( Pronunciation.apply( "BoxLanguage and MP3s", null ) ).isEqualTo( "BoxLanguage and MP3s" );
	}

	@DisplayName( "Other Bx products are said as Bee Ex and the name" )
	@Test
	void testBxProducts() {
		assertThat( Pronunciation.apply( "BxLens and bxSites", null ) ).isEqualTo( "Bee Ex Lens and Bee Ex Sites" );
	}

	@DisplayName( "Terms with digits or letters are respelled" )
	@Test
	void testTerms() {
		assertThat( Pronunciation.apply( "Saved as MP3 with FFmpeg", null ) ).isEqualTo( "Saved as M P 3 with F F M peg" );
	}

	@DisplayName( "A caller's rule wins over a built in one" )
	@Test
	void testCustom() {
		assertThat( Pronunciation.apply( "BoxLang by Ortus", Map.of( "BoxLang", "Bocks Lang", "Ortus", "Ortuss" ) ) ).isEqualTo( "Bocks Lang by Ortuss" );
	}
}
