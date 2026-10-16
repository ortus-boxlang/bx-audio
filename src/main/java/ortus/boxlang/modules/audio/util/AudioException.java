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
 * The error every function of the module throws. It is a BoxRuntimeException with a stable {@code type}, so a caller can tell the cases apart
 * with {@code catch( "bxaudio.ffmpegMissing" e )} instead of reading the message.
 *
 * <p>
 * The types are listed in the error reference of the documentation. They are part of the API: a type is never renamed, and the message may be
 * improved at any time.
 */
public class AudioException extends BoxRuntimeException {

	/**
	 * An argument has a value or a type the function cannot use
	 */
	public static final String	INVALID_ARGUMENT		= "bxaudio.invalidArgument";
	/**
	 * A file that was named does not exist
	 */
	public static final String	FILE_NOT_FOUND			= "bxaudio.fileNotFound";
	/**
	 * A file could not be read, written, moved or deleted
	 */
	public static final String	IO						= "bxaudio.io";
	/**
	 * The format, encoding, bit depth, channel count or sample rate is not one the module can handle
	 */
	public static final String	UNSUPPORTED_FORMAT		= "bxaudio.unsupportedFormat";
	/**
	 * The file claims to be audio but is damaged
	 */
	public static final String	CORRUPT_AUDIO			= "bxaudio.corruptAudio";
	/**
	 * The audio is too long to hold in memory
	 */
	public static final String	TOO_LARGE				= "bxaudio.tooLarge";
	/**
	 * A feature needs FFmpeg and there is none, and none can be downloaded
	 */
	public static final String	FFMPEG_MISSING			= "bxaudio.ffmpegMissing";
	/**
	 * FFmpeg ran and failed, or could not be run
	 */
	public static final String	FFMPEG_FAILED			= "bxaudio.ffmpegFailed";
	/**
	 * The speech engine is not installed
	 */
	public static final String	ENGINE_NOT_INSTALLED	= "bxaudio.engineNotInstalled";
	/**
	 * The voice is known but not installed
	 */
	public static final String	VOICE_NOT_INSTALLED		= "bxaudio.voiceNotInstalled";
	/**
	 * No voice has that name, or the voice is not one the module offers
	 */
	public static final String	VOICE_NOT_FOUND			= "bxaudio.voiceNotFound";
	/**
	 * The voice could not say the text
	 */
	public static final String	SPEECH_FAILED			= "bxaudio.speechFailed";
	/**
	 * A download failed, stalled or did not match its checksum
	 */
	public static final String	DOWNLOAD_FAILED			= "bxaudio.downloadFailed";
	/**
	 * Something the module ships is missing or unreadable
	 */
	public static final String	INTERNAL				= "bxaudio.internal";

	public AudioException( String type, String message ) {
		super( message, type, null );
	}

	public AudioException( String type, String message, Throwable cause ) {
		super( message, type, cause );
	}

}
