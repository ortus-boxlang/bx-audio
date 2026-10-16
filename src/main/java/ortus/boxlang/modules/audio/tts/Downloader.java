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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import ortus.boxlang.modules.audio.util.AudioException;

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

	/**
	 * How long a download may receive nothing before it is given up, in seconds. A server or a proxy that accepts the connection and then goes
	 * quiet must not hold a request thread forever.
	 */
	private static long stallSeconds() {
		return Long.getLong( "bxaudio.download.stallSeconds", 60L );
	}

	private static volatile HttpClient client;

	private static HttpClient client() {
		HttpClient c = client;
		if ( c == null ) {
			HttpClient.Builder	builder			= HttpClient.newBuilder()
			    // Not ALWAYS: a download must not be sent from https to plain http
			    .followRedirects( HttpClient.Redirect.NORMAL )
			    .connectTimeout( Duration.ofSeconds( 30 ) );
			ProxySelector		fromEnvironment	= environmentProxy();
			if ( fromEnvironment != null ) {
				builder.proxy( fromEnvironment );
			}
			c = client = builder.build();
		}
		return c;
	}

	/**
	 * The proxy named by HTTPS_PROXY or HTTP_PROXY, honoring NO_PROXY, for programs that do not set the JVM's own proxy properties. The JVM
	 * properties win when they are set, because the HTTP client already reads them.
	 */
	private static ProxySelector environmentProxy() {
		if ( System.getProperty( "https.proxyHost" ) != null || System.getProperty( "http.proxyHost" ) != null ) {
			return null;
		}
		String raw = firstEnvironment( "HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy" );
		if ( raw == null ) {
			return null;
		}
		try {
			URI uri = URI.create( raw.contains( "://" ) ? raw : "http://" + raw );
			if ( uri.getHost() == null ) {
				return null;
			}
			Proxy		proxy		= new Proxy( Proxy.Type.HTTP, new InetSocketAddress( uri.getHost(), uri.getPort() < 0 ? 80 : uri.getPort() ) );
			String		noProxy		= firstEnvironment( "NO_PROXY", "no_proxy" );
			String[]	exceptions	= noProxy == null ? new String[ 0 ] : noProxy.split( "," );
			return new ProxySelector() {

				@Override
				public java.util.List<Proxy> select( URI target ) {
					String host = target.getHost() == null ? "" : target.getHost().toLowerCase( java.util.Locale.ROOT );
					for ( String entry : exceptions ) {
						String e = entry.trim().toLowerCase( java.util.Locale.ROOT );
						if ( e.equals( "*" ) || ( !e.isEmpty() && ( host.equals( e ) || host.endsWith( e.startsWith( "." ) ? e : "." + e ) ) ) ) {
							return java.util.List.of( Proxy.NO_PROXY );
						}
					}
					return java.util.List.of( proxy );
				}

				@Override
				public void connectFailed( URI target, java.net.SocketAddress address, IOException failure ) {
					// the request itself reports the failure
				}
			};
		} catch ( IllegalArgumentException e ) {
			return null;
		}
	}

	private static String firstEnvironment( String... names ) {
		for ( String name : names ) {
			String value = System.getenv( name );
			if ( value != null && !value.isBlank() ) {
				return value.trim();
			}
		}
		return null;
	}

	/**
	 * One download per target at a time inside this JVM
	 */
	private static final ConcurrentHashMap<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

	private Downloader() {
	}

	/**
	 * Download a file unless it is already there and correct. Two callers that want the same file at the same time share one download:
	 * the second waits, finds the file in place and returns.
	 *
	 * @param url          Where to get it
	 * @param target       Where to put it. Its folder is created.
	 * @param expectedSize The size it must have, in bytes
	 * @param algorithm    The digest algorithm, such as {@code MD5} or {@code SHA-1}
	 * @param expectedHex  The digest it must have, in hex
	 * @param progress     Told about progress, may be null
	 */
	public static void download( String url, Path target, long expectedSize, String algorithm, String expectedHex, Progress progress ) {
		Path			absolute	= target.toAbsolutePath();
		ReentrantLock	lock		= LOCKS.computeIfAbsent( absolute.toString(), k -> new ReentrantLock() );
		lock.lock();
		try {
			Files.createDirectories( absolute.getParent() );
			Path lockFile = absolute.resolveSibling( absolute.getFileName() + ".lock" );
			// Another process may be downloading the same file: a lock file next to it keeps the two from writing the same .part file
			try ( FileChannel channel = FileChannel.open( lockFile, StandardOpenOption.CREATE,
			    StandardOpenOption.WRITE ); FileLock fileLock = channel.lock() ) {
				downloadLocked( url, absolute, expectedSize, algorithm, expectedHex, progress, true );
				Storage.claim( absolute );
			}
		} catch ( IOException e ) {
			throw new AudioException( AudioException.DOWNLOAD_FAILED, "Could not download " + url + ": " + e.getMessage(), e );
		} finally {
			try {
				Files.deleteIfExists( absolute.resolveSibling( absolute.getFileName() + ".lock" ) );
			} catch ( IOException ignored ) {
				// a leftover lock file is harmless
			}
			lock.unlock();
		}
	}

	private static void downloadLocked( String url, Path target, long expectedSize, String algorithm, String expectedHex, Progress progress,
	    boolean mayRetry ) {
		try {
			if ( Files.isRegularFile( target ) && Files.size( target ) == expectedSize && digest( target, algorithm ).equalsIgnoreCase( expectedHex ) ) {
				return;
			}
			Path	part	= target.resolveSibling( target.getFileName() + ".part" );
			long	have	= Files.isRegularFile( part ) ? Files.size( part ) : 0;
			if ( have > expectedSize ) {
				Files.delete( part );
				have = 0;
			}

			if ( have < expectedSize ) {
				// The timeout covers the wait for the headers; the body is watched separately for stalls
				HttpRequest.Builder request = HttpRequest.newBuilder( URI.create( url ) ).timeout( Duration.ofSeconds( 60 ) ).GET();
				if ( have > 0 ) {
					request.header( "Range", "bytes=" + have + "-" );
				}
				HttpResponse<InputStream> response = client().send( request.build(), HttpResponse.BodyHandlers.ofInputStream() );
				try ( InputStream body = response.body() ) {
					if ( response.statusCode() != 200 && response.statusCode() != 206 ) {
						throw new AudioException( AudioException.DOWNLOAD_FAILED,
						    "Could not download " + url + ": the server answered " + response.statusCode() );
					}
					// A server that ignores the range sends the whole file again, so start over
					boolean resumed = response.statusCode() == 206 && have > 0;
					if ( resumed && !rangeStartsAt( response, have ) ) {
						// It answered with another part of the file than the one asked for: the partial file cannot be trusted
						Files.deleteIfExists( part );
						if ( mayRetry ) {
							downloadLocked( url, target, expectedSize, algorithm, expectedHex, progress, false );
							return;
						}
						throw new AudioException( AudioException.DOWNLOAD_FAILED,
						    "Could not download " + url + ": the server did not resume where it was asked to" );
					}
					if ( !resumed ) {
						have = 0;
					}
					copy( url, body, part, resumed, have, expectedSize, progress );
				}
			}

			if ( Files.size( part ) != expectedSize || !digest( part, algorithm ).equalsIgnoreCase( expectedHex ) ) {
				Files.deleteIfExists( part );
				throw new AudioException( AudioException.DOWNLOAD_FAILED, "The download of " + url + " is not the file that was expected (size or " + algorithm
				    + " does not match). It was removed, try again." );
			}
			Files.move( part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE );
		} catch ( IOException e ) {
			throw new AudioException( AudioException.DOWNLOAD_FAILED, "Could not download " + url + ": " + e.getMessage(), e );
		} catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new AudioException( AudioException.DOWNLOAD_FAILED, "The download of " + url + " was interrupted", e );
		}
	}

	/**
	 * @return Whether a 206 answer starts at the byte that was asked for. Without a Content-Range header it is taken as right.
	 */
	private static boolean rangeStartsAt( HttpResponse<?> response, long expectedStart ) {
		return response.headers().firstValue( "Content-Range" ).map( header -> {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile( "bytes\\s+(\\d+)-" ).matcher( header );
			return m.find() && Long.parseLong( m.group( 1 ) ) == expectedStart;
		} ).orElse( true );
	}

	/**
	 * Copy the body to the part file, giving up when nothing arrives for a while
	 */
	private static void copy( String url, InputStream body, Path part, boolean resumed, long have, long expectedSize, Progress progress ) throws IOException {
		AtomicLong		lastData	= new AtomicLong( System.nanoTime() );
		AtomicBoolean	stalled		= new AtomicBoolean();
		AtomicBoolean	finished	= new AtomicBoolean();
		long			stallNanos	= stallSeconds() * 1_000_000_000L;
		Thread			watchdog	= new Thread( () -> {
										while ( !finished.get() ) {
											try {
												Thread.sleep( Math.max( 50, Math.min( 2000, stallNanos / 4_000_000 ) ) );
											} catch ( InterruptedException e ) {
												return;
											}
											if ( System.nanoTime() - lastData.get() > stallNanos ) {
												stalled.set( true );
												try {
													body.close();
												} catch ( IOException ignored ) {
													// closing is how a read that waits forever is stopped
												}
												return;
											}
										}
									}, "bxaudio-download-watchdog" );
		watchdog.setDaemon( true );
		watchdog.start();
		try ( OutputStream out = Files.newOutputStream( part, StandardOpenOption.CREATE,
		    resumed ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE ) ) {
			byte[]	buffer	= new byte[ 1 << 16 ];
			long	done	= have;
			int		read;
			while ( ( read = body.read( buffer ) ) != -1 ) {
				lastData.set( System.nanoTime() );
				out.write( buffer, 0, read );
				done += read;
				if ( progress != null ) {
					progress.update( done, expectedSize );
				}
			}
		} catch ( IOException e ) {
			if ( stalled.get() ) {
				throw new IOException( "nothing was received for " + stallSeconds() + " seconds, so the download of " + url
				    + " was stopped. Run it again: what arrived is kept and the download carries on from there" );
			}
			throw e;
		} finally {
			finished.set( true );
			watchdog.interrupt();
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
			throw new AudioException( AudioException.INTERNAL, "This JVM has no " + algorithm + " digest", e );
		}
	}

}
