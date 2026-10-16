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

import java.nio.file.Path;

import ortus.boxlang.modules.audio.image.ImageOutput;
import ortus.boxlang.modules.audio.image.Png;
import ortus.boxlang.modules.audio.image.SpectrogramImage;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;
import ortus.boxlang.modules.audio.util.Audios;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.util.FileSystemUtil;

@BoxBIF( description = "Draw the spectrogram of audio as a PNG or SVG picture" )
public class AudioSpectrogramImage extends BIF {

	private static final Key	audio	= Key.of( "audio" );
	private static final Key	width	= Key.of( "width" );
	private static final Key	height	= Key.of( "height" );
	private static final Key	format	= Key.of( "format" );
	private static final Key	minHz	= Key.of( "minHz" );
	private static final Key	maxHz	= Key.of( "maxHz" );
	private static final Key	rangeDb	= Key.of( "rangeDb" );
	private static final Key	scale	= Key.of( "scale" );

	/**
	 * Constructor
	 */
	public AudioSpectrogramImage() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( false, "integer", width, 800 ),
		    new Argument( false, "integer", height, 256 ),
		    new Argument( false, "string", format, "" ),
		    new Argument( false, "string", Key.path, "" ),
		    new Argument( false, "double", minHz, 40.0 ),
		    new Argument( false, "double", maxHz, 16000.0 ),
		    new Argument( false, "double", rangeDb, 80.0 ),
		    new Argument( false, "string", scale, "magma" )
		};
	}

	/**
	 * Draws which frequencies are in a sound and when: time runs across, frequency up (low at the bottom, on a log scale like hearing) and loudness is
	 * the
	 * color. A steady tone is a horizontal line, a drum hit a vertical smear, speech a pattern of bands. The color scale is set by the loudest point of
	 * the
	 * picture, so quiet and loud recordings both fill the range.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.width Pixels across, 8 to 4000. Each column is one slice of time. Defaults to 800.
	 *
	 * @argument.height Pixels up, 8 to 512. Each row is one band of frequencies. Defaults to 256.
	 *
	 * @argument.format png or svg. Left out, it follows the extension of path, and is png when there is none. The SVG holds the PNG, since a spectrogram
	 *                  has
	 *                  a color for every point.
	 *
	 * @argument.path A file to write the picture to. Folders are created and an existing file is replaced.
	 *
	 * @argument.minHz The lowest frequency shown. Defaults to 40.
	 *
	 * @argument.maxHz The highest frequency shown. Defaults to 16000.
	 *
	 * @argument.rangeDb How many dB below the loudest point still show, 10 to 120. Defaults to 80.
	 *
	 * @argument.scale The colors, from quiet to loud: magma, viridis or gray. Defaults to magma.
	 *
	 * @return The absolute path of the file when path is given. Otherwise the PNG as binary data, or the SVG as a string.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		AudioBuffer	buffer	= Audios.require( arguments.get( audio ), "audio" );
		String		path	= arguments.getAsString( Key.path );
		String		kind	= ImageOutput.format( arguments.getAsString( format ), path, "png" );
		int			w		= arguments.getAsInteger( width );
		int			h		= arguments.getAsInteger( height );
		ImageOutput.checkSize( w, h, 4000, 512 );
		double range = arguments.getAsDouble( rangeDb );
		if ( ! ( range >= 10 && range <= 120 ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "rangeDb must be between 10 and 120 but was " + range );
		}
		String chosen = arguments.getAsString( scale ).trim().toLowerCase();
		if ( !chosen.equals( "magma" ) && !chosen.equals( "viridis" ) && !chosen.equals( "gray" ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "The scale must be magma, viridis or gray but was [" + arguments.getAsString( scale ) + "]" );
		}
		byte[]	png	= Png.encode( w, h,
		    SpectrogramImage.pixels( buffer,
		        new SpectrogramImage.Options( w, h, arguments.getAsDouble( minHz ), arguments.getAsDouble( maxHz ), range, chosen ) ) );
		byte[]	data;
		Object	result;
		if ( kind.equals( "svg" ) ) {
			String svg = SpectrogramImage.svg( w, h, png, buffer.getDurationSeconds() );
			data	= ImageOutput.bytes( svg );
			result	= svg;
		} else {
			data	= png;
			result	= png;
		}
		if ( path.isBlank() ) {
			return result;
		}
		Path target = FileSystemUtil.expandPath( context, path ).absolutePath();
		ImageOutput.write( target, data );
		return target.toString();
	}

}
