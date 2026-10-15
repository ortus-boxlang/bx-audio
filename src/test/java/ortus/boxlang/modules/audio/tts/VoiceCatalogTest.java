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
	private static final Set<String> OPEN = Set.of( "CC0-1.0", "Public-Domain", "CC-BY-4.0", "CC-BY-3.0", "CC-BY-SA-4.0", "Apache-2.0", "Unlicense" );

	@DisplayName( "Every voice in the catalog has an open license and complete, plausible file information" )
	@Test
	void testEveryVoiceIsOpen() {
		VoiceCatalog	catalog	= VoiceCatalog.load();
		Set<String>		ids		= new HashSet<>();
		assertThat( catalog.all().size() ).isAtLeast( 10 );
		assertThat( catalog.getBaseUrl() ).startsWith( "https://" );
		for ( VoiceInfo voice : catalog.all() ) {
			assertThat( OPEN ).contains( voice.license() );
			assertThat( voice.licenseText().toLowerCase() ).doesNotContain( "-nc" );
			assertThat( voice.licenseText().toLowerCase() ).doesNotContain( "non-commercial" );
			assertThat( voice.shareAlike() ).isEqualTo( voice.license().contains( "-SA-" ) );
			assertThat( ids.add( voice.id() ) ).isTrue();
			assertThat( voice.id() ).isEqualTo( voice.language() + "-" + voice.name() + "-" + voice.quality() );
			assertThat( voice.model().path() ).endsWith( voice.id() + ".onnx" );
			assertThat( voice.config().path() ).endsWith( voice.id() + ".onnx.json" );
			assertThat( voice.model().size() ).isGreaterThan( 10_000_000L );
			assertThat( voice.model().md5() ).matches( "[0-9a-f]{32}" );
			assertThat( voice.config().md5() ).matches( "[0-9a-f]{32}" );
		}
	}

	@DisplayName( "Voices with restrictive or unclear licenses are not offered" )
	@Test
	void testRestrictedVoicesAreLeftOut() {
		List<String> ids = VoiceCatalog.load().all().stream().map( VoiceInfo::id ).toList();
		assertThat( ids ).contains( "en_US-joe-medium" );
		assertThat( ids.stream().anyMatch( id -> id.contains( "lessac" ) ) ).isFalse();
		assertThat( ids.stream().anyMatch( id -> id.contains( "-ryan-" ) ) ).isFalse();
		assertThat( ids.stream().anyMatch( id -> id.contains( "-amy-" ) ) ).isFalse();
		assertThat( ids.stream().anyMatch( id -> id.contains( "hfc_" ) ) ).isFalse();
	}

	@DisplayName( "Only US English voices are offered, because the British dictionary of the engine is licensed GPL 3" )
	@Test
	void testOnlyUsEnglish() {
		assertThat( VoiceCatalog.load().all().stream().map( VoiceInfo::language ).distinct().toList() ).containsExactly( "en_US" );
	}

	@DisplayName( "A voice is found by its id or by its short name, and a name that fits several voices is refused" )
	@Test
	void testFind() {
		VoiceCatalog catalog = VoiceCatalog.load();
		assertThat( catalog.find( "en_US-joe-medium" ).name() ).isEqualTo( "joe" );
		assertThat( catalog.find( "JOE" ).id() ).isEqualTo( "en_US-joe-medium" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "ljspeech" ) ).getMessage() ).contains( "en_US-ljspeech-high" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "nonexistent" ) ).getMessage() ).contains( "Unknown voice [nonexistent]" );
	}

	@DisplayName( "A voice that is not offered is refused with the reason, not as unknown" )
	@Test
	void testFindExplainsExcluded() {
		VoiceCatalog catalog = VoiceCatalog.load();
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "en_US-ryan-high" ) ).getMessage() ).contains( "Non-commercial" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "lessac" ) ).getMessage() ).contains( "not offered" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> catalog.find( "en_GB-alba-medium" ) ).getMessage() ).contains( "GPL 3" );
	}

	@DisplayName( "Every voice of the collection is either offered or excluded with a reason, and none is both" )
	@Test
	void testExcludedList() {
		VoiceCatalog catalog = VoiceCatalog.load();
		assertThat( catalog.excluded().size() ).isGreaterThan( 100 );
		assertThat( catalog.excluded().stream().allMatch( v -> !v.reason().isBlank() && !v.reasonKind().isBlank() ) ).isTrue();
		assertThat( catalog.excluded().stream().map( VoiceCatalog.ExcludedVoice::reasonKind ).distinct().toList() )
		    .containsAtLeast( "license", "dictionary", "speakers", "language" );
		var offered = catalog.all().stream().map( VoiceInfo::id ).toList();
		assertThat( catalog.excluded().stream().anyMatch( v -> offered.contains( v.id() ) ) ).isFalse();
	}

}
