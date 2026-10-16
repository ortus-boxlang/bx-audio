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

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Numbers from the values a BoxLang struct holds. Data that came from JSON or a form often has its numbers as text, such as "12.5".
 */
public final class Numbers {

	private Numbers() {
	}

	/**
	 * @param value A number, or text that holds one
	 * @param what  What the value is, for the message, such as "the time of a marker"
	 *
	 * @return The value as a double
	 *
	 * @throws BoxRuntimeException when it is not a finite number
	 */
	public static double toDouble( Object value, String what ) {
		double result;
		if ( value instanceof Number n ) {
			result = n.doubleValue();
		} else if ( value instanceof CharSequence text ) {
			try {
				result = Double.parseDouble( text.toString().trim() );
			} catch ( NumberFormatException e ) {
				throw new BoxRuntimeException( "Expected a number for " + what + " but got [" + text + "]" );
			}
		} else {
			throw new BoxRuntimeException( "Expected a number for " + what + " but got " + ( value == null ? "nothing" : value.getClass().getSimpleName() ) );
		}
		if ( Double.isNaN( result ) || Double.isInfinite( result ) ) {
			throw new BoxRuntimeException( "Expected a finite number for " + what + " but got " + result );
		}
		return result;
	}

}
