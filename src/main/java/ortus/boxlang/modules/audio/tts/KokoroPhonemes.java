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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.jr.ob.JSON;

import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Turns the phonemes the Voices library says into the tokens Kokoro was trained on.
 * <p>
 * Kokoro v1.0 was trained on espeak-ng phonemes, and the engine has no espeak-ng. The Voices phonemizer is close but not the same: it puts a
 * stress mark on every function word, writes the schwa as {@code ʌ}, says {@code ɒ} where American English says {@code ɑː}, and leaves out
 * length marks. Fed straight in, the audio was 6.6 percent longer than the reference and sounded more different from it than a 5 percent speed
 * change does; with the rules below it is 1 percent longer and as close as a 5 percent speed change is. The measurements are in
 * the Kokoro section of {@code docs/guides/speech-and-subtitles.md}.
 * <p>
 * The rules are, for American English, a table of function words learned from the reference (see {@code tools/kokoro/derive-words.py}) and a few
 * vowel rules; for British English only the table, whose phonemes already agree with espeak-ng's {@code en-gb} for the most part.
 */
public final class KokoroPhonemes {

	/**
	 * The punctuation the phonemizer keeps, which Kokoro has tokens for
	 */
	private static final String			PUNCTUATION	= ",.;:!?()\"“”—…";
	/**
	 * The stress marks: primary and secondary
	 */
	private static final String			STRESS		= "ˈˌ";
	/**
	 * The vowel symbols the Voices phonemizer writes, which the flap rule looks for before a {@code t}
	 */
	private static final String			VOWELS		= "aeiouɑɐɒæɔəɛɚɜɪʊʌᵻ";

	/**
	 * The table of this JVM, read once
	 */
	private static KokoroPhonemes		instance;

	/**
	 * The token of each phoneme symbol, by code point
	 */
	private final Map<Integer, Integer>	vocab;

	/**
	 * What is known of each language, by name such as {@code en_US}
	 */
	private final Map<String, Language>	languages;

	/**
	 * What is known of a language: its function words, and the espeak-ng spelling of those it spells differently
	 *
	 * @param function The function words, which the table names without stress marks
	 * @param words    The espeak-ng spelling of each word that is spelled differently, by word
	 */
	private record Language( Set<String> function, Map<String, String> words ) {
	}

	/**
	 * Use {@link #get()}
	 *
	 * @param vocab     The token of each phoneme symbol, by code point
	 * @param languages What is known of each language
	 */
	private KokoroPhonemes( Map<Integer, Integer> vocab, Map<String, Language> languages ) {
		this.vocab		= vocab;
		this.languages	= languages;
	}

	/**
	 * @return The table bundled with the module, read once
	 *
	 * @throws AudioException when the table is missing or cannot be read
	 */
	public static synchronized KokoroPhonemes get() {
		if ( instance == null ) {
			try ( InputStream in = KokoroPhonemes.class.getClassLoader().getResourceAsStream( "kokoro-phonemes.json" ) ) {
				if ( in == null ) {
					throw new AudioException( AudioException.INTERNAL, "The Kokoro phoneme table (kokoro-phonemes.json) is missing from the module" );
				}
				instance = parse( in );
			} catch ( IOException e ) {
				throw new AudioException( AudioException.IO, "Could not read the Kokoro phoneme table: " + e.getMessage(), e );
			}
		}
		return instance;
	}

	/**
	 * Read a table from JSON
	 *
	 * @param in The JSON, with a {@code vocab} and an {@code en_US} and {@code en_GB} part
	 *
	 * @return The table
	 *
	 * @throws IOException when the JSON cannot be read
	 */
	@SuppressWarnings( "unchecked" )
	public static KokoroPhonemes parse( InputStream in ) throws IOException {
		Map<String, Object>		root	= JSON.std.mapFrom( in );
		Map<Integer, Integer>	vocab	= new HashMap<>();
		for ( Map.Entry<String, Object> entry : ( ( Map<String, Object> ) root.get( "vocab" ) ).entrySet() ) {
			vocab.put( entry.getKey().codePointAt( 0 ), ( ( Number ) entry.getValue() ).intValue() );
		}
		Map<String, Language> languages = new HashMap<>();
		for ( String name : List.of( "en_US", "en_GB" ) ) {
			Map<String, Object> node = ( Map<String, Object> ) root.get( name );
			languages.put( name, new Language( new HashSet<>( ( List<String> ) node.get( "function" ) ), ( Map<String, String> ) node.get( "words" ) ) );
		}
		return new KokoroPhonemes( Map.copyOf( vocab ), Map.copyOf( languages ) );
	}

	/**
	 * @return The longest run of tokens the model accepts
	 */
	public static int maxTokens() {
		return 510;
	}

	/**
	 * Keep what Kokoro has a token for
	 *
	 * @param phonemes Any phoneme string
	 *
	 * @return The same string without the symbols the model has no token for
	 */
	public String known( String phonemes ) {
		StringBuilder out = new StringBuilder();
		phonemes.codePoints().filter( this.vocab::containsKey ).forEach( out::appendCodePoint );
		return out.toString();
	}

	/**
	 * The tokens of a phoneme string, leaving out what the model has no token for
	 *
	 * @param phonemes Any phoneme string
	 *
	 * @return One token per symbol the model knows, in order
	 */
	public long[] tokens( String phonemes ) {
		return phonemes.codePoints().filter( this.vocab::containsKey ).mapToLong( c -> this.vocab.get( c ) ).toArray();
	}

