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
 * What a voice's license means for someone who sells a service built on it. The class is written into {@code voices.json} by
 * {@code tools/update-voices.bxs}, which also explains each one.
 */
public final class Usage {

	/** CC0, public domain, Unlicense, Apache 2.0: nothing to publish */
	public static final String			PERMISSIVE		= "permissive";
	/** CC BY: credit the creator */
	public static final String			ATTRIBUTION		= "attribution";
	/** CC BY-SA: credit the creator, and adapted material you share carries the same terms */
	public static final String			SHAREALIKE		= "sharealike";
	/** BY-NC and BY-NC-SA: not for a product you sell */
	public static final String			NONCOMMERCIAL	= "noncommercial";
	/** GPL and AGPL: whether model weights count as the program is untested */
	public static final String			COPYLEFT		= "copyleft";
	/** A license written for one dataset */
	public static final String			CUSTOM			= "custom";
	/** The card says nothing, or only "See URL", or a form that is not classified */
	public static final String			UNCLEAR			= "unclear";

	/** Every class, the commercial ones first */
	public static final List<String>	ALL				= List.of( PERMISSIVE, ATTRIBUTION, SHAREALIKE, NONCOMMERCIAL, COPYLEFT, CUSTOM, UNCLEAR );

	private Usage() {
	}

	/**
	 * @return true for the classes whose license allows a product you sell
	 */
	public static boolean commercial( String usage ) {
		return PERMISSIVE.equals( usage ) || ATTRIBUTION.equals( usage ) || SHAREALIKE.equals( usage );
	}

}
