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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import ortus.boxlang.modules.audio.tts.Downloader.Progress;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * The voices on disk: one folder per voice holding its model and configuration, laid out the way the Piper engine expects.
 */
public final class VoiceStore {

	private final Path		voicesDir;
	private final String	baseUrl;

	/**
	 * @param voicesDir The folder with one subfolder per voice
	 * @param baseUrl   The URL that the file paths in the catalog are relative to
	 */
	public VoiceStore( Path voicesDir, String baseUrl ) {
		this.voicesDir	= voicesDir;
		this.baseUrl	= baseUrl.endsWith( "/" ) ? baseUrl : baseUrl + "/";
	}

	/**
	 * @return The folder with one subfolder per voice
	 */
	public Path getVoicesDir() {
		return this.voicesDir;
	}

	/**
	 * @return The model file of a voice, whether or not it is installed
	 */
	public Path modelFile( VoiceInfo voice ) {
		return this.voicesDir.resolve( voice.id() ).resolve( voice.id() + ".onnx" );
	}

	/**
	 * @return The configuration file of a voice, whether or not it is installed
	 */
	public Path configFile( VoiceInfo voice ) {
		return this.voicesDir.resolve( voice.id() ).resolve( voice.id() + ".onnx.json" );
	}

	/**
	 * A voice is installed when both files are there at the size the catalog says. Files are only moved into place after their digest
	 * was checked, so the size is enough here and keeps this check fast.
	 */
	public boolean isInstalled( VoiceInfo voice ) {
		try {
			Path	model	= modelFile( voice );
			Path	config	= configFile( voice );
			return Files.isRegularFile( model ) && Files.size( model ) == voice.model().size() && Files.isRegularFile( config )
			    && Files.size( config ) == voice.config().size();
		} catch ( IOException e ) {
			return false;
		}
	}

	/**
	 * Download a voice
	 *
	 * @param progress Told about progress of the large model file, may be null
	 */
	public void install( VoiceInfo voice, Progress progress ) {
		Downloader.download( this.baseUrl + voice.config().path(), configFile( voice ), voice.config().size(), "MD5", voice.config().md5(), null );
		Downloader.download( this.baseUrl + voice.model().path(), modelFile( voice ), voice.model().size(), "MD5", voice.model().md5(), progress );
	}

	/**
	 * Delete a voice from disk
	 *
	 * @return True if there was something to delete
	 */
	public boolean remove( VoiceInfo voice ) {
		Path dir = this.voicesDir.resolve( voice.id() );
		if ( !Files.isDirectory( dir ) ) {
			return false;
		}
		try ( Stream<Path> walk = Files.walk( dir ) ) {
			for ( Path path : walk.sorted( Comparator.reverseOrder() ).toList() ) {
				Files.delete( path );
			}
			return true;
		} catch ( IOException e ) {
			throw new AudioException( AudioException.IO, "Could not remove voice [" + voice.id() + "]: " + e.getMessage(), e );
		}
	}

}
