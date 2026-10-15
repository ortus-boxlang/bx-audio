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
package ortus.boxlang.modules.audio.util;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.Key;

/**
 * The settings of the bxaudio module, as configured in boxlang.json or the module descriptor
 */
public final class ModuleSettings {

	private static final Key MODULE = Key.of( "bxaudio" );

	private ModuleSettings() {
	}

	/**
	 * @return The value of a setting, or null when it is not set or the module is not running as a module
	 */
	public static Object get( String name ) {
		try {
			ModuleRecord record = BoxRuntime.getInstance().getModuleService().getModuleRecord( MODULE );
			return record == null || record.settings == null ? null : record.settings.get( Key.of( name ) );
		} catch ( RuntimeException e ) {
			return null;
		}
	}

	/**
	 * @return A text setting, empty when it is not set
	 */
	public static String text( String name ) {
		Object value = get( name );
		return value instanceof String s ? s.trim() : "";
	}

}
