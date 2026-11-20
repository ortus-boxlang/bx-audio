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
	 * The model file of a voice, whether or not it is installed
	 *
	 * @param voice The voice
	 *
	 * @return The file: the voice's own, or the one model every Kokoro voice shares
	 */
	public Path modelFile( VoiceInfo voice ) {
		if ( VoiceInfo.KOKORO.equals( voice.engine() ) ) {
			return this.voicesDir.resolve( VoiceInfo.KOKORO ).resolve( fileName( voice.model() ) );
		}
		return this.voicesDir.resolve( voice.id() ).resolve( voice.id() + ".onnx" );
	}

	/**
	 * The configuration file of a voice, whether or not it is installed
	 *
	 * @param voice The voice
	 *
	 * @return The file: the voice's own, or for a Kokoro voice the voices pack that every Kokoro voice shares
	 */
	public Path configFile( VoiceInfo voice ) {
		if ( VoiceInfo.KOKORO.equals( voice.engine() ) ) {
			return this.voicesDir.resolve( VoiceInfo.KOKORO ).resolve( fileName( voice.config() ) );
		}
		return this.voicesDir.resolve( voice.id() ).resolve( voice.id() + ".onnx.json" );
	}

	/**
	 * The name of a file below the Kokoro folder: the last part of its URL. Every Kokoro voice shares the model and the voices pack, so they
	 * are installed once.
	 *
	 * @param file A file of a voice
	 *
	 * @return The file name
	 */
	private static String fileName( VoiceInfo.VoiceFile file ) {
		return file.path().substring( file.path().lastIndexOf( '/' ) + 1 );
	}

	/**
	 * A voice is installed when both files are there at the size the catalog says. Files are only moved into place after their digest
	 * was checked, so the size is enough here and keeps this check fast.
	 *
	 * @param voice The voice
	 *
	 * @return True when both files are there at the size the catalog says
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
	 * Download a voice, checking each file against its digest
	 *
	 * @param voice    The voice
	 * @param progress Told about progress of the large model file, may be null
	 */
	public void install( VoiceInfo voice, Progress progress ) {
		VoiceInfo.VoiceFile	config	= voice.config();
		VoiceInfo.VoiceFile	model	= voice.model();
		Downloader.download( config.url( this.baseUrl ), configFile( voice ), config.size(), config.algorithm(), config.digest(), null );
		Downloader.download( model.url( this.baseUrl ), modelFile( voice ), model.size(), model.algorithm(), model.digest(), progress );
	}

	/**
	 * Delete a voice from disk. For a Kokoro voice that deletes the shared model and voices pack, so every Kokoro voice goes.
	 *
	 * @param voice The voice
	 *
	 * @return True if there was something to delete
	 */
	public boolean remove( VoiceInfo voice ) {
		// the Kokoro voices share one folder, so removing one removes them all
		Path dir = this.voicesDir.resolve( VoiceInfo.KOKORO.equals( voice.engine() ) ? VoiceInfo.KOKORO : voice.id() );
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
