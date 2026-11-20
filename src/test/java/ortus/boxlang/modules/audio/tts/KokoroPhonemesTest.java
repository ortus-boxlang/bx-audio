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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.jr.ob.JSON;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class KokoroPhonemesTest {

	private final KokoroPhonemes phonemes = KokoroPhonemes.get();

	@DisplayName( "Only what Kokoro has a token for is kept, and each phoneme is one token" )
	@Test
	void testTokens() {
		assertThat( this.phonemes.known( "həlˈoʊ ♣" ) ).isEqualTo( "həlˈoʊ " );
		assertThat( this.phonemes.tokens( " " ) ).isEqualTo( new long[] { 16 } );
		assertThat( this.phonemes.tokens( "♣" ) ).isEmpty();
		assertThat( this.phonemes.tokens( "hə" ) ).hasLength( 2 );
		assertThat( KokoroPhonemes.maxTokens() ).isEqualTo( 510 );
	}

	@DisplayName( "A function word loses its stress and takes the spelling espeak-ng gives it" )
	@Test
	void testFunctionWords() {
		// the Voices dictionary says ðʌ, espeak-ng ðə
		assertThat( this.phonemes.map( "en_US", List.of( "ðʌ", " ", "kˈæt" ) ) ).isEqualTo( "ðə kˈæt" );
		// punctuation stays where it was
		assertThat( this.phonemes.map( "en_US", List.of( "kˈæt", ",", " ", "ðʌ", ".", "" ) ) ).isEqualTo( "kˈæt, ðə." );
	}

	@DisplayName( "American vowels: the British vowel of lot becomes ɑː, an unstressed ʌ a schwa, and a t between vowels a flap" )
	@Test
	void testAmericanVowels() {
		assertThat( this.phonemes.map( "en_US", List.of( "mˈɒdʌl" ) ) ).isEqualTo( "mˈɑːdəl" );
		assertThat( this.phonemes.map( "en_US", List.of( "ɑbʌv" ) ).contains( "ʌ" ) ).isFalse();
		assertThat( this.phonemes.map( "en_US", List.of( "rˈaɪtl" ) ) ).isEqualTo( "rˈaɪtl" );
		assertThat( this.phonemes.map( "en_US", List.of( "ɹˈaɪtɪŋ" ) ) ).isEqualTo( "ɹˈaɪɾɪŋ" );
	}

	@DisplayName( "British English keeps its own vowels, only the function words are put right" )
	@Test
	void testBritish() {
		assertThat( this.phonemes.map( "en_GB", List.of( "mˈɒdʌl" ) ) ).isEqualTo( "mˈɒdʌl" );
		assertThat( this.phonemes.map( "en_GB", List.of( "ðə", " ", "mˈɒdʌl" ) ).startsWith( "ðə " ) ).isTrue();
	}

	@DisplayName( "A language the dictionary does not have is refused" )
	@Test
	void testUnknownLanguage() {
		assertThat( assertThrows( BoxRuntimeException.class, () -> this.phonemes.map( "fr_FR", List.of( "a" ) ) ).getMessage() ).contains( "fr_FR" );
	}

	/**
	 * The cases in {@code kokoro/golden-phonemes.json} are what the first version of these rules, written in Python against the same table, gave
	 * for the phonemes the Voices dictionary returns for sentences of the documentation: the Java port must say the same.
	 */
	@DisplayName( "The Java rules say the same as the reference version of them, for 80 sentences" )
	@Test
	@SuppressWarnings( "unchecked" )
	void testGolden() throws IOException {
		try ( InputStream in = Files.newInputStream( Path.of( "src", "test", "resources", "kokoro", "golden-phonemes.json" ) ) ) {
			List<Object> cases = JSON.std.listFrom( in );
			assertThat( cases ).hasSize( 80 );
			for ( Object each : cases ) {
				Map<String, Object> c = ( Map<String, Object> ) each;
				assertThat( this.phonemes.map( ( String ) c.get( "language" ), ( List<String> ) c.get( "phonemes" ) ) ).isEqualTo( c.get( "mapped" ) );
			}
		}
	}

}
