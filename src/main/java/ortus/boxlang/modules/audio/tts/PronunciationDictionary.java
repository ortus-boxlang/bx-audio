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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Words the engine's English dictionary does not have, added to it as phonemes. A name said as one word keeps its rhythm (Box Lang, with the
 * stress on Box and no gap), where respelling it as two words leaves a pause between them. The phonemes use the engine's own notation: IPA
 * with a stress mark in front of the stressed vowel.
 *
 * <p>
 * The engine runs in its own class loader, so the dictionary it needs is made as a proxy of its interface.
 */
public final class PronunciationDictionary {

	private static final String[]	BOX_PREFIXES	= { "cold", "command", "test", "wire", "log", "cache", "forge", "mock", "mega", "stache", "time", "rule",
	    "socket" };

	/**
	 * Letters said one by one, as the engine's dictionary says the single letters
	 */
	private static final String		BEE				= "bˈi";
	private static final String		EX				= "ˈɛks";
	private static final String		AUDIO			= "ˈɒdiˌoʊ";

	private PronunciationDictionary() {
	}

	/**
	 * @param loader The class loader the speech engine runs in
	 * @param base   The engine's English dictionary
	 *
	 * @return The dictionary with our words added
	 */
	public static Object extend( ClassLoader loader, Object base ) throws ReflectiveOperationException {
		Class<?>			dictionary	= loader.loadClass( "org.pitest.voices.g2p.core.Dictionary" );
		Class<?>			pos			= loader.loadClass( "org.pitest.voices.g2p.core.pos.Pos" );
		Object				other		= pos.getField( "OTHER" ).get( null );
		Method				get			= dictionary.getMethod( "get", String.class, pos );
		Map<String, String>	entries		= entries( word -> {
											try {
												return ( ( Optional<?> ) get.invoke( base, word, other ) ).map( String::valueOf ).orElse( null );
											} catch ( ReflectiveOperationException e ) {
												return null;
											}
										} );
		Object				additions	= Proxy.newProxyInstance( loader, new Class<?>[] { dictionary }, ( proxy, method, args ) -> {
											switch ( method.getName() ) {
												case "containsWord" :
													return entries.containsKey( String.valueOf( args[ 0 ] ).toLowerCase( Locale.ROOT ) );
												case "words" :
													return entries.keySet();
												case "get" :
													return Optional.ofNullable( entries.get( String.valueOf( args[ 0 ] ).toLowerCase( Locale.ROOT ) ) );
												case "hashCode" :
													return System.identityHashCode( proxy );
												case "equals" :
													return proxy == args[ 0 ];
												case "toString" :
													return "PronunciationDictionary" + entries.keySet();
												default :
													throw new UnsupportedOperationException( method.getName() );
											}
										} );
		return dictionary.getMethod( "withAdditions", dictionary ).invoke( base, additions );
	}

	/**
	 * The phonemes of the added words. {@code lookup} finds a word in the engine's own dictionary, so the names built from ordinary words
	 * (ColdBox is cold and box) keep the voice's own sound for them.
	 */
	static Map<String, String> entries( java.util.function.Function<String, String> lookup ) {
		Map<String, String>	entries	= new LinkedHashMap<>();
		String				box		= lookup.apply( "box" );
		String				lang	= lookup.apply( "lang" );
		if ( box != null && lang != null ) {
			// Box stressed, Lang with a secondary stress, as one word
			entries.put( "boxlang", box + lang.replace( 'ˈ', 'ˌ' ) );
		}
		entries.put( "bx", BEE + EX );
		entries.put( "bxaudio", BEE + EX.replace( 'ˈ', 'ˌ' ) + AUDIO );
		entries.put( "ortus", "ˈɔɹtəs" );
		entries.put( "jvm", "dʒˈeɪvˈiˈɛm" );
		entries.put( "flac", "flˈæk" );
		entries.put( "lufs", "lˈuvs".replace( 'v', 'f' ) );
		entries.put( "wav", "wˈeɪv" );
		if ( box != null ) {
			for ( String prefix : BOX_PREFIXES ) {
				String start = lookup.apply( prefix );
				if ( start != null ) {
					entries.put( prefix + "box", start + box.replace( 'ˈ', 'ˌ' ) );
				}
			}
		}
		return entries;
	}

}
