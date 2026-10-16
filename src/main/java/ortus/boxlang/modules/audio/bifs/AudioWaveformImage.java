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

import ortus.boxlang.modules.audio.image.Colors;
import ortus.boxlang.modules.audio.image.ImageOutput;
import ortus.boxlang.modules.audio.image.Png;
import ortus.boxlang.modules.audio.image.WaveformImage;
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

@BoxBIF( description = "Draw the waveform of audio as an SVG or PNG picture" )
public class AudioWaveformImage extends BIF {

	private static final Key	audio		= Key.of( "audio" );
	private static final Key	width		= Key.of( "width" );
	private static final Key	height		= Key.of( "height" );
	private static final Key	format		= Key.of( "format" );
	private static final Key	color		= Key.of( "color" );
	private static final Key	background	= Key.of( "background" );
	private static final Key	style		= Key.of( "style" );
	private static final Key	split		= Key.of( "split" );

	/**
	 * Constructor
	 */
	public AudioWaveformImage() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", audio ),
		    new Argument( false, "integer", width, 800 ),
		    new Argument( false, "integer", height, 160 ),
		    new Argument( false, "string", format, "" ),
		    new Argument( false, "string", Key.path, "" ),
		    new Argument( false, "string", color, "#3b82f6" ),
		    new Argument( false, "string", background, "" ),
		    new Argument( false, "string", style, "filled" ),
		    new Argument( false, "boolean", split, false )
		};
	}

	/**
	 * Draws how loud a sound is over time, the way a player or an editor shows it. For each column of the picture the highest and lowest sample are drawn
	 * pale and the average level (RMS) solid, so a loud, steady sound and a sparse, peaky one look different. Silence is a thin line. The picture can go
	 * in
	 * a web page, a report or a podcast page.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.audio The audio buffer.
	 *
	 * @argument.width Pixels across, 8 to 8192. Defaults to 800.
	 *
	 * @argument.height Pixels down, 8 to 4096. Defaults to 160.
	 *
	 * @argument.format svg or png. Left out, it follows the extension of path, and is svg when there is none.
	 *
	 * @argument.path A file to write the picture to. Folders are created and an existing file is replaced.
	 *
	 * @argument.color The color of the waveform as a hex color, #rgb, #rrggbb or #rrggbbaa. Defaults to #3b82f6.
	 *
	 * @argument.background The color behind it as a hex color. Left out it is transparent.
	 *
	 * @argument.style filled for a continuous shape, or bars for separate bars. Defaults to filled.
	 *
	 * @argument.split True to give each channel of a stereo sound its own lane. Defaults to false, which draws both over one another.
	 *
	 * @return The absolute path of the file when path is given. Otherwise the SVG as a string, or the PNG as binary data.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		AudioBuffer	buffer	= Audios.require( arguments.get( audio ), "audio" );
		String		path	= arguments.getAsString( Key.path );
		String		kind	= ImageOutput.format( arguments.getAsString( format ), path, "svg" );
		int			w		= arguments.getAsInteger( width );
		int			h		= arguments.getAsInteger( height );
		ImageOutput.checkSize( w, h, 8192, 4096 );
		String chosen = arguments.getAsString( style ).trim().toLowerCase();
		if ( !chosen.equals( "filled" ) && !chosen.equals( "bars" ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The style must be filled or bars but was [" + arguments.getAsString( style ) + "]" );
		}
		String					backgroundText	= arguments.getAsString( background );
		WaveformImage.Options	options			= new WaveformImage.Options( w, h, Colors.parse( arguments.getAsString( color ), "color" ),
		    backgroundText.isBlank() ? 0 : Colors.parse( backgroundText, "background" ), chosen.equals( "bars" ), arguments.getAsBoolean( split ) );
		byte[]					data;
		Object					result;
		if ( kind.equals( "svg" ) ) {
			String svg = WaveformImage.svg( buffer, options );
			data	= ImageOutput.bytes( svg );
			result	= svg;
		} else {
			data	= Png.encode( w, h, WaveformImage.pixels( buffer, options ) );
			result	= data;
		}
		if ( path.isBlank() ) {
			return result;
		}
		Path target = FileSystemUtil.expandPath( context, path ).absolutePath();
		ImageOutput.write( target, data );
		return target.toString();
	}

}
