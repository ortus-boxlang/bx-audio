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

/**
 * One voice in the catalog
 *
 * @param id          The Piper voice key, for example {@code en_US-joe-medium}
 * @param name        The short name, for example {@code joe}
 * @param language    The language and region, for example {@code en_US}
 * @param quality     {@code low}, {@code medium} or {@code high}
 * @param license     The license as an identifier, for example {@code CC0-1.0}
 * @param licenseText The license exactly as the voice's model card states it
 * @param usage       What the license means for a product you sell, see {@link Usage}
 * @param shareAlike  True when the license requires derived works to be shared under the same terms
 * @param source      Where the training data came from, may be empty
 * @param model       The model file
 * @param config      The configuration file
 */
public record VoiceInfo(
    String id,
    String name,
    String language,
    String quality,
    String license,
    String licenseText,
    String usage,
    boolean shareAlike,
    String source,
    VoiceFile model,
    VoiceFile config ) {

	/**
	 * A file of a voice
	 *
	 * @param path The path below the catalog's base URL
	 * @param size The size in bytes
	 * @param md5  The MD5 digest in hex
	 */
	public record VoiceFile( String path, long size, String md5 ) {
	}

	/**
	 * @return true when the license allows a product you sell
	 */
	public boolean commercial() {
		return Usage.commercial( this.usage );
	}

	/**
	 * @return What to check before using the voice in a product you sell, or an empty string when its license allows it
	 */
	public String warning() {
		if ( commercial() ) {
			return "";
		}
		String card = this.licenseText.isBlank() ? "its model card" : "its license [" + this.licenseText + "]";
		return Usage.NONCOMMERCIAL.equals( this.usage )
		    ? "The voice [" + this.id + "] is for non-commercial use only (" + card + "). Do not use it in a product you sell."
		    : "The license of the voice [" + this.id + "] is not clear (" + card + "). Check it before you use the voice in a product you sell.";
	}

	/**
	 * @return The bytes to download for this voice
	 */
	public long downloadSize() {
		return this.model.size() + this.config.size();
	}

}
