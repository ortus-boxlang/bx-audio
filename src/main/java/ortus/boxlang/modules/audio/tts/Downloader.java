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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Downloads one file, checks its size and digest, and only then puts it in place. A download that is interrupted leaves a {@code .part}
 * file next to the target, and the next attempt carries on from where it stopped.
 */
public final class Downloader {

	/**
	 * Told how far a download has got
	 */
	@FunctionalInterface
	public interface Progress {

		/**
		 * @param done  Bytes of the file that are on disk
		 * @param total Size of the file in bytes
		 */
		void update( long done, long total );
	}

	private static final HttpClient CLIENT = HttpClient.newBuilder()
	    .followRedirects( HttpClient.Redirect.ALWAYS )
	    .connectTimeout( Duration.ofSeconds( 30 ) )
	    .build();

	private Downloader() {
	}

	/**
	 * Download a file unless it is already there and correct
	 *
	 * @param url          Where to get it
	 * @param target       Where to put it. Its folder is created.
	 * @param expectedSize The size it must have, in bytes
	 * @param algorithm    The digest algorithm, such as {@code MD5} or {@code SHA-1}
	 * @param expectedHex  The digest it must have, in hex
	 * @param progress     Told about progress, may be null
	 */
	public static void download( String url, Path target, long expectedSize, String algorithm, String expectedHex, Progress progress ) {
		try {
			if ( Files.isRegularFile( target ) && Files.size( target ) == expectedSize && digest( target, algorithm ).equalsIgnoreCase( expectedHex ) ) {
				return;
			}
			Files.createDirectories( target.toAbsolutePath().getParent() );
			Path	part	= target.resolveSibling( target.getFileName() + ".part" );
			long	have	= Files.isRegularFile( part ) ? Files.size( part ) : 0;
			if ( have > expectedSize ) {
				Files.delete( part );
				have = 0;
			}

			if ( have < expectedSize ) {
				HttpRequest.Builder request = HttpRequest.newBuilder( URI.create( url ) ).timeout( Duration.ofMinutes( 30 ) ).GET();
				if ( have > 0 ) {
					request.header( "Range", "bytes=" + have + "-" );
				}
				HttpResponse<InputStream> response = CLIENT.send( request.build(), HttpResponse.BodyHandlers.ofInputStream() );
				try ( InputStream body = response.body() ) {
					if ( response.statusCode() != 200 && response.statusCode() != 206 ) {
						throw new BoxRuntimeException( "Could not download " + url + ": the server answered " + response.statusCode() );
					}
					// A server that ignores the range sends the whole file again, so start over
					boolean resumed = response.statusCode() == 206 && have > 0;
					if ( !resumed ) {
						have = 0;
					}
					try ( OutputStream out = Files.newOutputStream( part, StandardOpenOption.CREATE,
					    resumed ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE ) ) {
						byte[]	buffer	= new byte[ 1 << 16 ];
						long	done	= have;
						int		read;
						while ( ( read = body.read( buffer ) ) != -1 ) {
							out.write( buffer, 0, read );
							done += read;
							if ( progress != null ) {
								progress.update( done, expectedSize );
							}
						}
					}
				}
			}

			if ( Files.size( part ) != expectedSize || !digest( part, algorithm ).equalsIgnoreCase( expectedHex ) ) {
				Files.deleteIfExists( part );
				throw new BoxRuntimeException( "The download of " + url + " is not the file that was expected (size or " + algorithm
				    + " does not match). It was removed, try again." );
			}
			Files.move( part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE );
		} catch ( IOException e ) {
			throw new BoxRuntimeException( "Could not download " + url + ": " + e.getMessage(), e );
		} catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new BoxRuntimeException( "The download of " + url + " was interrupted", e );
		}
	}

	/**
	 * The digest of a file, in lower case hex
	 */
	public static String digest( Path file, String algorithm ) throws IOException {
		try ( InputStream in = Files.newInputStream( file ) ) {
			MessageDigest	md		= MessageDigest.getInstance( algorithm );
			byte[]			buffer	= new byte[ 1 << 16 ];
			int				read;
			while ( ( read = in.read( buffer ) ) != -1 ) {
				md.update( buffer, 0, read );
			}
			return HexFormat.of().formatHex( md.digest() );
		} catch ( NoSuchAlgorithmException e ) {
			throw new BoxRuntimeException( "This JVM has no " + algorithm + " digest", e );
		}
	}

}
