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
package ortus.boxlang.modules.audio;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * The bx-audio command line, run through the same Cli class that `boxlang module:bxaudio` uses
 */
public class CliTest extends BaseIntegrationTest {

	/**
	 * Run a command line and return everything it printed
	 */
	private String run( String... args ) {
		variables.put( Key.of( "cliArgs" ), new Array( ( Object[] ) args ) );
		variables.put( Key.of( "moduleRoot" ), Path.of( "build", "module" ).toAbsolutePath().toString() );
		runtime.executeSource(
		    """
		    cli = new bxModules.bxaudio.cli.Cli( moduleRoot );
		    cli.run( cliArgs );
		    result = cli.getOutput();
		    """,
		    context );
		return ( String ) variables.get( Key.of( "result" ) );
	}

	/**
	 * Write a one second stereo tone to a file and return its path
	 */
	private String tone( Path dir, String name ) {
		String path = dir.resolve( name ).toString();
		variables.put( Key.of( "target" ), path );
		runtime.executeSource( "audioSave( audioPan( audioGain( audioOsc( 'sine', 440, 1 ), -6 ), 0 ), target )", context );
		return path;
	}

	@DisplayName( "help lists every command and each command has its own usage" )
	@Test
	void testHelp() {
		String help = run( "help" );
		for ( String command : new String[] { "info", "convert", "normalize", "trim", "waveform", "pieces", "render", "explorer", "doctor", "version",
		    "help" } ) {
			assertThat( help ).contains( command );
		}
		assertThat( run() ).isEqualTo( help );
		assertThat( run( "help", "convert" ) ).contains( "--bitrate" );
		assertThat( run( "trim", "--help" ) ).contains( "--start" );
	}

	@DisplayName( "info describes a file" )
	@Test
	void testInfo( @TempDir Path dir ) {
		String info = run( "info", tone( dir, "tone.wav" ) );
		assertThat( info ).contains( "format    WAV" );
		assertThat( info ).contains( "channels  2" );
		assertThat( info ).contains( "44100 Hz" );
		assertThat( info ).contains( "length    1.000 s" );
		// -6 dB of a full scale sine is a peak of 0.5, and a centered pan takes 3 dB off each side, so 0.354 or -9.0 dBFS
		assertThat( info ).contains( "peak      0.354" );
		assertThat( info ).contains( "-9.0 dBFS" );
	}

	@DisplayName( "convert writes MP3 at the requested bitrate, and changes the sample rate and channels" )
	@Test
	void testConvert( @TempDir Path dir ) throws Exception {
		String	source	= tone( dir, "tone.wav" );
		String	low		= dir.resolve( "low.mp3" ).toString();
		String	high	= dir.resolve( "high.mp3" ).toString();
		run( "convert", source, low, "--bitrate=64" );
		run( "convert", source, high, "--bitrate=192" );
		assertThat( Files.size( Path.of( high ) ) ).isGreaterThan( Files.size( Path.of( low ) ) * 2 );
		assertThat( run( "info", high ) ).contains( "format    MP3" );

		String small = dir.resolve( "small.wav" ).toString();
		run( "convert", source, small, "--sampleRate=22050", "--channels=1" );
		String info = run( "info", small );
		assertThat( info ).contains( "channels  1" );
		assertThat( info ).contains( "22050 Hz" );
	}

	@DisplayName( "normalize sets the peak and trim cuts the requested section" )
	@Test
	void testNormalizeAndTrim( @TempDir Path dir ) {
		String	source	= tone( dir, "tone.wav" );
		String	loud	= dir.resolve( "loud.wav" ).toString();
		run( "normalize", source, loud, "--db=-3" );
		// -3 dB below full scale is 0.708
		assertThat( run( "info", loud ) ).contains( "peak      0.708" );

		String cut = dir.resolve( "cut.wav" ).toString();
		run( "trim", source, cut, "--start=0.25", "--end=0.75" );
		assertThat( run( "info", cut ) ).contains( "length    0.500 s" );
	}

	@DisplayName( "waveform draws one line of the requested width, with plain characters unless asked" )
	@Test
	void testWaveform( @TempDir Path dir ) {
		String	source	= tone( dir, "tone.wav" );
		String	plain	= run( "waveform", source, "--width=40" ).replaceAll( "\\R$", "" );
		assertThat( plain ).hasLength( 40 );
		assertThat( plain.chars().allMatch( c -> c < 128 ) ).isTrue();
		assertThat( plain ).contains( "@" );
		String blocks = run( "waveform", source, "--width=40", "--unicode" ).replaceAll( "\\R$", "" );
		assertThat( blocks ).hasLength( 40 );
		assertThat( blocks.chars().anyMatch( c -> c > 128 ) ).isTrue();
	}

	@DisplayName( "pieces lists the whole catalog" )
	@Test
	void testPieces() {
		String pieces = run( "pieces" );
		for ( String piece : new String[] { "pop-drive", "lofi", "house", "ambient", "techno", "synthwave", "cinematic" } ) {
			assertThat( pieces ).contains( piece );
		}
	}

	@DisplayName( "render synthesizes a piece to the file asked for, with the settings asked for" )
	@Test
	void testRender( @TempDir Path dir ) {
		String	out		= dir.resolve( "pop.wav" ).toString();
		String	output	= run( "render", "pop-drive", "--out=" + out, "--bars=4", "--bpm=240" );
		assertThat( output ).contains( "wrote " + out );
		// 4 bars at 240 bpm is 4 s, plus the 2.5 s tail
		assertThat( run( "info", out ) ).contains( "length    6.500 s" );
	}

	@DisplayName( "Mistakes in the command line are reported with a clear message" )
	@Test
	void testErrors( @TempDir Path dir ) {
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "bogus" ) ).getMessage() ).contains( "Unknown command [bogus]" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "info" ) ).getMessage() ).contains( "Usage: bx-audio info <file>" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "render", "nope" ) ).getMessage() ).contains( "Unknown piece [nope]" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "render", "house", "--wat=1" ) ).getMessage() ).contains( "no setting [wat]" );
		// a relative path is relative to where the command was run, and the message says where that is
		String message = assertThrows( BoxRuntimeException.class, () -> run( "info", "missing.wav" ) ).getMessage();
		assertThat( message ).contains( "File not found" );
		assertThat( message ).contains( Path.of( System.getProperty( "user.dir" ), "missing.wav" ).toString() );
	}

	@DisplayName( "doctor checks MP3 and the examples" )
	@Test
	void testDoctor() {
		String doctor = run( "doctor" );
		assertThat( doctor ).contains( "ok    MP3 write and read" );
		assertThat( doctor ).contains( "ok    Examples folder" );
	}

}