	/**
	 * What the Voices phonemizer said, in the spelling Kokoro expects
	 *
	 * @param language {@code en_US} or {@code en_GB}
	 * @param phonemes The items the phonemizer returned: phonemes, spaces and punctuation
	 *
	 * @return The phonemes as one string, words separated by single spaces, with only what Kokoro has a token for
	 *
	 * @throws AudioException when the language is not US or British English
	 */
	public String map( String language, List<String> phonemes ) {
		Language known = this.languages.get( language );
		if ( known == null ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT, "Kokoro speaks US and British English here, not [" + language + "]" );
		}
		boolean			american	= language.equals( "en_US" );
		String			text		= known( String.join( "", phonemes ) );
		List<String>	words		= new ArrayList<>();
		for ( String word : text.trim().split( "\\s+" ) ) {
			if ( word.isEmpty() ) {
				continue;
			}
			int	start	= 0;
			int	end		= word.length();
			while ( start < end && PUNCTUATION.indexOf( word.charAt( start ) ) >= 0 ) {
				start++;
			}
			while ( end > start && PUNCTUATION.indexOf( word.charAt( end - 1 ) ) >= 0 ) {
				end--;
			}
			String	core	= word.substring( start, end );
			String	key		= strip( core );
			String	spoken	= known.words().get( key );
			if ( spoken == null ) {
				spoken = american ? vowels( core ) : core;
			}
			words.add( word.substring( 0, start ) + spoken + word.substring( end ) );
		}
		return known( String.join( " ", words ) );
	}

	/**
	 * A word without its stress marks and punctuation, which is how the function word table names it
	 *
	 * @param word A word as the phonemizer says it
	 *
	 * @return The word with only its phonemes
	 */
	private static String strip( String word ) {
		StringBuilder out = new StringBuilder();
		for ( int i = 0; i < word.length(); i++ ) {
			char c = word.charAt( i );
			if ( STRESS.indexOf( c ) < 0 && PUNCTUATION.indexOf( c ) < 0 ) {
				out.append( c );
			}
		}
		return out.toString();
	}

	/**
	 * Whether a stress mark comes right before a symbol
	 *
	 * @param word The word
	 * @param at   The position of the symbol
	 *
	 * @return True when the symbol is the start of a stressed syllable
	 */
	private static boolean stressed( String word, int at ) {
		return at > 0 && STRESS.indexOf( word.charAt( at - 1 ) ) >= 0;
	}

	/**
	 * The symbol that follows another
	 *
	 * @param word The word
	 * @param at   The position of the symbol
	 *
	 * @return The next symbol, or 0 at the end of the word
	 */
	private static char after( String word, int at ) {
		return at + 1 < word.length() ? word.charAt( at + 1 ) : 0;
	}

	/**
	 * The American English vowel rules: where the Voices dictionary says a vowel one way and espeak-ng another
	 *
	 * @param word A word as the phonemizer says it, with its stress marks
	 *
	 * @return The word as espeak-ng says it
	 */
	private static String vowels( String word ) {
		StringBuilder	out	= new StringBuilder();
		int				i	= 0;
		while ( i < word.length() ) {
			char	c		= word.charAt( i );
			char	next	= after( word, i );
			// ɒ is the British vowel of "lot": Americans say ɑː, or ɔː before an r
			if ( c == 'ɒ' ) {
				out.append( next == 'ɹ' ? "ɔː" : "ɑː" );
				i++;
				continue;
			}
			if ( c == 'ɔ' && next == 'ɹ' ) {
				out.append( "ɔː" );
				i++;
				continue;
			}
			// ɜː is stressed only in a stressed syllable, otherwise the r-coloured schwa ɚ
			if ( c == 'ɜ' && next == 'ː' && !stressed( word, i ) ) {
				out.append( 'ɚ' );
				i += 2;
				continue;
			}
			// an unstressed ʌ is a schwa, ɐ at the start of a word
			if ( c == 'ʌ' && !stressed( word, i ) ) {
				out.append( i == 0 ? 'ɐ' : 'ə' );
				i++;
				continue;
			}
			// long vowels the dictionary writes short, in a stressed syllable
			if ( c == 'i' && next != 'ː' && next != 'ɪ' && next != 'ə' && i < word.length() - 1 && stressed( word, i ) ) {
				out.append( "iː" );
				i++;
				continue;
			}
			if ( c == 'u' && next != 'ː' && next != 'ʊ' && stressed( word, i ) ) {
				out.append( "uː" );
				i++;
				continue;
			}
			if ( c == 'ɔ' && next != 'ː' && next != 'ɪ' && next != 'ɹ' && stressed( word, i ) ) {
				out.append( "ɔː" );
				i++;
				continue;
			}
			// the American flap: a t between a vowel (or r) and an unstressed vowel is said as a d
			if ( c == 't' && i > 0 && ( VOWELS.indexOf( word.charAt( i - 1 ) ) >= 0 || word.charAt( i - 1 ) == 'ɹ' ) && "ɪəɚiɐ".indexOf( next ) >= 0
			    && next != 0 && !stressed( word, i + 1 ) ) {
				out.append( 'ɾ' );
				i++;
				continue;
			}
			out.append( c );
			i++;
		}
		return out.toString();
	}

}
