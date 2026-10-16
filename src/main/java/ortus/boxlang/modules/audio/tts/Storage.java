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

import ortus.boxlang.modules.audio.util.AudioException;

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
		String name = kind == null ? "" : kind.trim().toLowerCase( java.util.Locale.ROOT );
		if ( !name.equals( "all" ) && !kinds().contains( name ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Unknown kind [" + kind + "]. Use one of: " + String.join( ", ", kinds() ) + ", all." );
		}
		requireOwned( home );
		if ( name.equals( "all" ) ) {
			long freed = 0;
			for ( String each : kinds() ) {
				freed += clear( home, each );
			}
			return freed;
		}
		if ( !kinds().contains( name ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Unknown kind [" + kind + "]. Use one of: " + String.join( ", ", kinds() ) + ", all." );
		}
		Path	path	= folder( home, name );
		long	bytes	= size( path );
		delete( path );
		return bytes;
	}

	/**
	 * The name of the file that says a folder is a bx-audio home, written when the first download lands in it
	 */
	public static final String MARKER = ".bxaudio-home";

	/**
	 * Mark the home that holds a downloaded file as ours, so a later clear knows it may delete there. Called after a download.
	 *
	 * @param downloaded A file that was downloaded into the home, in one of its subfolders
	 */
	public static void claim( Path downloaded ) {
		for ( Path dir = downloaded.toAbsolutePath().getParent(); dir != null && dir.getParent() != null; dir = dir.getParent() ) {
			if ( kinds().contains( dir.getFileName().toString() ) ) {
				try {
					Files.writeString( dir.getParent().resolve( MARKER ),
					    "This folder is a bx-audio home. `bx-audio clean` may delete its engine, voices, ffmpeg and cache folders.\n" );
				} catch ( IOException e ) {
					// not being able to write the marker only means a later clear will refuse, which is the safe side
				}
				return;
			}
		}
	}

	/**
	 * Refuse to delete in a folder that is not known to be a bx-audio home: a home setting that points at a project folder which happens to
	 * have a cache or voices folder of its own must not lose it. The default home counts as ours, and so does any folder with the marker.
	 */
	private static void requireOwned( Path home ) {
		Path normal = home.toAbsolutePath().normalize();
		if ( Files.exists( normal.resolve( MARKER ) ) || normal.equals( TtsHome.home().toAbsolutePath().normalize() ) && isDefaultHome( normal ) ) {
			return;
		}
		if ( !Files.isDirectory( normal ) ) {
			return;
		}
		throw new AudioException( AudioException.INVALID_ARGUMENT,
		    "[" + normal + "] is not known to be a bx-audio home (it has no " + MARKER + " file), so nothing was deleted. "
		        + "Downloads write that file. If this is the folder you set as home and you want it cleared, create the file yourself." );
	}

	private static boolean isDefaultHome( Path home ) {
		return home.equals( Path.of( System.getProperty( "user.home" ) ).resolve( ".bxaudio" ).toAbsolutePath().normalize() );
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
		delete( path, Files::delete );
	}

	/**
	 * Something that removes one file or folder, so the way a failure is reported can be tested
	 */
	interface Deleter {

		void delete( Path path ) throws IOException;

	}

	/**
	 * Delete everything under a folder that can be deleted. A file that cannot go (on Windows a jar the module has loaded stays locked until
	 * the process ends) does not stop the rest, and one error afterwards names what is left and what to do about it.
	 */
	static void delete( Path path, Deleter deleter ) {
		if ( !Files.isDirectory( path ) ) {
			return;
		}
		List<Path>	stuck	= new java.util.ArrayList<>();
		String		reason	= "";
		try ( var walk = Files.walk( path ) ) {
			for ( Path each : walk.sorted( Comparator.reverseOrder() ).toList() ) {
				try {
					deleter.delete( each );
				} catch ( IOException e ) {
					// a folder that still holds a stuck file cannot go either, which is not worth reporting again
					if ( stuck.isEmpty() ) {
						reason = String.valueOf( e.getMessage() );
					}
					if ( ! ( e instanceof java.nio.file.DirectoryNotEmptyException ) ) {
						stuck.add( each );
					}
				}
			}
		} catch ( IOException e ) {
			throw new AudioException( AudioException.IO, "Could not read " + path + ": " + e.getMessage(), e );
		}
		if ( !stuck.isEmpty() ) {
			throw new AudioException( AudioException.IO,
			    "Could not delete " + stuck.size() + " file" + ( stuck.size() == 1 ? "" : "s" ) + " in " + path + " (first: " + stuck.get( 0 ) + ", " + reason
			        + "). They are probably in use: on Windows the jars of the speech engine stay locked while BoxLang runs. The rest was deleted. Stop BoxLang and run the clean again." );
		}
	}

}
