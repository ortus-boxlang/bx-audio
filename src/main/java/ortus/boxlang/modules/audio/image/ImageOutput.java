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
package ortus.boxlang.modules.audio.image;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import ortus.boxlang.modules.audio.codec.AudioFiles;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * What the image functions have in common: which format, whether the size is allowed, and where the picture goes
 */
public final class ImageOutput {

	private static final long MAX_PIXELS = 16_000_000L;

	private ImageOutput() {
	}

	/**
	 * The format from the argument, or from the extension of the path, or the default
	 *
	 * @return svg or png
	 */
	public static String format( String format, String path, String fallback ) {
		String	given	= format == null ? "" : format.trim().toLowerCase( Locale.ROOT );
		String	ext		= "";
		if ( path != null && !path.isBlank() ) {
			String name = path.toLowerCase( Locale.ROOT );
			ext = name.endsWith( ".svg" ) ? "svg" : name.endsWith( ".png" ) ? "png" : "";
		}
		if ( !given.isEmpty() && !given.equals( "svg" ) && !given.equals( "png" ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The format must be svg or png but was [" + format + "]" );
		}
		if ( !given.isEmpty() && !ext.isEmpty() && !given.equals( ext ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The format is " + given + " but the path ends in ." + ext );
		}
		return !given.isEmpty() ? given : !ext.isEmpty() ? ext : fallback;
	}

	public static void checkSize( int width, int height, int maxWidth, int maxHeight ) {
		if ( width < 8 || width > maxWidth ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The width must be between 8 and " + maxWidth + " pixels but was " + width );
		}
		if ( height < 8 || height > maxHeight ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The height must be between 8 and " + maxHeight + " pixels but was " + height );
		}
		if ( ( long ) width * height > MAX_PIXELS ) {
			throw new AudioException( AudioException.TOO_LARGE,
			    "A picture of " + width + " x " + height + " pixels is more than the " + MAX_PIXELS + " allowed" );
		}
	}

	/**
	 * Write the picture to a file, replacing it in one step
	 */
	public static void write( Path path, byte[] data ) {
		try {
			Path parent = path.getParent();
			if ( parent != null ) {
				Files.createDirectories( parent );
			}
		} catch ( IOException e ) {
			throw new AudioException( AudioException.IO, "Could not create the folder of [" + path + "]: " + e.getMessage(), e );
		}
		AudioFiles.atomically( path, temp -> {
			try {
				Files.write( temp, data );
			} catch ( IOException e ) {
				throw new AudioException( AudioException.IO, "Could not write [" + path + "]: " + e.getMessage(), e );
			}
		} );
	}

	public static byte[] bytes( String svg ) {
		return svg.getBytes( StandardCharsets.UTF_8 );
	}

}
