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
import static com.google.common.truth.Truth.assertWithMessage;
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

	@DisplayName( "completions prints a script per shell, and the committed scripts are what the command prints" )
	@Test
	void testCompletions() throws Exception {
		String[][] shells = { { "bash", "bx-audio.bash" }, { "zsh", "_bx-audio" }, { "fish", "bx-audio.fish" }, { "powershell", "bx-audio.ps1" } };
		for ( String[] shell : shells ) {
			String	script		= run( "completions", shell[ 0 ] );
			String	committed	= Files.readString( Path.of( "completions", shell[ 1 ] ) );
			assertWithMessage( shell[ 0 ] ).that( script.replace( "\r\n", "\n" ).trim() ).isEqualTo( committed.replace( "\r\n", "\n" ).trim() );
			for ( String command : new String[] { "normalize", "master", "render", "completions" } ) {
				assertWithMessage( shell[ 0 ] + " " + command ).that( script ).contains( command );
			}
			assertWithMessage( shell[ 0 ] ).that( script ).contains( "targetDb" );
		}
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "completions", "tcsh" ) ).getMessage() ).contains( "Unknown shell" );
	}

	@DisplayName( "A command refuses an option it does not have" )
	@Test
	void testUnknownOption( @TempDir Path dir ) {
		String	source	= tone( dir, "in.wav" );
		String	target	= dir.resolve( "out.wav" ).toString();
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "normalize", source, target, "--db=-3" ) ).getMessage() )
		    .contains( "Unknown option --db" );
	}

	@DisplayName( "The explorer listens on this machine only unless a host is given" )
	@Test
	void testExplorerHost() {
		String help = run( "help", "explorer" );
		assertThat( help ).contains( "--host=127.0.0.1" );
		assertThat( help ).contains( "this machine only" );
	}

	@DisplayName( "voices shows the usage class of each license, filters by it, and credits writes the credit text" )
	@Test
	void testVoiceUsageAndCredits() {
		String list = run( "voices", "--usage=permissive" );
		assertThat( list ).contains( "USAGE" );
		assertThat( list ).contains( "en_US-joe-medium" );
		assertThat( list ).contains( "permissive" );
		assertThat( run( "voices", "--usage=attribution" ) ).contains( "en_GB-alba-medium" );
		assertThat( run( "voices", "--usage=attribution" ) ).doesNotContain( "en_US-joe-medium" );
		String copyleft = run( "voices", "--excluded", "--usage=copyleft" );
		assertThat( copyleft ).contains( "fr_FR-tom-medium" );
		assertThat( copyleft ).contains( "hy_AM-gor-medium" );
		assertThat( copyleft ).doesNotContain( "en_US-joe-medium" );
		String detail = run( "voices", "joe" );
		assertThat( detail ).contains( "usage        permissive (allows a product you sell)" );
		assertThat( run( "voices", "sam" ) ).contains( "credit       Voice \"sam\"" );
		assertThat( run( "credits", "joe" ) ).contains( "No credit is required for joe (CC0-1.0)." );
		assertThat( run( "credits", "sam", "joe" ) ).contains( "Apache License 2.0" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "credits" ) ).getMessage() ).contains( "Name at least one voice" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "voices", "--usage=free" ) ).getMessage() ).contains( "Unknown usage [free]" );
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
		run( "normalize", source, loud, "--targetDb=-3" );
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
		for ( String piece : new String[] { "pop-drive", "lofi", "house", "ambient", "techno", "synthwave", "cinematic", "theme" } ) {
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

	@DisplayName( "voices lists the catalog and filters it, and says where the licenses stand" )
	@Test
	void testVoices( @TempDir Path home ) {
		useSpeechHome( home );
		String voices = run( "voices" );
		assertThat( voices ).contains( "en_US-joe-medium" );
		assertThat( voices ).contains( "CC0-1.0" );
		assertThat( voices ).contains( "open license" );
		assertThat( voices ).contains( "en_GB-cori-high" );
		assertThat( run( "voices", "--lang=en_US" ) ).contains( "en_US-joe-medium" );
		assertThat( run( "voices", "--lang=en_GB" ) ).contains( "en_GB-alba-medium" );
		assertThat( run( "voices", "--lang=de_DE" ) ).contains( "No voices match" );
		assertThat( run( "voices", "--installed" ) ).contains( "No voices match" );
	}

	@DisplayName( "voices shows why voices are not offered, details of one voice, and the licenses" )
	@Test
	void testVoicesExcludedDetailAndLicenses( @TempDir Path home ) {
		useSpeechHome( home );
		assertThat( run( "voices" ) ).contains( "more voices are not offered" );
		String excluded = run( "voices", "--excluded" );
		assertThat( excluded ).contains( "en_US-ryan-high" );
		assertThat( excluded ).contains( "Non-commercial" );
		assertThat( excluded ).doesNotContain( "en_US-joe-medium" );
		assertThat( run( "voices", "--all" ) ).contains( "en_US-joe-medium" );
		assertThat( run( "voices", "--all" ) ).contains( "en_US-ryan-high" );
		assertThat( run( "voices", "--downloadable" ) ).contains( "downloadable" );
		assertThat( run( "voices", "--license=Apache-2.0" ) ).contains( "en_US-sam-medium" );
		String detail = run( "voices", "joe" );
		assertThat( detail ).contains( "CC0-1.0" );
		assertThat( detail ).contains( "downloadable" );
		assertThat( detail ).contains( "creativecommons.org" );
		assertThat( run( "voices", "en_US-ryan-high" ) ).contains( "not offered" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "voices", "nobody" ) ).getMessage() ).contains( "Unknown voice [nobody]" );
		String licenses = run( "licenses" );
		assertThat( licenses ).contains( "CC-BY-SA-4.0" );
		assertThat( licenses ).contains( "not legal advice" );
		String help = run( "help", "voices" );
		assertThat( help ).contains( "--excluded" );
		assertThat( help ).contains( "--downloadable" );
	}

	@DisplayName( "speak, install and remove report what is missing instead of failing with a stack trace" )
	@Test
	void testSpeechErrors( @TempDir Path home ) {
		useSpeechHome( home );
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "speak", "Hello" ) ).getMessage() ).contains( "--voice" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "speak", "--voice=joe" ) ).getMessage() ).contains( "Missing text" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "speak", "Hello", "--voice=joe", "--no-install" ) ).getMessage() )
		    .contains( "bx-audio install engine" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "install", "nope" ) ).getMessage() ).contains( "Unknown voice [nope]" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "install" ) ).getMessage() ).contains( "Usage: bx-audio install" );
		assertThat(
		    assertThrows( BoxRuntimeException.class, () -> run( "speak", "Hello there.", "--voice=joe", "--captions=x.srt", "--no-install" ) ).getMessage() )
		    .contains( "bx-audio install engine" );
		assertThat( run( "help", "speak" ) ).contains( "--captions" );
		assertThat( run( "remove", "joe" ) ).contains( "joe was not installed" );
		assertThat( run( "remove", "engine" ) ).contains( "engine was not installed" );
	}

	@DisplayName( "loudness, master, stretch, pitch, bpm and silence work on files" )
	@Test
	void testMasteringCommands( @TempDir Path dir ) {
		String	tone	= tone( dir, "tone.wav" );
		String	loud	= run( "loudness", tone );
		assertThat( loud ).contains( "integrated" );
		assertThat( loud ).contains( "LUFS" );
		String mastered = dir.resolve( "mastered.wav" ).toString();
		assertThat( run( "master", tone, mastered, "--target=-16" ) ).contains( "-16." );
		assertThat( run( "loudness", mastered ) ).containsMatch( "integrated +-1[56]\\.\\d LUFS" );
		String stretched = dir.resolve( "slow.wav" ).toString();
		assertThat( run( "stretch", tone, stretched, "--rate=0.5" ) ).contains( "2.0" );
		assertThat( run( "stretch", tone, stretched, "--seconds=3" ) ).contains( "3.00 s" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "stretch", tone, stretched ) ).getMessage() ).contains( "--rate" );
		String pitched = dir.resolve( "up.wav" ).toString();
		assertThat( run( "pitch", tone, pitched, "--semitones=12" ) ).contains( "wrote" );
		assertThat( run( "info", pitched ) ).contains( "length    1.000 s" );
		assertThat( run( "bpm", tone ) ).containsMatch( "No clear pulse|BPM" );
		assertThat( run( "silence", tone ) ).contains( "No silence found" );
		assertThat( run( "silence", tone, "--trim=" + dir.resolve( "trimmed.wav" ) ) ).contains( "wrote" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "pitch", tone, pitched ) ).getMessage() ).contains( "--semitones" );
	}

	@DisplayName( "play keeps time with a null output, loopify shortens, and sprite joins files with a map" )
	@Test
	void testGameCommands( @TempDir Path dir ) throws Exception {
		String	tone	= tone( dir, "tone.wav" );
		String	second	= tone( dir, "jump.wav" );
		assertThat( run( "play", tone, "--output=null" ) ).contains( "playing" );
		String looped = dir.resolve( "loop.wav" ).toString();
		assertThat( run( "loopify", tone, looped, "--crossfade=0.2" ) ).contains( "0.80 s" );
		String	sprite	= dir.resolve( "sfx/sprite.wav" ).toString();
		String	listing	= run( "sprite", sprite, tone, second, "--gap=0.5" );
		assertThat( listing ).contains( "  tone " );
		assertThat( listing ).contains( "  jump " );
		assertThat( Files.readString( dir.resolve( "sfx/sprite.json" ) ) ).contains( "\"sprite\"" );
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "sprite", sprite ) ).getMessage() ).contains( "Usage: bx-audio sprite" );
	}

	@DisplayName( "where shows the storage folders and clean asks first, then deletes only what was named" )
	@Test
	void testWhereAndClean( @TempDir Path home ) throws Exception {
		useSpeechHome( home );
		Files.writeString( home.resolve( ".bxaudio-home" ), "" );
		Path voice = home.resolve( "voices/joe/model.onnx" );
		Files.createDirectories( voice.getParent() );
		Files.write( voice, new byte[ 2_000_000 ] );
		String where = run( "where" );
		assertThat( where ).contains( home.toString() );
		assertThat( where ).contains( "voices" );
		assertThat( where ).contains( "2.0 MB" );
		assertThat( run( "clean", "voices" ) ).contains( "Add --yes" );
		assertThat( Files.exists( voice ) ).isTrue();
		assertThat( run( "clean", "engine", "--yes" ) ).contains( "freed 0.0 MB" );
		assertThat( Files.exists( voice ) ).isTrue();
		assertThat( run( "clean", "voices", "--yes" ) ).contains( "freed 2.0 MB" );
		assertThat( Files.exists( voice ) ).isFalse();
		assertThat( assertThrows( BoxRuntimeException.class, () -> run( "clean", "bogus", "--yes" ) ).getMessage() ).contains( "Unknown kind" );
	}

	@DisplayName( "doctor checks MP3 and the examples, and reports the speech setup without failing on it" )
	@Test
	void testDoctor( @TempDir Path home ) {
		useSpeechHome( home );
		String doctor = run( "doctor" );
		assertThat( doctor ).contains( "ok    MP3 write and read" );
		assertThat( doctor ).contains( "ok    Examples folder" );
		assertThat( doctor ).contains( "info  speech engine" );
		assertThat( doctor ).contains( "info  voices installed" );
	}

}
