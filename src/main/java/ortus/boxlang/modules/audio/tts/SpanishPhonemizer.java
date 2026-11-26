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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns Spanish text into the phonemes Kokoro was trained on, which are espeak-ng's for the language, without espeak-ng.
 * <p>
 * Spanish spelling is close to regular, so this is a set of rules rather than a dictionary: the letters, the stress (the written accent, else
 * the second to last syllable of a word that ends in a vowel, n or s, else the last), diphthongs and hiatus, the soft b, d and g between
 * vowels, n before a labial or a velar, and numbers said in words. The output follows espeak-ng's {@code es} voice (Castilian, with the
 * {@code θ} of {@code z} and {@code ce, ci}) or, with {@code latinAmerican}, its {@code es-419} (seseo, {@code s} for those). The stress mark
 * goes right before the stressed vowel, as espeak-ng writes it. It was checked against espeak-ng on a few hundred words and sentences, see the
 * tests.
 */
public final class SpanishPhonemizer {

	private static final String			VOWELS		= "aeiouáéíóúü";
	private static final String			STRONG		= "aeoáéó";
	private static final Set<String>	UNSTRESSED	= Set.of( "el", "la", "los", "las", "de", "del", "en", "con", "mi", "mis", "su", "sus", "tu", "tus", "que",
	    "lo",
	    "se", "si", "a", "al", "me", "te", "le", "les", "nos", "y", "e", "o", "u", "ni", "por", "sin", "como", "cuando", "pero", "mas", "para", "sobre",
	    "entre", "desde",
	    "mientras", "aunque", "hasta", "porque", "sino", "ante", "según" );
	private static final String[]		UNITS		= { "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once",
	    "doce", "trece",
	    "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro",
	    "veinticinco",
	    "veintiséis", "veintisiete", "veintiocho", "veintinueve" };
	private static final String[]		TENS		= { "", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa" };
	private static final String[]		HUNDREDS	= { "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos", "seiscientos", "setecientos",
	    "ochocientos",
	    "novecientos" };

	private SpanishPhonemizer() {
	}

	/**
	 * Say a text in phonemes
	 *
	 * @param text          Spanish text, with digits, punctuation and foreign words allowed
	 * @param latinAmerican True for the Latin American pronunciation ({@code z} and {@code ce, ci} as {@code s}), false for Castilian
	 *
	 * @return The phonemes: words separated by spaces, {@code , . ; : ! ?} kept where the text had them
	 */
	public static String phonemize( String text, boolean latinAmerican ) {
		String			prepared	= expandNumbers( text );
		StringBuilder	out			= new StringBuilder();
		StringBuilder	phrase		= new StringBuilder();
		for ( int i = 0; i < prepared.length(); i++ ) {
			char c = prepared.charAt( i );
			if ( ",.;:!?…".indexOf( c ) >= 0 ) {
				flush( phrase, out, latinAmerican );
				out.append( c == '…' ? "..." : String.valueOf( c ) ).append( ' ' );
			} else if ( c == '¿' || c == '¡' || c == '(' || c == ')' || c == '"' || c == '“' || c == '”' || c == '«' || c == '»' || c == '—' ) {
				flush( phrase, out, latinAmerican );
			} else {
				phrase.append( c );
			}
		}
		flush( phrase, out, latinAmerican );
		return out.toString().trim().replaceAll( "\\s+([,.;:!?])", "$1" ).replaceAll( "\\s+", " " );
	}

	/**
	 * One stretch of words between punctuation, said as a whole because a sound depends on the one before it, even across a space
	 */
	private static void flush( StringBuilder phrase, StringBuilder out, boolean latinAmerican ) {
		String text = phrase.toString().trim();
		phrase.setLength( 0 );
		if ( text.isEmpty() ) {
			return;
		}
		List<String> words = new ArrayList<>();
		for ( String raw : text.split( "\\s+" ) ) {
			String word = raw.toLowerCase( Locale.ROOT ).replaceAll( "[^a-záéíóúüñ'’-]", "" ).replace( "'", "" ).replace( "’", "" );
			for ( String part : word.split( "-" ) ) {
				if ( !part.isEmpty() ) {
					words.add( part );
				}
			}
		}
		StringBuilder	spoken		= new StringBuilder();
		char			previous	= ' ';
		for ( int w = 0; w < words.size(); w++ ) {
			String	word	= words.get( w );
			String	next	= w + 1 < words.size() ? words.get( w + 1 ) : "";
			String	ipa		= word( word, previous, next, latinAmerican, w == words.size() - 1 );
			if ( ipa.isEmpty() ) {
				continue;
			}
			if ( spoken.length() > 0 ) {
				spoken.append( ' ' );
			}
			spoken.append( ipa );
			previous = lastSound( ipa );
		}
		if ( spoken.length() > 0 ) {
			out.append( spoken ).append( ' ' );
		}
	}

	/**
	 * The last sound that matters to what follows: a vowel, or the consonant
	 */
	private static char lastSound( String ipa ) {
		for ( int i = ipa.length() - 1; i >= 0; i-- ) {
			char c = ipa.charAt( i );
			if ( c != 'ˈ' && c != 'ˌ' ) {
				return c;
			}
		}
		return ' ';
	}

	private static boolean vowel( char c ) {
		return VOWELS.indexOf( c ) >= 0;
	}

	private static boolean strong( char c ) {
		return STRONG.indexOf( c ) >= 0;
	}

	private static boolean accented( char c ) {
		return "áéíóú".indexOf( c ) >= 0;
	}

	private static char plain( char c ) {
		switch ( c ) {
			case 'á' :
				return 'a';
			case 'é' :
				return 'e';
			case 'í' :
				return 'i';
			case 'ó' :
				return 'o';
			case 'ú' :
			case 'ü' :
				return 'u';
			default :
				return c;
		}
	}

	/**
	 * The vowels of a word that carry a syllable, as indexes into the word, and which of them are glides that join the next or the
	 * previous one. A {@code u} after {@code q} or after {@code g} before {@code e, i} is silent and is not a vowel at all.
	 */
	private static List<int[]> nuclei( String w ) {
		List<int[]>	result	= new ArrayList<>();
		int			i		= 0;
		int			n		= w.length();
		while ( i < n ) {
			char c = w.charAt( i );
			if ( !vowel( c ) || silentU( w, i ) || ( c == 'u' && i > 0 && ( w.charAt( i - 1 ) == 'q' || w.charAt( i - 1 ) == 'g' ) && i + 1 < n
			    && "eiéí".indexOf( w.charAt( i + 1 ) ) >= 0 ) ) {
				i++;
				continue;
			}
			int	start	= i;
			int	end		= i;
			while ( end + 1 < n && vowel( w.charAt( end + 1 ) ) ) {
				end++;
			}
			// a final y after a vowel is a glide of the last nucleus of the cluster
			boolean	finalY	= end + 1 == n - 1 && w.charAt( end + 1 ) == 'y';
			// split the cluster into nuclei: strong + strong and an accented weak vowel are hiatus
			int		k		= start;
			while ( k <= end ) {
				int	nucleus	= k;
				int	last	= k;
				if ( strong( w.charAt( k ) ) || accented( w.charAt( k ) ) ) {
					// a strong vowel can take a following weak one (falling) and a preceding weak one was already taken
					int m = k + 1;
					while ( m <= end && ( w.charAt( m ) == 'i' || w.charAt( m ) == 'u' || w.charAt( m ) == 'y' || w.charAt( m ) == 'ü' ) ) {
						m++;
					}
					last = m - 1;
				} else {
					// a weak vowel: it joins a strong one that follows, or a weak one
					if ( k + 1 <= end && ( strong( w.charAt( k + 1 ) ) ) && !accented( w.charAt( k ) ) ) {
						int m = k + 1;
						nucleus	= m;
						last	= m;
						while ( last + 1 <= end && ( w.charAt( last + 1 ) == 'i' || w.charAt( last + 1 ) == 'u' || w.charAt( last + 1 ) == 'y' ) ) {
							last++;
						}
					} else if ( k + 1 <= end && !accented( w.charAt( k ) ) && ( w.charAt( k + 1 ) == 'i' || w.charAt( k + 1 ) == 'u' )
					    && !accented( w.charAt( k + 1 ) ) ) {
						nucleus	= k + 1;
						last	= k + 1;
					} else if ( k + 1 <= end && !accented( w.charAt( k ) ) && accented( w.charAt( k + 1 ) )
					    && ( w.charAt( k + 1 ) == 'í' || w.charAt( k + 1 ) == 'ú' ) ) {
						nucleus	= k + 1;
						last	= k + 1;
					}
				}
				if ( finalY && last + 1 > end ) {
					last++;
				}
				result.add( new int[] { k, last, nucleus } );
				k = last + 1;
			}
			i = finalY ? end + 2 : end + 1;
		}
		return result;
	}

	/**
	 * A {@code u} that is not said: after {@code q}, and after {@code g} before {@code e} or {@code i}
	 */
	private static boolean silentU( String w, int i ) {
		return w.charAt( i ) == 'u' && i > 0 && i + 1 < w.length() && ( w.charAt( i - 1 ) == 'q' || w.charAt( i - 1 ) == 'g' )
		    && "eiéí".indexOf( w.charAt( i + 1 ) ) >= 0;
	}

	/**
	 * One word in phonemes
	 *
	 * @param previous The last sound of the word before it in the same stretch, or a space at the start
	 * @param next     The word after it in the stretch, or ""
	 * @param last     True when nothing follows in the stretch
	 */
	private static String word( String w, char previous, String next, boolean latinAmerican, boolean last ) {
		w = Normalizer.normalize( w, Normalizer.Form.NFC );
		if ( w.equals( "y" ) ) {
			return "i";
		}
		if ( w.length() > 7 && w.endsWith( "mente" ) ) {
			String base = word( w.substring( 0, w.length() - 5 ), previous, "m", latinAmerican, false );
			return base + "mˈente";
		}
		List<int[]>		nuclei		= nuclei( w );
		int				stressed	= stressedNucleus( w, nuclei );
		boolean			stressMark	= !UNSTRESSED.contains( w );
		StringBuilder	out			= new StringBuilder();
		char			before		= previous;
		int				n			= w.length();
		// a final n becomes m before a stressed word that starts with b, v, p or f: un vaso, con permiso, examen fue
		boolean			nasalToM	= !next.isEmpty() && "bvpf".indexOf( next.charAt( 0 ) ) >= 0 && !UNSTRESSED.contains( next );
		for ( int i = 0; i < n; i++ ) {
			char	c				= w.charAt( i );
			char	after			= i + 1 < n ? w.charAt( i + 1 ) : ( next.isEmpty() ? ' ' : next.charAt( 0 ) );
			String	piece			= null;
			int		nucleusIndex	= nucleusAt( nuclei, i );
			if ( vowel( c ) || ( c == 'y' && ( i == n - 1 && n > 1 && vowel( w.charAt( i - 1 ) ) ) ) ) {
				if ( silentU( w, i ) ) {
					continue;
				}
				piece = vowelSound( w, i, nuclei, nucleusIndex, stressed, stressMark, c );
				if ( piece == null ) {
					continue;
				}
			} else if ( c == 'y' && i == n - 1 && n > 1 ) {
				piece = "i";
			} else {
				piece = consonant( w, i, before, after, latinAmerican, last && i == n - 1, nasalToM );
				if ( piece == null ) {
					continue;
				}
				if ( c == 'r' && i + 1 < n && w.charAt( i + 1 ) == 'r' ) {
					i++;
				} else if ( ( c == 'c' && i + 1 < n && w.charAt( i + 1 ) == 'h' ) || ( c == 'l' && i + 1 < n && w.charAt( i + 1 ) == 'l' )
				    || ( c == 'q' && i + 1 < n && w.charAt( i + 1 ) == 'u' )
				    || ( c == 'g' && i + 1 < n && w.charAt( i + 1 ) == 'u' && i + 2 < n && "eiéí".indexOf( w.charAt( i + 2 ) ) >= 0 ) ) {
					i++;
				} else if ( c == 'c' && i + 1 < n && w.charAt( i + 1 ) == 'c' && i + 2 < n && "eiéí".indexOf( w.charAt( i + 2 ) ) >= 0 ) {
					i++;
					piece = "k" + ( latinAmerican ? "s" : "θ" );
				}
			}
			out.append( piece );
			if ( !piece.isEmpty() ) {
				before = lastSound( out.toString() );
			}
		}
		return out.toString();
	}

	private static int nucleusAt( List<int[]> nuclei, int i ) {
		for ( int k = 0; k < nuclei.size(); k++ ) {
			if ( i >= nuclei.get( k )[ 0 ] && i <= nuclei.get( k )[ 1 ] ) {
				return k;
			}
		}
		return -1;
	}

	/**
	 * Which nucleus takes the stress: the written accent, else by the ending
	 */
	private static int stressedNucleus( String w, List<int[]> nuclei ) {
		if ( nuclei.isEmpty() ) {
			return -1;
		}
		for ( int k = 0; k < nuclei.size(); k++ ) {
			for ( int i = nuclei.get( k )[ 0 ]; i <= nuclei.get( k )[ 1 ]; i++ ) {
				if ( accented( w.charAt( i ) ) && w.charAt( i ) != 'ü' ) {
					return k;
				}
			}
		}
		if ( nuclei.size() == 1 ) {
			return 0;
		}
		char end = w.charAt( w.length() - 1 );
		if ( "aeiouns".indexOf( end ) >= 0 || ( end == 'y' && false ) ) {
			return nuclei.size() - 2;
		}
		return nuclei.size() - 1;
	}

	private static String vowelSound( String w, int i, List<int[]> nuclei, int k, int stressed, boolean stressMark, char c ) {
		if ( c == 'y' ) {
			char prev = plain( w.charAt( i - 1 ) );
			return prev == 'a' || prev == 'e' || prev == 'o' ? "ɪ" : "j";
		}
		int[]	nucleus	= k >= 0 ? nuclei.get( k ) : new int[] { i, i, i };
		boolean	isHead	= nucleus[ 2 ] == i;
		char	p		= plain( c );
		if ( !isHead ) {
			// a glide: before the head (rising) it is j or w, after it (falling) it is ɪ or ʊ
			boolean rising = i < nucleus[ 2 ];
			if ( c == 'ü' ) {
				return "w";
			}
			if ( rising ) {
				return p == 'i' ? "j" : "w";
			}
			return p == 'i' ? "ɪ" : "ʊ";
		}
		String mark = "";
		if ( k == stressed && stressMark ) {
			mark = "ˈ";
		} else if ( secondary( w, k, stressed, stressMark, nuclei.size() ) ) {
			mark = "ˌ";
		}
		String sound = String.valueOf( p );
		if ( p == 'e' && k == stressed && stressMark && nucleus[ 1 ] == nucleus[ 2 ] && openE( w, nucleus[ 1 ] ) ) {
			sound = "ɛ";
		}
		return mark + sound;
	}

	/**
	 * Does a nucleus carry the secondary stress? A word of several syllables with no stress of its own (para, pero, como) has it on the first; in
	 * other words every second syllable from the first does, except the one right before the stressed syllable: batería, actualización, alrededor
	 */
	private static boolean secondary( String w, int k, int stressed, boolean stressMark, int count ) {
		if ( !stressMark ) {
			return count >= 2 && k == 0;
		}
		return stressed >= 2 && k % 2 == 0 && k <= stressed - 2;
	}

	/**
	 * espeak-ng opens a stressed {@code e} before {@code n} and {@code t}, {@code g} or {@code k}: ciento, lengua, gente
	 */
	private static boolean openE( String w, int at ) {
		return at + 2 < w.length() && w.charAt( at + 1 ) == 'n' && !vowel( w.charAt( at + 2 ) );
	}

	private static String consonant( String w, int i, char before, char after, boolean latinAmerican, boolean atEnd, boolean nasalToM ) {
		char	c				= w.charAt( i );
		boolean	afterVowelish	= "aeiouɪʊjwɛ".indexOf( before ) >= 0;
		boolean	softCluster		= before == 'l' || before == 'ɾ' || before == 'r' || before == 's' || before == 'θ' || before == 'x' || before == 'f';
		boolean	startOrNasal	= before == ' ' || before == 'm' || before == 'n' || before == 'ŋ';
		boolean	frontNext		= after == 'e' || after == 'i' || after == 'é' || after == 'í';
		switch ( c ) {
			case 'b' :
			case 'v' :
				return startOrNasal ? "b" : "β";
			case 'c' :
				if ( after == 'h' ) {
					return "tʃ";
				}
				if ( frontNext && latinAmerican && i > 0 && w.charAt( i - 1 ) == 's' ) {
					return "";
				}
				return frontNext ? ( latinAmerican ? "s" : "θ" ) : "k";
			case 'd' :
				if ( i == w.length() - 1 && before != ' ' ) {
					return "d";
				}
				return before == ' ' || before == 'n' || before == 'ŋ' ? "d" : "ð";
			case 'f' :
				return "f";
			case 'g' :
				if ( frontNext && ! ( i + 1 < w.length() && w.charAt( i + 1 ) == 'u' ) ) {
					return "x";
				}
				return before == ' ' || before == 'n' || before == 'ŋ' ? "ɡ" : "ɣ";
			case 'h' :
				return "";
			case 'j' :
				return "x";
			case 'k' :
				return "k";
			case 'l' :
				if ( after == 'l' ) {
					return latinAmerican ? "ʝ" : "ʎ";
				}
				return "l";
			case 'm' :
				return "m";
			case 'n' : {
				boolean within = i + 1 < w.length();
				if ( within && ( after == 'f' || after == 'b' || after == 'v' || after == 'p' ) ) {
					return "m";
				}
				if ( !within && nasalToM ) {
					return "m";
				}
				if ( within && after == 'g' ) {
					return "ŋ";
				}
				return "n";
			}
			case 'ñ' :
				return "ɲ";
			case 'p' :
				return "p";
			case 'q' :
				return "k";
			case 'r' :
				if ( after == 'r' ) {
					return "r";
				}
				return i == 0 || before == 'n' || before == 'l' || before == 's' ? "r" : "ɾ";
			case 's' :
				return "s";
			case 't' :
				return "t";
			case 'w' :
				return "w";
			case 'x' :
				return i == 0 ? "s" : "ks";
			case 'y' :
				return "ʝ";
			case 'z' :
				return latinAmerican ? "s" : "θ";
			default :
				return "";
		}
	}

	// ---- numbers ----

	/**
	 * Digits said in words: whole numbers up to a billion, a decimal comma or point, a percent sign
	 */
	static String expandNumbers( String text ) {
		StringBuilder	out	= new StringBuilder();
		int				i	= 0;
		int				n	= text.length();
		while ( i < n ) {
			char c = text.charAt( i );
			if ( Character.isDigit( c ) ) {
				int j = i;
				while ( j < n && Character.isDigit( text.charAt( j ) ) ) {
					j++;
				}
				String whole = text.substring( i, j );
				// 1.250 and 1,250,000 group thousands: a point or comma followed by exactly three digits more
				while ( j + 3 < n + 0 && ( text.charAt( j ) == '.' || text.charAt( j ) == ',' ) && j + 4 <= n
				    && text.substring( j + 1, j + 4 ).chars().allMatch( Character::isDigit )
				    && ( j + 4 == n || !Character.isDigit( text.charAt( j + 4 ) ) ) && text.charAt( j ) == '.' ) {
					whole	+= text.substring( j + 1, j + 4 );
					j		+= 4;
				}
				String frac = "";
				if ( j + 1 < n && ( text.charAt( j ) == ',' || text.charAt( j ) == '.' ) && Character.isDigit( text.charAt( j + 1 ) ) ) {
					int m = j + 1;
					while ( m < n && Character.isDigit( text.charAt( m ) ) ) {
						m++;
					}
					frac	= text.substring( j + 1, m );
					j		= m;
				}
				out.append( ' ' ).append( words( whole ) );
				if ( !frac.isEmpty() ) {
					out.append( " coma" );
					for ( char d : frac.toCharArray() ) {
						out.append( ' ' ).append( UNITS[ d - '0' ] );
					}
				}
				out.append( ' ' );
				i = j;
			} else if ( c == '%' ) {
				out.append( " por ciento " );
				i++;
			} else {
				out.append( c );
				i++;
			}
		}
		return out.toString();
	}

	/**
	 * A whole number in words, as Spanish says it (cero ... novecientos noventa y nueve millones)
	 */
	static String words( String digits ) {
		if ( digits.length() > 9 ) {
			StringBuilder out = new StringBuilder();
			for ( char d : digits.toCharArray() ) {
				out.append( UNITS[ d - '0' ] ).append( ' ' );
			}
			return out.toString().trim();
		}
		long value = Long.parseLong( digits );
		if ( value < 30 ) {
			return UNITS[ ( int ) value ];
		}
		return below( value );
	}

	private static String below( long v ) {
		if ( v >= 1_000_000 ) {
			long	millions	= v / 1_000_000;
			long	rest		= v % 1_000_000;
			String	head		= millions == 1 ? "un millón" : below( millions ) + " millones";
			return rest == 0 ? head : head + " " + below( rest );
		}
		if ( v >= 1000 ) {
			long	thousands	= v / 1000;
			long	rest		= v % 1000;
			String	head		= thousands == 1 ? "mil" : below( thousands ).replaceAll( "uno$", "un" ).replaceAll( "veintiuno$", "veintiún" ) + " mil";
			return rest == 0 ? head : head + " " + below( rest );
		}
		if ( v == 100 ) {
			return "cien";
		}
		if ( v >= 100 ) {
			long rest = v % 100;
			return HUNDREDS[ ( int ) ( v / 100 ) ] + ( rest == 0 ? "" : " " + below( rest ) );
		}
		if ( v < 30 ) {
			return UNITS[ ( int ) v ];
		}
		long units = v % 10;
		return TENS[ ( int ) ( v / 10 ) ] + ( units == 0 ? "" : " y " + UNITS[ ( int ) units ] );
	}
}
