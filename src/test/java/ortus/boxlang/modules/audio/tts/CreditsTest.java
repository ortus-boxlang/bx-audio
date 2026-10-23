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

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.tts.VoiceInfo.VoiceFile;

public class CreditsTest {

	private static VoiceInfo voice( String name, String license, String usage, boolean shareAlike, String source ) {
		VoiceFile file = new VoiceFile( "x.onnx", 1, "0" );
		return new VoiceInfo( "xx_XX-" + name + "-medium", name, "xx_XX", "medium", license, license, usage, shareAlike, source, file, file );
	}

	@DisplayName( "A CC BY voice asks for credit: the creator, the license and its link" )
	@Test
	void attribution() {
		VoiceInfo voice = voice( "rosa", "CC-BY-4.0", Usage.ATTRIBUTION, false, "https://example.org/dataset" );
		assertThat( Credits.required( voice ) ).isTrue();
		String line = Credits.line( voice );
		assertThat( line ).contains( "\"rosa\"" );
		assertThat( line ).contains( "xx_XX" );
		assertThat( line ).contains( "trained on <https://example.org/dataset>." );
		assertThat( line ).contains( "Creative Commons Attribution 4.0" );
		assertThat( line ).contains( "https://creativecommons.org/licenses/by/4.0/" );
		assertThat( line ).doesNotContain( "same license" );
	}

	@DisplayName( "A CC BY-SA voice also says adapted material carries the same license, and a missing source is named as the model card's" )
	@Test
	void shareAlike() {
		String line = Credits.line( voice( "ana", "CC-BY-SA-4.0", Usage.SHAREALIKE, true, "" ) );
		assertThat( line ).contains( "Adapted material that you share must carry the same license." );
		assertThat( line ).contains( "the dataset named on its model card" );
	}

	@DisplayName( "Apache 2.0 asks for its notice, CC0 and public domain ask for nothing" )
	@Test
	void permissive() {
		assertThat( Credits.line( voice( "sam", "Apache-2.0", Usage.PERMISSIVE, false, "" ) ) ).contains( "Apache License 2.0" );
		assertThat( Credits.line( voice( "joe", "CC0-1.0", Usage.PERMISSIVE, false, "" ) ) ).isEmpty();
		assertThat( Credits.line( voice( "kristin", "Public-Domain", Usage.PERMISSIVE, false, "" ) ) ).isEmpty();
		assertThat( Credits.required( voice( "joe", "CC0-1.0", Usage.PERMISSIVE, false, "" ) ) ).isFalse();
	}

	@DisplayName( "The text lists each voice that asks once, and names the ones that do not" )
	@Test
	void text() {
		VoiceInfo	rosa		= voice( "rosa", "CC-BY-4.0", Usage.ATTRIBUTION, false, "https://example.org/dataset" );
		VoiceInfo	joe			= voice( "joe", "CC0-1.0", Usage.PERMISSIVE, false, "" );
		VoiceInfo	ljspeech	= voice( "ljspeech", "Public-Domain", Usage.PERMISSIVE, false, "" );
		String		text		= Credits.text( List.of( rosa, joe, rosa, ljspeech ) );
		assertThat( text ).startsWith( "Voices\n\n- Voice \"rosa\"" );
		assertThat( text.split( "Voice \"rosa\"", -1 ).length ).isEqualTo( 2 );
		assertThat( text ).contains( "No credit is required for joe (CC0-1.0), ljspeech (Public-Domain)." );
		assertThat( Credits.text( List.of( joe ) ) ).isEqualTo( "No credit is required for joe (CC0-1.0).\n" );
	}

	@DisplayName( "A voice with a non-commercial or unclear license is warned about, not listed as needing no credit" )
	@Test
	void warnings() {
		VoiceInfo	semaine	= voice( "semaine", "", Usage.NONCOMMERCIAL, false, "" );
		VoiceInfo	alan	= voice( "alan", "", Usage.UNCLEAR, false, "" );
		VoiceInfo	joe		= voice( "joe", "CC0-1.0", Usage.PERMISSIVE, false, "" );
		assertThat( semaine.warning() ).contains( "non-commercial" );
		assertThat( alan.warning() ).contains( "not clear" );
		assertThat( joe.warning() ).isEmpty();
		String text = Credits.text( List.of( joe, semaine, alan ) );
		assertThat( text ).contains( "No credit is required for joe (CC0-1.0)." );
		assertThat( text ).contains( "Warning: " );
		assertThat( text ).doesNotContain( "No credit is required for semaine" );
	}

	@DisplayName( "Only the permissive, attribution and sharealike classes are commercial" )
	@Test
	void commercialClasses() {
		for ( String usage : Usage.ALL ) {
			assertThat( Usage.commercial( usage ) ).isEqualTo( List.of( "permissive", "attribution", "sharealike" ).contains( usage ) );
		}
		assertThat( Usage.commercial( "nonsense" ) ).isFalse();
		assertThat( Usage.commercial( null ) ).isFalse();
	}

}
