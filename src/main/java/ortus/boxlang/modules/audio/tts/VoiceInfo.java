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
 * @param config      The configuration file, or for a Kokoro voice the voices pack
 * @param engine      Which engine speaks it: {@link #PIPER} or {@link #KOKORO}
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
    VoiceFile config,
    String engine ) {

	/**
	 * The Piper voices, spoken by {@link PiperEngine}
	 */
	public static final String PIPER = "piper";
	/**
	 * The Kokoro voices, spoken by {@link KokoroEngine}
	 */
	public static final String KOKORO = "kokoro";

	/**
	 * A Piper voice
	 *
	 * @param id          The voice key
	 * @param name        The short name
	 * @param language    The language and region
	 * @param quality     {@code low}, {@code medium} or {@code high}
	 * @param license     The license as an identifier
	 * @param licenseText The license as the model card states it
	 * @param usage       What the license means for a product you sell, see {@link Usage}
	 * @param shareAlike  Whether the license asks that adapted material carries the same terms
	 * @param source      Where the training data came from, may be empty
	 * @param model       The model file
	 * @param config      The configuration file
	 */
	public VoiceInfo( String id, String name, String language, String quality, String license, String licenseText, String usage, boolean shareAlike,
	    String source, VoiceFile model, VoiceFile config ) {
		this( id, name, language, quality, license, licenseText, usage, shareAlike, source, model, config, PIPER );
	}

	/**
	 * A file of a voice
	 *
	 * @param path      The path below the catalog's base URL, or a whole URL
	 * @param size      The size in bytes
	 * @param digest    The digest in hex
	 * @param algorithm The digest algorithm, {@code MD5} or {@code SHA-256}
	 */
	public record VoiceFile( String path, long size, String digest, String algorithm ) {

		/**
		 * A file with an MD5 digest, as the Piper catalog has
		 *
		 * @param path The path below the catalog's base URL, or a whole URL
		 * @param size The size in bytes
		 * @param md5  The MD5 digest in hex
		 */
		public VoiceFile( String path, long size, String md5 ) {
			this( path, size, md5, "MD5" );
		}

		/**
		 * The URL to download the file from
		 *
		 * @param baseUrl The URL the catalog's paths are relative to
		 *
		 * @return The whole URL when the path is one, otherwise the path below the base URL
		 */
		public String url( String baseUrl ) {
			return this.path.startsWith( "http://" ) || this.path.startsWith( "https://" ) ? this.path : baseUrl + this.path;
		}
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
