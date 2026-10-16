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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import ortus.boxlang.modules.audio.analysis.Silence;
import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Speaking a longer text a piece at a time: control over the pauses, and the time each piece starts and ends so captions can follow the voice
 */
public final class Narration {

	/**
	 * A caption: a line of text and when it is spoken
	 *
	 * @param start Seconds
	 * @param end   Seconds
	 * @param text  What is said
	 */
	public record Cue( double start, double end, String text ) {
	}

	/**
	 * @param audio The narration, mono
	 * @param cues  When each line is spoken
	 */
	public record Result( AudioBuffer audio, List<Cue> cues ) {
	}

	/**
	 * Pauses in seconds
	 *
	 * @param sentence  After a sentence
	 * @param clause    After a comma, semicolon, colon or dash
	 * @param paragraph After a paragraph
	 */
	public record Pauses( double sentence, double clause, double paragraph ) {

		public static final Pauses DEFAULT = new Pauses( 0.35, 0.15, 0.8 );

		public Pauses {
			if ( sentence < 0 || clause < 0 || paragraph < 0 || sentence > 10 || clause > 10 || paragraph > 10 ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT, "Pauses must be between 0 and 10 seconds" );
			}
		}

		/**
		 * @param values A map with any of sentence, clause and paragraph; the others keep their default
		 */
		public static Pauses from( Map<String, Object> values ) {
			return new Pauses( number( values, "sentence", DEFAULT.sentence ), number( values, "clause", DEFAULT.clause ),
			    number( values, "paragraph", DEFAULT.paragraph ) );
		}

		private static double number( Map<String, Object> values, String key, double fallback ) {
			Object value = values.get( key );
			return value instanceof Number n ? n.doubleValue() : fallback;
		}

		double after( String kind ) {
			return switch ( kind ) {
				case "sentence" -> this.sentence;
				case "clause" -> this.clause;
				case "paragraph" -> this.paragraph;
				default -> 0;
			};
		}
	}

	private Narration() {
	}

	/**
	 * Speak a text in pieces
	 *
	 * @param text     The text
	 * @param speaker  Turns one piece of text into audio
	 * @param pauses   How long to stay quiet between pieces
	 * @param clauses  True to speak and time each clause on its own, false to work in sentences
	 * @param maxChars Longest caption line in characters; a longer piece is split in lines whose times follow their share of the characters
	 */
	public static Result narrate( String text, Function<String, AudioBuffer> speaker, Pauses pauses, boolean clauses, int maxChars ) {
		if ( text == null || text.isBlank() ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "There is nothing to say: the text is empty" );
		}
		if ( maxChars < 10 || maxChars > 400 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "maxChars must be between 10 and 400 but was " + maxChars );
		}
		List<AudioBuffer>	parts	= new ArrayList<>();
		List<Cue>			cues	= new ArrayList<>();
		double				clock	= 0;
		int					rate	= 0;
		for ( TextSplitter.Piece piece : TextSplitter.pieces( text, clauses ) ) {
			// A voice pads each utterance with some silence. Cut it, so the pauses are the ones asked for and the captions start and end with the words.
			AudioBuffer spoken = Silence.trim( speaker.apply( piece.text() ), -50, 0 );
			if ( spoken.getFrames() == 0 ) {
				continue;
			}
			rate = spoken.getSampleRate();
			double length = spoken.getDurationSeconds();
			cues.addAll( lines( piece.text(), clock, clock + length, maxChars ) );
			parts.add( spoken );
			clock += length;
			double gap = pauses.after( piece.after() );
			if ( gap > 0 ) {
				parts.add( AudioBuffer.silenceSeconds( gap, spoken.getChannels(), rate ) );
				clock += parts.get( parts.size() - 1 ).getDurationSeconds();
			}
		}
		if ( parts.isEmpty() ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "There is nothing to say: the text has no words" );
		}
		return new Result( Dsp.concat( parts ), cues );
	}

	/**
	 * Caption lines for a piece spoken between two times, each given the share of the time its characters make of the piece
	 */
	static List<Cue> lines( String text, double start, double end, int maxChars ) {
		List<String>	lines	= TextSplitter.wrap( text, maxChars );
		List<Cue>		cues	= new ArrayList<>();
		double			total	= lines.stream().mapToInt( String::length ).sum();
		double			at		= start;
		for ( String line : lines ) {
			double to = at + ( end - start ) * line.length() / total;
			cues.add( new Cue( at, to, line ) );
			at = to;
		}
		return cues;
	}

}
