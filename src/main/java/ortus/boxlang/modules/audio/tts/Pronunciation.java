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
	 * Names written as one word that are really two: ColdBox, CommandBox, TestBox, MiniServer, and the Bx products such as BxAudio and BxLens
	 */
	private static final Pattern				BOX_NAMES	= Pattern
	    .compile( "\\b(Cold|Command|Test|Wire|Log|Cache|Forge|Mock|Mega|Stache|Time|Rule|Socket)Box\\b" );
	private static final Pattern				BX_NAMES	= Pattern.compile( "\\b[Bb][Xx]([A-Z][a-z]+)\\b" );

	static {
		BUILT_IN.put( "BoxLang+", "Box Lang Plus" );
		BUILT_IN.put( "BoxLang", "Box Lang" );
		BUILT_IN.put( "bx-audio", "B X Audio" );
		BUILT_IN.put( "BxAudio", "B X Audio" );
		BUILT_IN.put( "bxSites", "B X Sites" );
		BUILT_IN.put( "MiniServer", "Mini Server" );
		BUILT_IN.put( "Ortus", "Or tus" );
		BUILT_IN.put( "JVM", "J V M" );
		BUILT_IN.put( "MP3", "M P 3" );
		BUILT_IN.put( "FLAC", "flack" );
		BUILT_IN.put( "FFmpeg", "F F M peg" );
		BUILT_IN.put( "LUFS", "loofs" );
		BUILT_IN.put( "EBU", "E B U" );
		BUILT_IN.put( "CFML", "C F M L" );
		BUILT_IN.put( "WAV", "wave" );
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
		result	= BOX_NAMES.matcher( result ).replaceAll( "$1 Box" );
		result	= BX_NAMES.matcher( result ).replaceAll( "B X $1" );
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
