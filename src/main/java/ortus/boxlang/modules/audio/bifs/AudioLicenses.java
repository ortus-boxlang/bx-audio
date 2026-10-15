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
package ortus.boxlang.modules.audio.bifs;

import ortus.boxlang.modules.audio.tts.Licenses;
import ortus.boxlang.modules.audio.tts.Speech;
import ortus.boxlang.modules.audio.tts.VoiceInfo;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Struct;

@BoxBIF( description = "List the licenses of the offered voices and what each asks of you" )
public class AudioLicenses extends BIF {

	/**
	 * Constructor
	 */
	public AudioLicenses() {
		super();
		declaredArguments = new ortus.boxlang.runtime.types.Argument[] {};
	}

	/**
	 * List the licenses of the offered voices and what each asks of you. A summary for orientation, the voice's model card is the authority.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @return An array of structs with id, name, url, commercial, attribution, shareAlike, summary and voices (how many offered voices use it).
	 */
	public Array _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Array result = new Array();
		for ( Licenses.License license : Licenses.all() ) {
			long count = Speech.catalog().all().stream().map( VoiceInfo::license ).filter( license.id()::equals ).count();
			result.add( Struct.ofNonConcurrent( "id", license.id(), "name", license.name(), "url", license.url(), "commercial", license.commercial(),
			    "attribution", license.attribution(), "shareAlike", license.shareAlike(), "summary", license.summary(), "voices", count ) );
		}
		return result;
	}

}
