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

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class KokoroEngineTest {

	@DisplayName( "A text is spoken sentence by sentence, lines apart" )
	@Test
	void testSentences() {
		assertThat( KokoroEngine.sentences( "One. Two!  Three?\nFour" ) ).containsExactly( "One.", "Two!", "Three?", "Four" ).inOrder();
		assertThat( KokoroEngine.sentences( "  \n " ) ).isEmpty();
		// a full stop inside a number or a name is not a sentence end
		assertThat( KokoroEngine.sentences( "Version 2.5 is out." ) ).containsExactly( "Version 2.5 is out." );
	}

	@DisplayName( "Phonemes longer than the model takes are cut at a space, and the pieces together are the whole" )
	@Test
	void testChunks() {
		String			word	= "abc";
		String			text	= ( word + " " ).repeat( 400 ).trim();
		List<String>	pieces	= KokoroEngine.chunks( text );
		assertThat( pieces.size() ).isAtLeast( 3 );
		for ( String piece : pieces ) {
			assertThat( piece.length() ).isAtMost( KokoroPhonemes.maxTokens() );
			assertThat( piece ).doesNotContain( "  " );
		}
		assertThat( String.join( " ", pieces ) ).isEqualTo( text );
		assertThat( KokoroEngine.chunks( "short" ) ).containsExactly( "short" );
		// a word with no space in it is cut at the limit rather than looped on
		assertThat( KokoroEngine.chunks( "x".repeat( 1200 ) ).size() ).isAtLeast( 3 );
	}

	@DisplayName( "Silence is cut from both ends of a sentence, and a sound with no silence is left whole" )
	@Test
	void testTrim() {
		float[] audio = new float[ 24000 ];
		for ( int i = 8000; i < 16000; i++ ) {
			audio[ i ] = ( float ) Math.sin( i * 0.3 ) * 0.5f;
		}
		float[] trimmed = KokoroEngine.trim( audio );
		// frames are 2048 samples, so those next to the sound count as sound: about a thousand samples more on each side
		assertThat( trimmed.length ).isLessThan( 11000 );
		assertThat( trimmed.length ).isAtLeast( 8000 );
		float[] loud = new float[ 4000 ];
		java.util.Arrays.fill( loud, 0.3f );
		assertThat( KokoroEngine.trim( loud ).length ).isEqualTo( 4000 );
		float[] silent = new float[ 4000 ];
		assertThat( KokoroEngine.trim( silent ) ).isEqualTo( silent );
	}

}
