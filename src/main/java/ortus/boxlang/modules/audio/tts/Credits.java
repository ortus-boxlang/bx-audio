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

import java.util.ArrayList;
import java.util.List;

import ortus.boxlang.modules.audio.tts.Licenses.License;

/**
 * The credit a voice's license asks for, as text to put on an about page
 */
public final class Credits {

	private Credits() {
	}

	/**
	 * @return The license record of a voice, or null when it is not one the catalog describes
	 */
	private static License licenseOf( VoiceInfo voice ) {
		return Licenses.all().stream().filter( l -> l.id().equals( voice.license() ) ).findFirst().orElse( null );
	}

	/**
	 * @return true when the license of the voice asks that the creator is credited
	 */
	public static boolean required( VoiceInfo voice ) {
		License license = licenseOf( voice );
		return license != null && license.attribution();
	}

	/**
	 * The credit line of a voice, empty when its license asks for none
	 */
	public static String line( VoiceInfo voice ) {
		License license = licenseOf( voice );
		if ( license == null || !license.attribution() ) {
			return "";
		}
		// Angle brackets keep the full stop that follows from becoming part of the link when the text is read as Markdown
		String	trained	= voice.source().isBlank() ? "the dataset named on its model card"
		    : voice.source().startsWith( "http" ) ? "<" + voice.source() + ">" : voice.source();
		String	line	= "Voice \"" + voice.name() + "\" (" + voice.language() + "), trained on " + trained + ". Licensed under " + license.name() + " ("
		    + license.url() + ").";
		if ( license.shareAlike() ) {
			line += " Adapted material that you share must carry the same license.";
		}
		return line;
	}

	/**
	 * The credits for a set of voices: a line for each voice that asks for one, and a sentence naming those that do not
	 */
	public static String text( List<VoiceInfo> voices ) {
		List<String>	lines	= new ArrayList<>();
		List<String>	free	= new ArrayList<>();
		for ( VoiceInfo voice : voices ) {
			String line = line( voice );
			if ( line.isEmpty() ) {
				free.add( voice.name() + " (" + voice.license() + ")" );
			} else if ( !lines.contains( "- " + line ) ) {
				lines.add( "- " + line );
			}
		}
		StringBuilder out = new StringBuilder();
		if ( !lines.isEmpty() ) {
			out.append( "Voices\n\n" ).append( String.join( "\n", lines ) ).append( '\n' );
		}
		if ( !free.isEmpty() ) {
			if ( out.length() > 0 ) {
				out.append( '\n' );
			}
			out.append( "No credit is required for " ).append( String.join( ", ", free ) ).append( "." ).append( '\n' );
		}
		return out.toString();
	}

}
