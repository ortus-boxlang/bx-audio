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

import ortus.boxlang.modules.audio.tts.Speech;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Struct;
import java.util.List;

import ortus.boxlang.modules.audio.tts.Credits;
import ortus.boxlang.modules.audio.tts.Usage;
import ortus.boxlang.modules.audio.tts.VoiceCatalog;
import ortus.boxlang.modules.audio.tts.VoiceInfo;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.modules.audio.util.AudioException;

@BoxBIF( description = "List the voices of the collection with their licenses, whether they are installed, downloadable or not offered, and why" )
public class AudioVoices extends BIF {

	private static final Key	status	= Key.of( "status" );
	private static final Key	license	= Key.of( "license" );
	private static final Key	usage	= Key.of( "usage" );

	/**
	 * Constructor
	 */
	public AudioVoices() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, "string", KeyDictionary.language, "" ),
		    new Argument( false, "boolean", KeyDictionary.installed, false ),
		    new Argument( false, "string", status, "offered" ),
		    new Argument( false, "string", license, "" ),
		    new Argument( false, "string", usage, "" )
		};
	}

	/**
	 * List the voices of the collection, with their licenses and what you can do with each
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.language Only voices of this language, for example en_US or en_GB. Empty for all.
	 *
	 * @argument.installed True to list only the voices that are installed. Same as status="installed".
	 *
	 * @argument.status offered (default: voices you can use), installed, downloadable (offered, not installed yet), excluded (not offered, with
	 *                  the reason) or all.
	 *
	 * @argument.license Only voices with this license identifier, for example CC0-1.0 or Apache-2.0.
	 *
	 * @argument.usage Only voices of these usage classes, as a list: permissive, attribution, sharealike (the commercial ones), noncommercial, copyleft,
	 *                 custom or unclear. For example "permissive,attribution" gives the voices you can use in a product you sell without any condition
	 *                 beyond credit.
	 *
	 * @return An array of structs with id, name, language, quality, license, licenseText, usage (what the license means for a product you sell),
	 *         commercial (true for permissive, attribution and sharealike), credit (the text to publish, empty when the license asks for none),
	 *         shareAlike, source, sizeMB, installed, status (installed, downloadable or excluded), reasonKind and reason (empty unless excluded).
	 */
	public Array _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String			language	= arguments.getAsString( KeyDictionary.language );
		String			wanted		= arguments.getAsBoolean( KeyDictionary.installed ) ? "installed" : arguments.getAsString( status ).trim().toLowerCase();
		String			licenseId	= arguments.getAsString( license );
		List<String>	usages		= usageFilter( arguments.getAsString( usage ) );
		if ( !List.of( "offered", "installed", "downloadable", "excluded", "all" ).contains( wanted ) ) {
			throw new AudioException( AudioException.DOWNLOAD_FAILED,
			    "Unknown status [" + wanted + "]. Use offered, installed, downloadable, excluded or all." );
		}
		var		store	= Speech.store();
		var		catalog	= Speech.catalog();
		Array	result	= new Array();
		for ( VoiceInfo voice : catalog.all() ) {
			boolean	isInstalled	= store.isInstalled( voice );
			String	state		= isInstalled ? "installed" : "downloadable";
			if ( !matches( wanted, state ) || !matchesFilters( voice.language(), voice.license(), language, licenseId )
			    || !matchesUsage( voice.usage(), usages ) ) {
				continue;
			}
			result.add( Struct.ofNonConcurrent( "id", voice.id(), "name", voice.name(), "language", voice.language(), "quality", voice.quality(),
			    "license", voice.license(), "licenseText", voice.licenseText(), "usage", voice.usage(), "commercial", voice.commercial(), "credit",
			    Credits.line( voice ), "shareAlike", voice.shareAlike(), "source", voice.source(),
			    "sizeMB", Speech.megabytes( voice.downloadSize() ), "installed", isInstalled, "status", state, "reasonKind", "", "reason", "",
			    "warning", voice.warning() ) );
		}
		if ( wanted.equals( "excluded" ) || wanted.equals( "all" ) ) {
			for ( VoiceCatalog.ExcludedVoice voice : catalog.excluded() ) {
				if ( !matchesFilters( voice.language(), voice.license(), language, licenseId ) || !matchesUsage( voice.usage(), usages ) ) {
					continue;
				}
				result.add( Struct.ofNonConcurrent( "id", voice.id(), "name", voice.name(), "language", voice.language(), "quality", voice.quality(),
				    "license", voice.license(), "licenseText", voice.licenseText(), "usage", voice.usage(), "commercial", Usage.commercial( voice.usage() ),
				    "credit", "", "shareAlike", voice.license().contains( "-SA-" ), "source",
				    voice.source(), "sizeMB", Speech.megabytes( voice.size() ), "installed", false, "status", "excluded", "reasonKind",
				    voice.reasonKind(), "reason", voice.reason(), "warning", "" ) );
			}
		}
		return result;
	}

	private static boolean matches( String wanted, String state ) {
		return switch ( wanted ) {
			case "offered", "all" -> true;
			case "excluded" -> false;
			default -> wanted.equals( state );
		};
	}

	private static boolean matchesFilters( String voiceLanguage, String voiceLicense, String language, String licenseId ) {
		return ( language.isEmpty() || voiceLanguage.equalsIgnoreCase( language ) )
		    && ( licenseId.isEmpty() || voiceLicense.equalsIgnoreCase( licenseId ) );
	}

	/**
	 * The classes asked for, checked, or none for every class
	 */
	private static List<String> usageFilter( String text ) {
		List<String> classes = new java.util.ArrayList<>();
		for ( String each : text.split( "," ) ) {
			String name = each.trim().toLowerCase();
			if ( name.isEmpty() ) {
				continue;
			}
			if ( !Usage.ALL.contains( name ) ) {
				throw new AudioException( AudioException.INVALID_ARGUMENT,
				    "Unknown usage [" + name + "]. Use one or more of: " + String.join( ", ", Usage.ALL ) );
			}
			classes.add( name );
		}
		return classes;
	}

	private static boolean matchesUsage( String voiceUsage, List<String> usages ) {
		return usages.isEmpty() || usages.contains( voiceUsage );
	}

}
