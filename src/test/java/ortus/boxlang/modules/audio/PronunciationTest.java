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

	@DisplayName( "BoxLang is spoken as two words, in any case and with an apostrophe" )
	@Test
	void testBoxLang() {
		assertThat( Pronunciation.apply( "Welcome to BoxLang.", null ) ).isEqualTo( "Welcome to Box Lang." );
		assertThat( Pronunciation.apply( "boxlang's speed", null ) ).isEqualTo( "Box Lang's speed" );
		assertThat( Pronunciation.apply( "BoxLang+ plans", null ) ).isEqualTo( "Box Lang Plus plans" );
	}

	@DisplayName( "A longer word that contains a name is left alone" )
	@Test
	void testWholeWordsOnly() {
		assertThat( Pronunciation.apply( "BoxLanguage and MP3s", null ) ).isEqualTo( "BoxLanguage and MP3s" );
	}

	@DisplayName( "The family names are split: ColdBox, CommandBox, BxAudio, BxLens" )
	@Test
	void testFamily() {
		assertThat( Pronunciation.apply( "ColdBox and CommandBox", null ) ).isEqualTo( "Cold Box and Command Box" );
		assertThat( Pronunciation.apply( "BxAudio and BxLens", null ) ).isEqualTo( "B X Audio and B X Lens" );
	}

	@DisplayName( "Audio terms are respelled" )
	@Test
	void testTerms() {
		assertThat( Pronunciation.apply( "Loudness in LUFS on the JVM, saved as MP3", null ) ).isEqualTo( "Loudness in loofs on the J V M, saved as M P 3" );
	}

	@DisplayName( "A caller's rule wins over a built in one" )
	@Test
	void testCustom() {
		assertThat( Pronunciation.apply( "BoxLang by Ortus", Map.of( "BoxLang", "Bocks Lang", "Ortus", "Ortuss" ) ) ).isEqualTo( "Bocks Lang by Ortuss" );
	}
}
