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
package ortus.boxlang.modules.audio.tags;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tags of an audio file, as read from it or as an edit to apply to it. All the formats are mapped to the same names.
 *
 * <p>
 * As an edit, a text key that is present replaces what the file has, and a text key with a null value removes it. A key that is absent leaves the
 * file's tag alone. The cover and the chapters work the same way: untouched, replaced or (an empty list, or a cover set to null) removed.
 */
public final class TagSet {

	/**
	 * The text tags every format maps
	 */
	public static final List<String> TEXT_KEYS = List.of( "title", "artist", "album", "albumArtist", "composer", "genre", "year", "track", "disc", "comment" );

	/**
	 * A picture
	 */
	public record Cover( String mimeType, byte[] data ) {
	}

	/**
	 * A chapter. {@code end} is null when it lasts until the next chapter starts, or until the end of the audio.
	 */
	public record Chapter( double start, Double end, String title ) {
	}

	/**
	 * The text tags by name. As an edit, a null value removes the tag.
	 */
	public final Map<String, String>	text	= new LinkedHashMap<>();

	/**
	 * The cover picture. As an edit it is only used when {@link #coverTouched} is true, and null then removes the cover.
	 */
	public Cover						cover;

	public boolean						coverTouched;

	/**
	 * The chapters. As an edit, null leaves them alone and an empty list removes them.
	 */
	public List<Chapter>				chapters;

}
