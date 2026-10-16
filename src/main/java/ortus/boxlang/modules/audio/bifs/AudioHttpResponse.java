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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import ortus.boxlang.modules.audio.codec.AudioFiles;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;
import ortus.boxlang.modules.audio.util.KeyDictionary;
import ortus.boxlang.modules.audio.web.HttpAudio;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.util.FileSystemUtil;

@BoxBIF( description = "Build the HTTP response that serves audio, with range requests, ETag and 304" )
public class AudioHttpResponse extends BIF {

	private static final Key	source			= Key.of( "source" );
	private static final Key	request			= Key.of( "request" );
	private static final Key	format			= Key.of( "format" );
	private static final Key	maxAgeSeconds	= Key.of( "maxAgeSeconds" );
	private static final Key	filename		= Key.of( "filename" );
	private static final Key	download		= Key.of( "download" );

	/**
	 * Constructor
	 */
	public AudioHttpResponse() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "any", source ),
		    new Argument( false, "struct", request ),
		    new Argument( false, "string", format, "" ),
		    new Argument( false, "integer", KeyDictionary.bitrate, 0 ),
		    new Argument( false, "integer", Key.of( "bitDepth" ), 16 ),
		    new Argument( false, "long", maxAgeSeconds, 3600L ),
		    new Argument( false, "string", filename, "" ),
		    new Argument( false, "boolean", download, false )
		};
	}

	/**
	 * Works out what to send for a request for audio. It does not send anything: copy the status, the headers and the body to the response of your
	 * web server.
	 *
	 * <p>
	 * It answers what a browser audio player asks for. A request with a Range header gets just those bytes with status 206, which is what lets a player
	 * seek
	 * without downloading everything. A repeated request with a matching If-None-Match gets 304 and no body. HEAD gets the headers only. A range that
	 * is past the end gets 416.
	 *
	 * <p>
	 * The source can be a path (the file is sent as it is, and only the asked bytes are read from disk), an audio buffer (encoded to the format) or
	 * binary data
	 * you already encoded with audioEncode(). Encoding is done on every call, so encode once and keep the bytes when many requests ask for the same
	 * sound.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.source A file path, an audio buffer or binary data.
	 *
	 * @argument.request The request: a struct with method (GET or HEAD, defaults to GET) and headers, a struct of the request headers such as range and
	 *                   if-none-match. A struct that has no headers key is taken to be the headers themselves.
	 *
	 * @argument.format wav, mp3, flac, ogg, opus, m4a or aac. For a buffer it is the format to encode (defaults to mp3). For a path, leave it out to send
	 *                  the file
	 *                  as it is, or give another format to convert. For binary data it says what the data is, and is found from the content when left
	 *                  out.
	 *
	 * @argument.bitrate For MP3, OGG, Opus and AAC when audio is encoded: the bitrate in kbps. Defaults to the format's own.
	 *
	 * @argument.bitDepth For WAV and FLAC when audio is encoded. Defaults to 16.
	 *
	 * @argument.maxAgeSeconds How long a browser may keep it without asking again. 0 makes it ask every time (cheap, since the ETag answers 304), a
	 *                         negative
	 *                         number forbids storing it. Defaults to 3600.
	 *
	 * @argument.filename A file name offered to a browser that saves the audio.
	 *
	 * @argument.download True to ask the browser to save the file instead of playing it. Defaults to false.
	 *
	 * @return A struct with status (200, 206, 304, 405 or 416), statusText, headers (a struct) and body (binary data, empty when there is nothing to
	 *         send).
	 */
	public IStruct _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Object				given		= arguments.get( source );
		String				wanted		= arguments.getAsString( format );
		int					bitrate		= arguments.getAsInteger( KeyDictionary.bitrate );
		int					bitDepth	= arguments.getAsInteger( Key.of( "bitDepth" ) );
		HttpAudio.Content	content;
		String				type;
		if ( given instanceof AudioBuffer audio ) {
			String f = wanted.isEmpty() ? "mp3" : wanted;
			content	= HttpAudio.of( AudioFiles.encode( audio, f, bitDepth, bitrate ) );
			type	= AudioFiles.mimeType( f );
		} else if ( given instanceof byte[] bytes ) {
			String f = wanted.isEmpty() ? HttpAudio.sniff( bytes ) : wanted;
			if ( f == null ) {
				throw new AudioException( AudioException.UNSUPPORTED_FORMAT,
				    "The binary data is not a format that could be recognised. Say what it is with the format argument" );
			}
			content	= HttpAudio.of( bytes );
			type	= AudioFiles.mimeType( f );
		} else if ( given instanceof String pathString ) {
			Path path = FileSystemUtil.expandPath( context, pathString ).absolutePath();
			if ( !Files.isRegularFile( path ) ) {
				throw new AudioException( AudioException.FILE_NOT_FOUND, "The audio file [" + path + "] does not exist" );
			}
			String own = AudioFiles.formatName( path.getFileName().toString().replaceAll( "^.*(?=\\.[^.]*$)", "" ) );
			if ( wanted.isEmpty() || ( own != null && own.equals( AudioFiles.formatName( wanted ) ) ) ) {
				content	= HttpAudio.of( path );
				type	= AudioFiles.mimeType( own == null ? "" : own );
			} else {
				content	= HttpAudio.of( AudioFiles.encode( AudioFiles.read( path ), wanted, bitDepth, bitrate ) );
				type	= AudioFiles.mimeType( wanted );
			}
		} else {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "The source argument must be a file path, an audio buffer or binary data" );
		}

		String				method	= "GET";
		Map<String, String>	headers	= new java.util.HashMap<>();
		if ( arguments.get( request ) instanceof IStruct req ) {
			Object requestedMethod = req.get( Key.of( "method" ) );
			if ( requestedMethod != null ) {
				method = String.valueOf( requestedMethod );
			}
			Object	nested	= req.get( Key.of( "headers" ) );
			IStruct	source	= nested instanceof IStruct s ? s : req;
			for ( Key key : source.keySet() ) {
				Object value = source.get( key );
				if ( value != null ) {
					headers.put( key.getName().toLowerCase( Locale.ROOT ), String.valueOf( value ) );
				}
			}
		}

		HttpAudio.Result	result	= HttpAudio.respond( method, headers, content,
		    new HttpAudio.Options( type, arguments.getAsLong( maxAgeSeconds ), arguments.getAsString( filename ), arguments.getAsBoolean( download ) ) );
		IStruct				out		= new Struct( IStruct.TYPES.LINKED );
		result.headers().forEach( ( name, value ) -> out.put( Key.of( name ), value ) );
		return Struct.ofNonConcurrent( "status", result.status(), "statusText", result.statusText(), "headers", out, "body", result.body() );
	}

}
