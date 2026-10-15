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

import java.util.List;

/**
 * What the licenses of the offered voices ask of you. A summary for orientation, the model card of the voice stays the authority.
 */
public final class Licenses {

	private Licenses() {
	}

	/**
	 * @param id          The identifier used in the catalog, for example {@code CC-BY-4.0}
	 * @param name        The full name
	 * @param url         Where the license text is
	 * @param commercial  Whether commercial use is allowed
	 * @param attribution Whether you must credit the author
	 * @param shareAlike  Whether what you make from it must be shared under the same terms
	 * @param summary     One sentence on what it asks
	 */
	public record License( String id, String name, String url, boolean commercial, boolean attribution, boolean shareAlike, String summary ) {
	}

	private static final List<License> ALL = List.of(
	    new License( "CC0-1.0", "Creative Commons Zero 1.0", "https://creativecommons.org/publicdomain/zero/1.0/", true, false, false,
	        "No restrictions. Credit is welcome but not required." ),
	    new License( "Public-Domain", "Public domain", "https://creativecommons.org/publicdomain/mark/1.0/", true, false, false,
	        "No restrictions. The recordings are in the public domain." ),
	    new License( "Unlicense", "The Unlicense", "https://unlicense.org/", true, false, false, "No restrictions." ),
	    new License( "Apache-2.0", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0", true, true, false,
	        "Commercial use is fine. Keep the license notice and say what you changed." ),
	    new License( "CC-BY-4.0", "Creative Commons Attribution 4.0", "https://creativecommons.org/licenses/by/4.0/", true, true, false,
	        "Commercial use is fine. Credit the author and link the license." ),
	    new License( "CC-BY-3.0", "Creative Commons Attribution 3.0", "https://creativecommons.org/licenses/by/3.0/", true, true, false,
	        "Commercial use is fine. Credit the author and link the license." ),
	    new License( "CC-BY-SA-4.0", "Creative Commons Attribution-ShareAlike 4.0", "https://creativecommons.org/licenses/by-sa/4.0/", true, true, true,
	        "Commercial use is fine. Credit the author, and share what you make from it under the same license." ) );

	/**
	 * @return The licenses the catalog can offer
	 */
	public static List<License> all() {
		return ALL;
	}

}
