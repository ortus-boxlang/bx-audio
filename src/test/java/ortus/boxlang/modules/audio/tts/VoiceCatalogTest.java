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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class VoiceCatalogTest {

	/**
	 * The only licenses the catalog may contain: open ones. Anything else, such as non-commercial terms, must stay out.
	 */
	private static final Set<String>	OPEN	= Set.of( "CC0-1.0", "Public-Domain", "CC-BY-4.0", "CC-BY-3.0", "CC-BY-SA-4.0", "Apache-2.0", "Unlicense" );

	/**
	 * Voices offered although their license is non-commercial or unclear. They carry a warning, and the commercial usage filters leave them out.
	 */
	private static final Set<String>	WARNED	= Set.of( "en_GB-alan-low", "en_GB-alan-medium", "en_GB-jenny_dioco-medium", "en_GB-semaine-medium" );

	/**
	 * Every voice has an open license, or a warning, and complete, plausible file information
	 */
	@DisplayName( "Every voice has an open license, or a warning, and complete, plausible file information" )
	@Test
	void testEveryVoiceIsOpen() {
		VoiceCatalog	catalog	= VoiceCatalog.load();
		Set<String>		ids		= new HashSet<>();
		assertThat( catalog.all().size() ).isAtLeast( 10 );
		assertThat( catalog.getBaseUrl() ).startsWith( "https://" );
		for ( VoiceInfo voice : catalog.all() ) {
			if ( voice.commercial() ) {
				assertThat( OPEN ).contains( voice.license() );
				assertThat( voice.licenseText().toLowerCase() ).doesNotContain( "-nc" );
				assertThat( voice.licenseText().toLowerCase() ).doesNotContain( "non-commercial" );
				assertThat( voice.warning() ).isEmpty();
			} else {
				// the voices offered with a warning: no open license, and a usage class that says why
				assertThat( WARNED ).contains( voice.id() );
				assertThat( voice.license() ).isEmpty();
				assertThat( voice.warning() ).contains( voice.id() );
			}
			assertThat( voice.shareAlike() ).isEqualTo( voice.license().contains( "-SA-" ) );
			assertThat( ids.add( voice.id() ) ).isTrue();
			assertThat( voice.model().size() ).isGreaterThan( 10_000_000L );
			if ( VoiceInfo.KOKORO.equals( voice.engine() ) ) {
				continue;
			}
			assertThat( voice.id() ).isEqualTo( voice.language() + "-" + voice.name() + "-" + voice.quality() );
			assertThat( voice.model().path() ).endsWith( voice.id() + ".onnx" );
			assertThat( voice.config().path() ).endsWith( voice.id() + ".onnx.json" );
			assertThat( voice.model().digest() ).matches( "[0-9a-f]{32}" );
			assertThat( voice.config().digest() ).matches( "[0-9a-f]{32}" );
		}
	}

	/**
	 * The Kokoro voices share one model and one voices pack, both checked by SHA-256, and are open licensed
	 */
	@DisplayName( "The Kokoro voices share one model and one voices pack, both checked by SHA-256, and are open licensed" )
	@Test
	void testKokoroVoices() {
		VoiceCatalog	catalog	= VoiceCatalog.load();
		List<VoiceInfo>	kokoro	= catalog.all().stream().filter( v -> VoiceInfo.KOKORO.equals( v.engine() ) ).toList();
		assertThat( kokoro ).hasSize( 28 );
		assertThat( kokoro.stream().map( VoiceInfo::language ).distinct().toList() ).containsExactly( "en_US", "en_GB" );
		for ( VoiceInfo voice : kokoro ) {
			assertThat( voice.model() ).isEqualTo( kokoro.get( 0 ).model() );
			assertThat( voice.config() ).isEqualTo( kokoro.get( 0 ).config() );
			assertThat( voice.license() ).isEqualTo( "Apache-2.0" );
			assertThat( voice.commercial() ).isTrue();
			assertThat( voice.id() ).matches( "[ab][fm]_[a-z]+" );
		}
		VoiceInfo heart = catalog.find( "af_heart" );
		assertThat( heart.name() ).isEqualTo( "heart" );
		assertThat( catalog.find( "heart" ) ).isEqualTo( heart );
		assertThat( heart.model().algorithm() ).isEqualTo( "SHA-256" );
		assertThat( heart.model().digest() ).matches( "[0-9a-f]{64}" );
		assertThat( heart.config().digest() ).matches( "[0-9a-f]{64}" );
		assertThat( heart.model().url( catalog.getBaseUrl() ) ).startsWith( "https://github.com/thewh1teagle/kokoro-onnx/releases/download/" );
		// the voices of the other languages are listed as not supported yet, not as unknown
		assertThat( catalog.excluded().stream().filter( v -> VoiceInfo.KOKORO.equals( v.engine() ) ).count() ).isEqualTo( 26 );
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "jf_nezumi" ) ).getMessage() ).contains( "Japanese" );
	}

	/**
	 * Voices with restrictive or unclear licenses are not offered, except the few that carry a warning
	 */
	@DisplayName( "Voices with restrictive or unclear licenses are not offered, except the few that carry a warning" )
	@Test
	void testRestrictedVoicesAreLeftOut() {
		List<String> ids = VoiceCatalog.load().all().stream().map( VoiceInfo::id ).toList();
		assertThat( ids ).contains( "en_US-joe-medium" );
		assertThat( ids.stream().anyMatch( id -> id.contains( "lessac" ) ) ).isFalse();
		assertThat( ids.stream().anyMatch( id -> id.contains( "-ryan-" ) ) ).isFalse();
		assertThat( ids.stream().anyMatch( id -> id.contains( "-amy-" ) ) ).isFalse();
		assertThat( ids.stream().anyMatch( id -> id.contains( "hfc_" ) ) ).isFalse();
	}

	/**
	 * US and British English voices are offered, the other languages have no phonemizer yet
	 */
	@DisplayName( "US and British English voices are offered, the other languages have no phonemizer yet" )
	@Test
	void testEnglishVoices() {
		assertThat( VoiceCatalog.load().all().stream().map( VoiceInfo::language ).distinct().toList() ).containsExactly( "en_GB", "en_US" );
		assertThat( VoiceCatalog.load().find( "en_GB-alba-medium" ).language() ).isEqualTo( "en_GB" );
	}

	/**
	 * A voice is found by its id or by its short name, and a name that fits several voices is refused
	 */
	@DisplayName( "A voice is found by its id or by its short name, and a name that fits several voices is refused" )
	@Test
	void testFind() {
		VoiceCatalog catalog = VoiceCatalog.load();
		assertThat( catalog.find( "en_US-joe-medium" ).name() ).isEqualTo( "joe" );
		assertThat( catalog.find( "JOE" ).id() ).isEqualTo( "en_US-joe-medium" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "ljspeech" ) ).getMessage() ).contains( "en_US-ljspeech-high" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "nonexistent" ) ).getMessage() ).contains( "Unknown voice [nonexistent]" );
	}

	/**
	 * An engine prefix looks only at the voices of that engine, and an unknown engine is refused
	 */
	@DisplayName( "An engine prefix looks only at the voices of that engine, and an unknown engine is refused" )
	@Test
	void testFindWithEngine() {
		VoiceCatalog catalog = VoiceCatalog.load();
		assertThat( catalog.find( "kokoro:heart" ).id() ).isEqualTo( "af_heart" );
		assertThat( catalog.find( "KOKORO:af_heart" ).id() ).isEqualTo( "af_heart" );
		assertThat( catalog.find( "piper:joe" ).id() ).isEqualTo( "en_US-joe-medium" );
		assertThat( catalog.find( "piper:en_US-joe-medium" ).engine() ).isEqualTo( VoiceInfo.PIPER );
		// a name of another engine is not found through the wrong prefix
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "piper:heart" ) ).getMessage() ).contains( "Unknown voice" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "kokoro:joe" ) ).getMessage() ).contains( "Unknown voice" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "festival:joe" ) ).getMessage() ).contains( "Unknown engine [festival]" );
		// a name that fits several voices says which engine and id to use
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "piper:ljspeech" ) ).getMessage() )
		    .contains( "piper:en_US-ljspeech-high" );
		// a voice that is not offered is still explained through its engine
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "kokoro:jf_nezumi" ) ).getMessage() ).contains( "Japanese" );
	}

	/**
	 * A voice that is not offered is refused with the reason, not as unknown
	 */
	@DisplayName( "A voice that is not offered is refused with the reason, not as unknown" )
	@Test
	void testFindExplainsExcluded() {
		VoiceCatalog catalog = VoiceCatalog.load();
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "en_US-ryan-high" ) ).getMessage() ).contains( "Non-commercial" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "lessac" ) ).getMessage() ).contains( "not offered" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "en_US-hfc_female-medium" ) ).getMessage() ).contains( "not offered" );
	}

	/**
	 * Every voice of the collection is either offered or excluded with a reason, and none is both
	 */
	@DisplayName( "Every voice of the collection is either offered or excluded with a reason, and none is both" )
	@Test
	void testExcludedList() {
		VoiceCatalog catalog = VoiceCatalog.load();
		assertThat( catalog.excluded().size() ).isGreaterThan( 100 );
		assertThat( catalog.excluded().stream().allMatch( v -> !v.reason().isBlank() && !v.reasonKind().isBlank() ) ).isTrue();
		assertThat( catalog.excluded().stream().map( VoiceCatalog.ExcludedVoice::reasonKind ).distinct().toList() )
		    .containsAtLeast( "license", "speakers", "language" );
		var offered = catalog.all().stream().map( VoiceInfo::id ).toList();
		assertThat( catalog.excluded().stream().anyMatch( v -> offered.contains( v.id() ) ) ).isFalse();
	}

	/**
	 * Every voice, offered or not, has a usage class that agrees with its license
	 */
	@DisplayName( "Every voice, offered or not, has a usage class that agrees with its license" )
	@Test
	void testUsageClasses() {
		VoiceCatalog catalog = VoiceCatalog.load();
		for ( VoiceInfo voice : catalog.all() ) {
			assertThat( Usage.ALL ).contains( voice.usage() );
			assertThat( voice.commercial() ).isEqualTo( !WARNED.contains( voice.id() ) );
			if ( WARNED.contains( voice.id() ) ) {
				assertThat( List.of( Usage.NONCOMMERCIAL, Usage.UNCLEAR ) ).contains( voice.usage() );
				continue;
			}
			String expected = switch ( voice.license() ) {
				case "CC0-1.0", "Public-Domain", "Unlicense", "Apache-2.0" -> Usage.PERMISSIVE;
				case "CC-BY-4.0", "CC-BY-3.0" -> Usage.ATTRIBUTION;
				default -> Usage.SHAREALIKE;
			};
			assertThat( voice.usage() ).isEqualTo( expected );
		}
		Set<String> seen = new HashSet<>();
		for ( VoiceCatalog.ExcludedVoice voice : catalog.excluded() ) {
			assertThat( Usage.ALL ).contains( voice.usage() );
			seen.add( voice.usage() );
			String text = voice.licenseText().toLowerCase();
			if ( voice.reasonKind().equals( "license" ) ) {
				// a voice left out for its license is never in a commercial class
				assertThat( Usage.commercial( voice.usage() ) ).isFalse();
			}
			if ( voice.usage().equals( Usage.NONCOMMERCIAL ) ) {
				assertThat( text.contains( "nc" ) || text.contains( "non-commercial" ) || text.contains( "noncommercial" ) ).isTrue();
			}
			if ( voice.usage().equals( Usage.COPYLEFT ) ) {
				assertThat( text ).containsMatch( "gpl" );
			}
		}
		// the collection has voices of each kind that cannot be offered, and the catalog says which
		assertThat( seen ).containsAtLeast( Usage.NONCOMMERCIAL, Usage.COPYLEFT, Usage.CUSTOM, Usage.UNCLEAR, Usage.ATTRIBUTION, Usage.SHAREALIKE );
		assertThat( catalog.excluded().stream().filter( v -> v.usage().equals( Usage.COPYLEFT ) ).map( VoiceCatalog.ExcludedVoice::id ).toList() )
		    .containsExactly( "fr_FR-tom-medium", "hy_AM-gor-medium" );
	}

}
