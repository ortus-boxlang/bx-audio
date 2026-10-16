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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How words the voice does not know are said. The speech engine looks words up in an English dictionary and guesses the rest, which turns
 * a product name such as BoxLang into something odd. A word here is replaced by a respelling just before it is spoken, so the captions
 * keep the real text.
 *
 * <p>
 * The built in rules cover the names of the BoxLang family and the terms of audio work. A caller adds their own, and theirs win.
 */
public final class Pronunciation {

	/**
	 * Replacements for whole words, matched without regard to case. The value is what the voice is given.
	 */
	private static final Map<String, String>	BUILT_IN	= new LinkedHashMap<>();

	/**
	 * Products of the Bx family written as one word (BxLens, BxSites), said as the letters B and X and then the name. BxAudio is in the voice's
	 * dictionary as one word, see {@link PronunciationDictionary}.
	 */
	private static final Pattern				BX_NAMES	= Pattern.compile( "\\b[Bb][Xx](?!Audio\\b|audio\\b)([A-Z][a-z]+)\\b" );

	static {
		// BoxLang, BxAudio, ColdBox, Ortus, JVM, FLAC, LUFS and WAV are added to the voice's dictionary as phonemes, see PronunciationDictionary.
		BUILT_IN.put( "BoxLang+", "BoxLang Plus" );
		BUILT_IN.put( "bx-audio", "BxAudio" );
		BUILT_IN.put( "bxSites", "Bee Ex Sites" );
		BUILT_IN.put( "MiniServer", "Mini Server" );
		BUILT_IN.put( "MP3", "M P 3" );
		BUILT_IN.put( "FFmpeg", "F F M peg" );
		BUILT_IN.put( "EBU", "E B U" );
		BUILT_IN.put( "CFML", "C F M L" );
		BUILT_IN.put( "BPM", "B P M" );
		BUILT_IN.put( "API", "A P I" );
		BUILT_IN.put( "CLI", "C L I" );
	}

	private Pronunciation() {
	}

	/**
	 * @return The text with the words respelled for the voice
	 *
	 * @param text   What is about to be spoken
	 * @param custom Caller supplied replacements, word to spoken form, that win over the built in ones. May be null.
	 */
	public static String apply( String text, Map<String, String> custom ) {
		String result = text;
		if ( custom != null ) {
			for ( Map.Entry<String, String> rule : custom.entrySet() ) {
				result = replaceWord( result, rule.getKey(), rule.getValue() );
			}
		}
		result = BX_NAMES.matcher( result ).replaceAll( "Bee Ex $1" );
		for ( Map.Entry<String, String> rule : BUILT_IN.entrySet() ) {
			result = replaceWord( result, rule.getKey(), rule.getValue() );
		}
		return result;
	}

	/**
	 * Replace a whole word: it must not be part of a longer word, so MP3 is found in "an MP3 file" but the rule for BoxLang does not touch
	 * "BoxLanguage".
	 */
	private static String replaceWord( String text, String word, String spoken ) {
		if ( word == null || word.isEmpty() ) {
			return text;
		}
		Pattern pattern = Pattern.compile( "(?<![\\p{L}\\p{N}])" + Pattern.quote( word ) + "(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE );
		return pattern.matcher( text ).replaceAll( Matcher.quoteReplacement( spoken == null ? "" : spoken ) );
	}

}
