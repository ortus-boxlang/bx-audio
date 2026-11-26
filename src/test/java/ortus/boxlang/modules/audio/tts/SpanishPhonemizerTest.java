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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The expected phonemes are what espeak-ng 1.51's {@code es} voice (and {@code es-419} for the Latin American ones) says, which is what Kokoro was
 * trained on.
 */
public class SpanishPhonemizerTest {

	private static String es( String text ) {
		return SpanishPhonemizer.phonemize( text, false );
	}

	@DisplayName( "Words take the stress of their written accent or their ending" )
	@Test
	void testStress() {
		assertThat( es( "hola" ) ).isEqualTo( "ˈola" );
		assertThat( es( "comando" ) ).isEqualTo( "komˈando" );
		assertThat( es( "ratón" ) ).isEqualTo( "ratˈon" );
		assertThat( es( "dónde" ) ).isEqualTo( "dˈonde" );
		assertThat( es( "verdad" ) ).isEqualTo( "beɾðˈad" );
		assertThat( es( "batería" ) ).isEqualTo( "bˌateɾˈia" );
		assertThat( es( "alrededor" ) ).isEqualTo( "ˌalreðeðˈoɾ" );
	}

	@DisplayName( "Function words carry no stress mark, and the longer ones only a secondary one" )
	@Test
	void testFunctionWords() {
		assertThat( es( "el niño quiere queso y pan" ) ).isEqualTo( "el nˈiɲo kjˈeɾe kˈeso i pˈan" );
		assertThat( es( "me gusta el café y el té" ) ).isEqualTo( "me ɣˈusta el kafˈe i el tˈe" );
		assertThat( es( "esto es para mí" ) ).isEqualTo( "ˈesto ˈes pˌaɾa mˈi" );
	}

	@DisplayName( "Diphthongs, hiatus and the semivowels" )
	@Test
	void testVowels() {
		assertThat( es( "bien bueno quien" ) ).isEqualTo( "bjˈem bwˈeno kjˈen" );
		assertThat( es( "aire causa hoy rey" ) ).isEqualTo( "ˈaɪɾe kˈaʊsa ˈoɪ rˈeɪ" );
		assertThat( es( "río país leer baúl" ) ).isEqualTo( "rˈio paˈis leˈeɾ βaˈul" );
		assertThat( es( "muy" ) ).isEqualTo( "mˈuj" );
		assertThat( es( "acción" ) ).isEqualTo( "akθjˈon" );
	}

	@DisplayName( "Consonants: the soft b d g, ñ, ll, ch, qu, gu, j, h, x, r and rr" )
	@Test
	void testConsonants() {
		assertThat( es( "vaso lavar alba" ) ).isEqualTo( "bˈaso laβˈaɾ ˈalβa" );
		assertThat( es( "dado desde el dedo" ) ).isEqualTo( "dˈaðo ðˌesðe el ðˈeðo" );
		assertThat( es( "gato agua guerra" ) ).isEqualTo( "ɡˈato ˈaɣwa ɣˈera" );
		assertThat( es( "niño calle hecho" ) ).isEqualTo( "nˈiɲo kˈaʎe ˈetʃo" );
		assertThat( es( "queso mejor ahora" ) ).isEqualTo( "kˈeso mexˈoɾ aˈoɾa" );
		assertThat( es( "perro pero rápido honra enredo" ) ).isEqualTo( "pˈero pˌeɾo rˈapiðo ˈonra enrˈeðo" );
		assertThat( es( "examen xilófono" ) ).isEqualTo( "eksˈamen silˈofono" );
		assertThat( es( "gracias cielo zapatos" ) ).isEqualTo( "ɡɾˈaθjas θjˈelo θapˈatos" );
	}

	@DisplayName( "A sound depends on the one before it, even across a space" )
	@Test
	void testAssimilation() {
		assertThat( es( "un vaso" ) ).isEqualTo( "ˈum bˈaso" );
		assertThat( es( "con pan" ) ).isEqualTo( "kom pˈan" );
		assertThat( es( "enviar" ) ).isEqualTo( "embjˈaɾ" );
		assertThat( es( "tengo" ) ).isEqualTo( "tˈɛŋɡo" );
		assertThat( es( "los vasos" ) ).isEqualTo( "los βˈasos" );
	}

	@DisplayName( "A whole sentence, with its punctuation kept" )
	@Test
	void testSentence() {
		assertThat( es( "¿Quieres que ejecute el comando?" ) ).isEqualTo( "kjˈeɾes ke ˌexekˈute el komˈando?" );
		assertThat( es( "Hola, soy Boxy, tu asistente." ) ).isEqualTo( "ˈola, sˈoɪ βˈoksi, tu ˌasistˈɛnte." );
	}

	@DisplayName( "The Latin American accent says z and ce, ci as s" )
	@Test
	void testLatinAmerican() {
		assertThat( SpanishPhonemizer.phonemize( "zapatos cielo gracias", true ) ).isEqualTo( "sapˈatos sjˈelo ɣɾˈasjas" );
		assertThat( SpanishPhonemizer.phonemize( "doscientos", true ) ).isEqualTo( "dosjˈɛntos" );
	}

	@DisplayName( "Numbers and percent signs are said in words" )
	@Test
	void testNumbers() {
		assertThat( SpanishPhonemizer.words( "0" ) ).isEqualTo( "cero" );
		assertThat( SpanishPhonemizer.words( "21" ) ).isEqualTo( "veintiuno" );
		assertThat( SpanishPhonemizer.words( "80" ) ).isEqualTo( "ochenta" );
		assertThat( SpanishPhonemizer.words( "101" ) ).isEqualTo( "ciento uno" );
		assertThat( SpanishPhonemizer.words( "100" ) ).isEqualTo( "cien" );
		assertThat( SpanishPhonemizer.words( "1985" ) ).isEqualTo( "mil novecientos ochenta y cinco" );
		assertThat( SpanishPhonemizer.words( "2000000" ) ).isEqualTo( "dos millones" );
		assertThat( SpanishPhonemizer.expandNumbers( "80%" ).trim().replaceAll( "\\s+", " " ) ).isEqualTo( "ochenta por ciento" );
		assertThat( SpanishPhonemizer.expandNumbers( "1.250 euros" ).trim().replaceAll( "\\s+", " " ) ).isEqualTo( "mil doscientos cincuenta euros" );
		assertThat( SpanishPhonemizer.expandNumbers( "3,5" ).trim().replaceAll( "\\s+", " " ) ).isEqualTo( "tres coma cinco" );
	}
}
