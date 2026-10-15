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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * The folders under the bx-audio home, what is in them and how to empty them
 */
public final class Storage {

	private Storage() {
	}

	/**
	 * One folder of the home
	 *
	 * @param kind        The name used to ask for it: engine, voices, ffmpeg or cache
	 * @param path        Where it is
	 * @param description What it holds
	 * @param bytes       How much it holds now, 0 when it does not exist
	 */
	public record Entry( String kind, Path path, String description, long bytes ) {

		public boolean exists() {
			return Files.isDirectory( this.path );
		}
	}

	/**
	 * @return The kinds that can be cleared
	 */
	public static List<String> kinds() {
		return List.of( "engine", "voices", "ffmpeg", "cache" );
	}

	/**
	 * @param home The bx-audio home
	 *
	 * @return One entry per kind, in the order of {@link #kinds()}
	 */
	public static List<Entry> list( Path home ) {
		List<Entry> entries = new ArrayList<>();
		for ( String kind : kinds() ) {
			Path path = folder( home, kind );
			entries.add( new Entry( kind, path, describe( kind ), size( path ) ) );
		}
		return entries;
	}

	/**
	 * Delete the contents of one kind, or of all of them
	 *
	 * @param home The bx-audio home
	 * @param kind engine, voices, ffmpeg, cache or all
	 *
	 * @return The bytes freed
	 */
	public static long clear( Path home, String kind ) {
		String name = kind == null ? "" : kind.trim().toLowerCase();
		if ( name.equals( "all" ) ) {
			long freed = 0;
			for ( String each : kinds() ) {
				freed += clear( home, each );
			}
			return freed;
		}
		if ( !kinds().contains( name ) ) {
			throw new BoxRuntimeException( "Unknown kind [" + kind + "]. Use one of: " + String.join( ", ", kinds() ) + ", all." );
		}
		Path	path	= folder( home, name );
		long	bytes	= size( path );
		delete( path );
		return bytes;
	}

	private static Path folder( Path home, String kind ) {
		return switch ( kind ) {
			case "engine" -> TtsHome.engine( home );
			case "voices" -> TtsHome.voices( home );
			case "ffmpeg" -> TtsHome.ffmpeg( home );
			default -> TtsHome.cache( home );
		};
	}

	private static String describe( String kind ) {
		return switch ( kind ) {
			case "engine" -> "Text to speech engine jars";
			case "voices" -> "Downloaded voices";
			case "ffmpeg" -> "FFmpeg, when the system has none";
			default -> "Rendered samples and other things that can be rebuilt";
		};
	}

	private static long size( Path path ) {
		if ( !Files.isDirectory( path ) ) {
			return 0;
		}
		try ( var walk = Files.walk( path ) ) {
			return walk.filter( Files::isRegularFile ).mapToLong( file -> {
				try {
					return Files.size( file );
				} catch ( IOException e ) {
					return 0;
				}
			} ).sum();
		} catch ( IOException e ) {
			return 0;
		}
	}

	private static void delete( Path path ) {
		if ( !Files.isDirectory( path ) ) {
			return;
		}
		try ( var walk = Files.walk( path ) ) {
			for ( Path each : walk.sorted( Comparator.reverseOrder() ).toList() ) {
				Files.delete( each );
			}
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Could not delete " + path + ": " + e.getMessage(), e );
		}
	}

}
