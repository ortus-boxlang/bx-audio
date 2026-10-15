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
import java.util.Set;

/**
 * Cuts text into the pieces that are spoken one at a time: paragraphs, sentences and clauses, and into lines short enough for a caption
 */
public final class TextSplitter {

	private static final Set<String> ABBREVIATIONS = Set.of( "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc", "inc", "ltd", "co", "no", "e.g",
	    "i.e",
	    "approx", "dept", "est", "fig", "mt", "gen", "col", "lt", "sgt", "capt", "rev", "hon", "u.s", "a.m", "p.m" );

	private TextSplitter() {
	}

	/**
	 * A piece of text and how long to stay quiet after it
	 *
	 * @param text  What to say
	 * @param after What ends it: {@code sentence}, {@code clause}, {@code paragraph} or {@code none} for the end of the text
	 */
	public record Piece( String text, String after ) {
	}

	/**
	 * Split into sentences, or into clauses as well
	 *
	 * @param clauses True to split at commas, semicolons, colons and dashes too
	 */
	public static List<Piece> pieces( String text, boolean clauses ) {
		List<Piece>	result		= new ArrayList<>();
		String[]	paragraphs	= text.strip().split( "\\R\\s*\\R+" );
		for ( int p = 0; p < paragraphs.length; p++ ) {
			String paragraph = paragraphs[ p ].replaceAll( "\\s+", " " ).strip();
			if ( paragraph.isEmpty() ) {
				continue;
			}
			List<String> sentences = sentences( paragraph );
			for ( int s = 0; s < sentences.size(); s++ ) {
				boolean	lastOfParagraph	= s == sentences.size() - 1;
				boolean	lastOfText		= lastOfParagraph && p == paragraphs.length - 1;
				String	ending			= lastOfText ? "none" : lastOfParagraph ? "paragraph" : "sentence";
				if ( !clauses ) {
					add( result, sentences.get( s ), ending );
					continue;
				}
				List<String> parts = clauses( sentences.get( s ) );
				for ( int c = 0; c < parts.size(); c++ ) {
					add( result, parts.get( c ), c == parts.size() - 1 ? ending : "clause" );
				}
			}
		}
		return result;
	}

	private static void add( List<Piece> pieces, String text, String after ) {
		// Punctuation alone, such as a dash left over from a split, has nothing to say
		if ( text.chars().anyMatch( Character::isLetterOrDigit ) ) {
			pieces.add( new Piece( text.strip(), after ) );
		} else if ( !pieces.isEmpty() ) {
			Piece last = pieces.remove( pieces.size() - 1 );
			pieces.add( new Piece( last.text(), after ) );
		}
	}

	/**
	 * Sentences of one paragraph: after . ! ? or an ellipsis, with closing quotes or brackets, when a capital letter, digit or opening quote follows
	 * and the word before is not an abbreviation or an initial
	 */
	static List<String> sentences( String paragraph ) {
		List<String>	result	= new ArrayList<>();
		int				start	= 0;
		int				n		= paragraph.length();
		for ( int i = 0; i < n; i++ ) {
			char c = paragraph.charAt( i );
			if ( c != '.' && c != '!' && c != '?' && c != '\u2026' ) {
				continue;
			}
			int end = i + 1;
			while ( end < n && ".!?\u2026".indexOf( paragraph.charAt( end ) ) >= 0 ) {
				end++;
			}
			while ( end < n && "\"')]\u201d\u2019".indexOf( paragraph.charAt( end ) ) >= 0 ) {
				end++;
			}
			if ( end >= n ) {
				break;
			}
			if ( paragraph.charAt( end ) != ' ' ) {
				i = end - 1;
				continue;
			}
			int next = end;
			while ( next < n && paragraph.charAt( next ) == ' ' ) {
				next++;
			}
			if ( next >= n ) {
				break;
			}
			char	following		= paragraph.charAt( next );
			boolean	startsSentence	= Character.isUpperCase( following ) || Character.isDigit( following ) || "\"'(\u201c\u2018[".indexOf( following ) >= 0;
			if ( c == '.' && startsSentence && abbreviation( paragraph, i ) ) {
				startsSentence = false;
			}
			if ( startsSentence ) {
				result.add( paragraph.substring( start, end ).strip() );
				start = next;
			}
			i = end - 1;
		}
		if ( start < n ) {
			result.add( paragraph.substring( start ).strip() );
		}
		return result;
	}

	private static boolean abbreviation( String text, int dot ) {
		int start = dot;
		while ( start > 0 && ( Character.isLetter( text.charAt( start - 1 ) ) || text.charAt( start - 1 ) == '.' ) ) {
			start--;
		}
		String word = text.substring( start, dot ).toLowerCase();
		if ( word.length() == 1 && Character.isLetter( word.charAt( 0 ) ) ) {
			return true;
		}
		return ABBREVIATIONS.contains( word );
	}

	/**
	 * Clauses of a sentence: split after , ; : and at dashes, folding short fragments into the next so a clause is not just "Well,"
	 */
	static List<String> clauses( String sentence ) {
		List<String>	raw		= new ArrayList<>();
		int				start	= 0;
		for ( int i = 0; i < sentence.length() - 1; i++ ) {
			char	c		= sentence.charAt( i );
			boolean	split	= ( c == ',' || c == ';' || c == ':' ) && sentence.charAt( i + 1 ) == ' '
			    && ! ( Character.isDigit( sentence.charAt( Math.max( 0, i - 1 ) ) ) && i + 2 < sentence.length()
			        && Character.isDigit( sentence.charAt( i + 2 ) ) );
			boolean	dash	= ( c == '\u2014' || c == '\u2013' ) || ( c == '-' && i > 0 && sentence.charAt( i - 1 ) == ' ' && sentence.charAt( i + 1 ) == ' ' );
			if ( split ) {
				raw.add( sentence.substring( start, i + 1 ) );
				start = i + 2;
			} else if ( dash ) {
				raw.add( sentence.substring( start, i ) );
				start = i + 1;
			}
		}
		raw.add( sentence.substring( start ) );
		List<String>	result	= new ArrayList<>();
		String			carry	= "";
		for ( String part : raw ) {
			String joined = ( carry.isEmpty() ? "" : carry + " " ) + part.strip();
			if ( joined.length() < 14 && part != raw.get( raw.size() - 1 ) ) {
				carry = joined;
			} else {
				result.add( joined.strip() );
				carry = "";
			}
		}
		if ( !carry.isEmpty() ) {
			result.add( carry );
		}
		return result;
	}

	/**
	 * Wrap text into lines of at most {@code maxChars} characters, at word boundaries; a word longer than that stays whole
	 */
	public static List<String> wrap( String text, int maxChars ) {
		List<String>	lines	= new ArrayList<>();
		StringBuilder	line	= new StringBuilder();
		for ( String word : text.split( " " ) ) {
			if ( word.isEmpty() ) {
				continue;
			}
			if ( line.length() > 0 && line.length() + 1 + word.length() > maxChars ) {
				lines.add( line.toString() );
				line.setLength( 0 );
			}
			if ( line.length() > 0 ) {
				line.append( ' ' );
			}
			line.append( word );
		}
		if ( line.length() > 0 ) {
			lines.add( line.toString() );
		}
		return lines;
	}

}
