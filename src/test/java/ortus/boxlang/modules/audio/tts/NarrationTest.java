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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.tts.Narration.Cue;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class NarrationTest {

	/**
	 * A voice that takes a twentieth of a second a character, as a tone, with a little silence around it like a real voice leaves
	 */
	private static AudioBuffer fake( String text ) {
		int			rate	= 22050;
		int			frames	= ( int ) Math.round( text.length() * 0.05 * rate );
		int			pad		= rate / 10;
		float[][]	data	= new float[ 1 ][ frames + 2 * pad ];
		for ( int i = 0; i < frames; i++ ) {
			data[ 0 ][ pad + i ] = ( float ) ( 0.3 * Math.sin( 2 * Math.PI * 220 * i / rate ) + 0.05 );
		}
		return new AudioBuffer( data, rate );
	}

	@DisplayName( "text is split into sentences without breaking at abbreviations, initials, decimals or ellipses inside quotes" )
	@Test
	void testSentences() {
		assertThat( TextSplitter.sentences( "It began. Then it grew! Did it end? Yes." ) )
		    .containsExactly( "It began.", "Then it grew!", "Did it end?", "Yes." ).inOrder();
		assertThat( TextSplitter.sentences( "Dr. Smith met Mr. Jones at 5 p.m. on Main St. today. They talked." ) )
		    .containsExactly( "Dr. Smith met Mr. Jones at 5 p.m. on Main St. today.", "They talked." ).inOrder();
		assertThat( TextSplitter.sentences( "J. R. Tolkien wrote it. Pi is 3.14 roughly." ) )
		    .containsExactly( "J. R. Tolkien wrote it.", "Pi is 3.14 roughly." ).inOrder();
		assertThat( TextSplitter.sentences( "She said \"stop.\" Then she left." ) ).containsExactly( "She said \"stop.\"", "Then she left." ).inOrder();
		assertThat( TextSplitter.sentences( "no capital here. so one sentence" ) ).hasSize( 1 );
	}

	@DisplayName( "pieces carry what ends them, with clauses and paragraphs" )
	@Test
	void testPieces() {
		List<TextSplitter.Piece> pieces = TextSplitter.pieces( "First, the setup. Then the twist.\n\nThe end.", false );
		assertThat( pieces )
		    .containsExactly( new TextSplitter.Piece( "First, the setup.", "sentence" ), new TextSplitter.Piece( "Then the twist.", "paragraph" ),
		        new TextSplitter.Piece( "The end.", "none" ) )
		    .inOrder();
		List<TextSplitter.Piece> clauses = TextSplitter.pieces( "When the night comes, we will leave; do not wait for me.", true );
		assertThat( clauses.stream().map( TextSplitter.Piece::text ).toList() )
		    .containsExactly( "When the night comes,", "we will leave;", "do not wait for me." ).inOrder();
		assertThat( clauses.get( 0 ).after() ).isEqualTo( "clause" );
		assertThat( clauses.get( 2 ).after() ).isEqualTo( "none" );
		// A short fragment joins the clause after it, and a number with a comma is not a clause
		assertThat( TextSplitter.pieces( "Well, that was fun and 1,000 people agreed.", true ).stream().map( TextSplitter.Piece::text ).toList() )
		    .containsExactly( "Well, that was fun and 1,000 people agreed." );
		assertThat( TextSplitter.pieces( "  \n ", false ) ).isEmpty();
	}

	@DisplayName( "wrapping keeps lines within the limit and a long word whole" )
	@Test
	void testWrap() {
		assertThat( TextSplitter.wrap( "one two three four five six", 10 ) ).containsExactly( "one two", "three four", "five six" ).inOrder();
		assertThat( TextSplitter.wrap( "extraordinarily short", 5 ) ).containsExactly( "extraordinarily", "short" ).inOrder();
	}

	@DisplayName( "narration puts the chosen pauses between pieces and times each caption from the pieces' lengths" )
	@Test
	void testNarrate() {
		Narration.Pauses	pauses	= Narration.Pauses.from( Map.of( "sentence", 0.5, "paragraph", 1.0 ) );
		Narration.Result	result	= Narration.narrate( "Hello there. Second one.\n\nThird.", NarrationTest::fake, pauses, false, 80 );
		assertThat( result.audio().getDurationSeconds() ).isWithin( 0.05 ).of( 2.95 );
		List<Cue> cues = result.cues();
		assertThat( cues ).hasSize( 3 );
		assertThat( cues.get( 0 ).start() ).isWithin( 0.05 ).of( 0.0 );
		assertThat( cues.get( 0 ).end() ).isWithin( 0.05 ).of( 0.6 );
		assertThat( cues.get( 1 ).start() ).isWithin( 0.05 ).of( 1.1 );
		assertThat( cues.get( 2 ).start() ).isWithin( 0.05 ).of( 2.65 );
		assertThat( cues.get( 2 ).text() ).isEqualTo( "Third." );
	}

	@DisplayName( "a long sentence becomes several caption lines that share its time by characters and leave no gap" )
	@Test
	void testLongSentence() {
		Narration.Result	result	= Narration.narrate( "alpha beta gamma delta epsilon zeta", NarrationTest::fake, Narration.Pauses.DEFAULT, false, 12 );
		List<Cue>			cues	= result.cues();
		assertThat( cues.size() ).isAtLeast( 3 );
		assertThat( cues.get( 0 ).start() ).isWithin( 1e-9 ).of( 0.0 );
		for ( int i = 1; i < cues.size(); i++ ) {
			assertThat( cues.get( i ).start() ).isWithin( 1e-9 ).of( cues.get( i - 1 ).end() );
		}
		assertThat( cues.get( cues.size() - 1 ).end() ).isWithin( 1e-6 ).of( result.audio().getDurationSeconds() );
		assertThat( cues.stream().allMatch( c -> c.text().length() <= 12 ) ).isTrue();
	}

	@DisplayName( "clause mode adds the shorter clause pause inside a sentence" )
	@Test
	void testClausePauses() {
		Narration.Pauses	pauses	= new Narration.Pauses( 0.5, 0.2, 1.0 );
		double				plain	= Narration.narrate( "When the night comes, we will leave.", NarrationTest::fake, pauses, false, 80 ).audio()
		    .getDurationSeconds();
		double				clauses	= Narration.narrate( "When the night comes, we will leave.", NarrationTest::fake, pauses, true, 80 ).audio()
		    .getDurationSeconds();
		// The clause pause, less the 0.05 s of the space between the two clauses that the fake voice no longer speaks
		assertThat( clauses - plain ).isWithin( 0.03 ).of( 0.15 );
	}

	@DisplayName( "empty text and silly pauses are refused" )
	@Test
	void testErrors() {
		assertThat(
		    assertThrows( BoxRuntimeException.class, () -> Narration.narrate( "  ", NarrationTest::fake, Narration.Pauses.DEFAULT, false, 80 ) ).getMessage() )
		    .contains( "nothing to say" );
		assertThat(
		    assertThrows( BoxRuntimeException.class, () -> Narration.narrate( "...", NarrationTest::fake, Narration.Pauses.DEFAULT, false, 80 ) ).getMessage() )
		    .contains( "no words" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> new Narration.Pauses( -1, 0, 0 ) ).getMessage() ).contains( "between 0 and 10" );
		assertThat(
		    assertThrows( BoxRuntimeException.class, () -> Narration.narrate( "Hi.", NarrationTest::fake, Narration.Pauses.DEFAULT, false, 3 ) ).getMessage() )
		    .contains( "maxChars" );
	}

	@DisplayName( "SRT and WebVTT captions are formatted with the offset applied" )
	@Test
	void testSubtitles() {
		List<Cue> cues = List.of( new Cue( 0, 2.5, "Hello" ), new Cue( 3661.25, 3662, "Later" ) );
		assertThat( Subtitles.format( cues, "srt", 0 ) ).isEqualTo( "1\n00:00:00,000 --> 00:00:02,500\nHello\n\n2\n01:01:01,250 --> 01:01:02,000\nLater\n\n" );
		assertThat( Subtitles.format( cues, "vtt", 1.5 ) ).startsWith( "WEBVTT\n\n00:00:01.500 --> 00:00:04.000\nHello" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> Subtitles.format( cues, "ass", 0 ) ).getMessage() ).contains( "Unknown subtitle format" );
	}

}
