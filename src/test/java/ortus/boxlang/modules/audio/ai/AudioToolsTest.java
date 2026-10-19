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
package ortus.boxlang.modules.audio.ai;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.BaseIntegrationTest;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * The AI tool facade works on files, so these tests make real audio files in a temp folder, call each tool through BoxLang and read the
 * files that come back. Nothing here needs bx-ai, see AiRegistrationTest for that.
 */
public class AudioToolsTest extends BaseIntegrationTest {

	@TempDir
	Path	dir;

	String	tone;

	/**
	 * Each test gets a 2 second 440 Hz tone, a tool facade that may only use the temp folder, and its output folder
	 */
	@BeforeEach
	void makeTone() {
		tone = dir.resolve( "tone.wav" ).toString().replace( '\\', '/' );
		variables.put( Key.of( "dir" ), dir.toString().replace( '\\', '/' ) );
		variables.put( Key.of( "tone" ), tone );
		runtime.executeSource(
		    """
		    audioSave( audioOsc( shape="sine", freq=440, seconds=2, sampleRate=22050 ), tone );
		    tools = new bxModules.bxaudio.models.tools.AudioTools( { allowedPaths : [ dir ], outputDir : dir & "/out" } );
		    """,
		    context );
	}

	private Object run( String source ) {
		runtime.executeSource( source, context );
		return variables.get( result );
	}

	private IStruct struct( String source ) {
		return ( IStruct ) run( source );
	}

	private double seconds( IStruct made ) {
		return ( ( Number ) made.get( Key.of( "seconds" ) ) ).doubleValue();
	}

	private String errorType( String source ) {
		BoxRuntimeException e = assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( source, context ) );
		return e.getType();
	}

	// ------------------------------------------------------------------------------------------------------------
	// Inspect
	// ------------------------------------------------------------------------------------------------------------

	@DisplayName( "audioInfo describes the file and measures levels when asked" )
	@Test
	void testInfo() {
		IStruct info = struct( "result = tools.toolInfo( tone, true )" );
		assertThat( info.get( Key.of( "channels" ) ) ).isEqualTo( 1 );
		assertThat( info.get( Key.of( "sampleRate" ) ) ).isEqualTo( 22050 );
		assertThat( ( ( Number ) info.get( Key.of( "seconds" ) ) ).doubleValue() ).isWithin( 0.001 ).of( 2.0 );
		assertThat( info.containsKey( Key.of( "peak" ) ) ).isTrue();
		assertThat( info.get( Key.of( "path" ) ).toString().replace( '\\', '/' ) ).endsWith( "tone.wav" );
	}

	@DisplayName( "audioLoudness, audioBpm and audioDetectSilence report on a file" )
	@Test
	void testAnalysis() {
		IStruct loud = struct( "result = tools.toolLoudness( tone )" );
		assertThat( loud.containsKey( Key.of( "integrated" ) ) ).isTrue();

		IStruct tempo = struct( "result = tools.toolBpm( tone )" );
		assertThat( tempo.containsKey( Key.of( "bpm" ) ) ).isTrue();
		assertThat( tempo.containsKey( Key.of( "beatCount" ) ) ).isTrue();

		runtime.executeSource(
		    """
		    gap = audioConcat( [ audioOsc( "sine", 440, 1, 22050 ), audioSilence( 1, 1, 22050 ), audioOsc( "sine", 440, 1, 22050 ) ] );
		    audioSave( gap, dir & "/gap.wav" );
		    """,
		    context );
		Array quiet = ( Array ) run( "result = tools.toolDetectSilence( dir & '/gap.wav' )" );
		assertThat( quiet ).hasSize( 1 );
		assertThat( ( ( Number ) ( ( IStruct ) quiet.get( 0 ) ).get( Key.of( "duration" ) ) ).doubleValue() ).isWithin( 0.05 ).of( 1.0 );
	}

	@DisplayName( "audioWaveformImage and audioSpectrogramImage write a PNG in the output folder" )
	@Test
	void testPictures() throws Exception {
		for ( String tool : new String[] { "toolWaveform", "toolSpectrogram" } ) {
			IStruct	made	= struct( "result = tools." + tool + "( tone )" );
			Path	picture	= Path.of( made.get( Key.of( "path" ) ).toString() );
			assertThat( Files.exists( picture ) ).isTrue();
			assertThat( picture.getFileName().toString() ).endsWith( ".png" );
			assertThat( picture.getParent().getFileName().toString() ).isEqualTo( "out" );
			// The PNG signature
			assertThat( Files.readAllBytes( picture )[ 1 ] ).isEqualTo( ( byte ) 'P' );
		}
	}

	@DisplayName( "audioReadTags reads a tagged file and leaves it alone" )
	@Test
	void testReadTags() throws Exception {
		Path tagged = dir.resolve( "tagged.mp3" );
		Files.copy( Path.of( "src/test/resources/mp3/lame-tagged.mp3" ), tagged );
		long before = Files.size( tagged );
		variables.put( Key.of( "tagged" ), tagged.toString().replace( '\\', '/' ) );
		IStruct tags = struct( "result = tools.toolReadTags( tagged )" );
		assertThat( tags.get( Key.of( "format" ) ) ).isEqualTo( "mp3" );
		assertThat( Files.size( tagged ) ).isEqualTo( before );
	}

	// ------------------------------------------------------------------------------------------------------------
	// Edit
	// ------------------------------------------------------------------------------------------------------------

	@DisplayName( "audioTrim keeps a section as a new file and never touches the input" )
	@Test
	void testTrim() throws Exception {
		long	before	= Files.size( Path.of( tone ) );
		IStruct	made	= struct( "result = tools.toolTrim( tone, 0.5, 1.5 )" );
		assertThat( seconds( made ) ).isWithin( 0.001 ).of( 1.0 );
		assertThat( made.get( Key.of( "channels" ) ) ).isEqualTo( 1 );
		assertThat( made.get( Key.of( "sampleRate" ) ) ).isEqualTo( 22050 );
		Path out = Path.of( made.get( Key.of( "path" ) ).toString() );
		assertThat( Files.exists( out ) ).isTrue();
		assertThat( out.getFileName().toString() ).startsWith( "tone-trim-" );
		assertThat( out.getFileName().toString() ).endsWith( ".wav" );
		assertThat( Files.size( Path.of( tone ) ) ).isEqualTo( before );
	}

	@DisplayName( "audioTrim without an end keeps everything after the start" )
	@Test
	void testTrimToEnd() {
		assertThat( seconds( struct( "result = tools.toolTrim( tone, 0.5 )" ) ) ).isWithin( 0.001 ).of( 1.5 );
	}

	@DisplayName( "an output path the agent names is used" )
	@Test
	void testOutputPath() {
		IStruct made = struct( "result = tools.toolTrim( path=tone, start=0, end=1, outputPath=dir & '/chosen/short.wav' )" );
		assertThat( made.get( Key.of( "path" ) ).toString().replace( '\\', '/' ) ).endsWith( "/chosen/short.wav" );
		assertThat( Files.exists( dir.resolve( "chosen/short.wav" ) ) ).isTrue();
	}

	@DisplayName( "audioTrimSilence cuts the quiet ends" )
	@Test
	void testTrimSilence() {
		runtime.executeSource(
		    """
		    padded = audioConcat( [ audioSilence( 1, 1, 22050 ), audioOsc( "sine", 440, 1, 22050 ), audioSilence( 1, 1, 22050 ) ] );
		    audioSave( padded, dir & "/padded.wav" );
		    """,
		    context );
		IStruct made = struct( "result = tools.toolTrimSilence( dir & '/padded.wav' )" );
		assertThat( seconds( made ) ).isWithin( 0.1 ).of( 1.0 );
	}

	@DisplayName( "audioConcat and audioMix take lists of files" )
	@Test
	void testConcatAndMix() {
		assertThat( seconds( struct( "result = tools.toolConcat( [ tone, tone, tone ] )" ) ) ).isWithin( 0.001 ).of( 6.0 );
		assertThat( seconds( struct( "result = tools.toolMix( [ tone, tone ] )" ) ) ).isWithin( 0.001 ).of( 2.0 );
		assertThat( errorType( "tools.toolConcat( [] )" ) ).isEqualTo( "AudioToolInvalidArgument" );
	}

	@DisplayName( "audioConcat and audioMix convert files of another sample rate or channel count to match the first" )
	@Test
	void testMismatchedFormats() {
		runtime.executeSource( "audioSave( audioOsc( shape='sine', freq=220, seconds=1, sampleRate=44100 ), dir & '/hifi.wav' );", context );
		runtime.executeSource( "audioSave( audioChannels( audioOsc( shape='sine', freq=330, seconds=1, sampleRate=8000 ), 2 ), dir & '/lofi.wav' );", context );
		IStruct joined = struct( "result = tools.toolConcat( [ tone, dir & '/hifi.wav', dir & '/lofi.wav' ] )" );
		assertThat( joined.get( Key.of( "sampleRate" ) ) ).isEqualTo( 22050 );
		assertThat( joined.get( Key.of( "channels" ) ) ).isEqualTo( 1 );
		assertThat( seconds( joined ) ).isWithin( 0.01 ).of( 4.0 );
		IStruct mixed = struct( "result = tools.toolMix( [ dir & '/hifi.wav', tone, dir & '/lofi.wav' ] )" );
		assertThat( mixed.get( Key.of( "sampleRate" ) ) ).isEqualTo( 44100 );
		assertThat( mixed.get( Key.of( "channels" ) ) ).isEqualTo( 1 );
		assertThat( seconds( mixed ) ).isWithin( 0.01 ).of( 2.0 );
	}

	@DisplayName( "a concat of too many files is refused" )
	@Test
	void testTooManyFiles() {
		assertThat( errorType( "files = []; for ( i = 1; i <= 65; i++ ) { files.append( tone ); } tools.toolConcat( files )" ) )
		    .isEqualTo( "AudioToolInvalidArgument" );
	}

	@DisplayName( "audioFade, audioGain and audioReverse keep the length" )
	@Test
	void testSameLength() {
		assertThat( seconds( struct( "result = tools.toolFade( tone, 0.5, 0.5, 'linear' )" ) ) ).isWithin( 0.001 ).of( 2.0 );
		assertThat( seconds( struct( "result = tools.toolGain( tone, -6 )" ) ) ).isWithin( 0.001 ).of( 2.0 );
		assertThat( seconds( struct( "result = tools.toolReverse( tone )" ) ) ).isWithin( 0.001 ).of( 2.0 );
	}

	@DisplayName( "audioGain changes the peak by the given decibels" )
	@Test
	void testGainLevel() {
		IStruct made = struct( "result = tools.toolGain( tone, -6 )" );
		variables.put( Key.of( "gained" ), made.get( Key.of( "path" ) ) );
		double	original	= ( ( Number ) struct( "result = audioInfo( tone, true )" ).get( Key.of( "peak" ) ) ).doubleValue();
		double	quieter		= ( ( Number ) struct( "result = audioInfo( gained, true )" ).get( Key.of( "peak" ) ) ).doubleValue();
		assertThat( quieter / original ).isWithin( 0.01 ).of( 0.501 );
	}

	@DisplayName( "audioStretch changes the length and audioPitch does not" )
	@Test
	void testStretchAndPitch() {
		assertThat( seconds( struct( "result = tools.toolStretch( tone, 2 )" ) ) ).isWithin( 0.1 ).of( 1.0 );
		assertThat( seconds( struct( "result = tools.toolPitch( tone, 12 )" ) ) ).isWithin( 0.1 ).of( 2.0 );
	}

	@DisplayName( "audioConvert changes the format, the sample rate and the channels" )
	@Test
	void testConvert() {
		IStruct made = struct( "result = tools.toolConvert( path=tone, format='wav', sampleRate=44100, channels=2 )" );
		assertThat( made.get( Key.of( "sampleRate" ) ) ).isEqualTo( 44100 );
		assertThat( made.get( Key.of( "channels" ) ) ).isEqualTo( 2 );
		assertThat( seconds( made ) ).isWithin( 0.01 ).of( 2.0 );
	}

	@DisplayName( "audioConvert writes MP3 and FLAC by format" )
	@Test
	void testConvertFormats() {
		for ( String format : new String[] { "mp3", "flac" } ) {
			IStruct made = struct( "result = tools.toolConvert( path=tone, format='" + format + "' )" );
			assertThat( made.get( Key.of( "path" ) ).toString() ).endsWith( "." + format );
			assertThat( Files.exists( Path.of( made.get( Key.of( "path" ) ).toString() ) ) ).isTrue();
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// Master
	// ------------------------------------------------------------------------------------------------------------

	@DisplayName( "audioNormalize reaches the target peak" )
	@Test
	void testNormalize() {
		IStruct made = struct( "result = tools.toolNormalize( path=tone, targetDb=-6 )" );
		variables.put( Key.of( "normalized" ), made.get( Key.of( "path" ) ) );
		double peak = ( ( Number ) struct( "result = audioInfo( normalized, true )" ).get( Key.of( "peak" ) ) ).doubleValue();
		assertThat( peak ).isWithin( 0.01 ).of( 0.501 );
	}

	@DisplayName( "audioMaster takes a preset name or a number of LUFS" )
	@Test
	void testMaster() {
		for ( String target : new String[] { "podcast", "-18" } ) {
			IStruct	made	= struct( "result = tools.toolMaster( path=tone, target='" + target + "' )" );
			double	goal	= target.equals( "podcast" ) ? -16.0 : -18.0;
			variables.put( Key.of( "mastered" ), made.get( Key.of( "path" ) ) );
			double lufs = ( ( Number ) struct( "result = audioLoudness( audioLoad( mastered ) )" ).get( Key.of( "integrated" ) ) ).doubleValue();
			assertThat( lufs ).isWithin( 1.0 ).of( goal );
		}
	}

	@DisplayName( "audioEq and audioReverb write a file the same length or longer" )
	@Test
	void testEqAndReverb() {
		assertThat(
		    seconds( struct( "result = tools.toolEq( tone, [ { type : 'highpass', freq : 200 }, { type : 'peak', freq : 1000, gainDb : 3, q : 1 } ] )" ) ) )
		    .isWithin( 0.001 ).of( 2.0 );
		assertThat( seconds( struct( "result = tools.toolReverb( path=tone, room=0.8 )" ) ) ).isAtLeast( 2.0 );
	}

	// ------------------------------------------------------------------------------------------------------------
	// Create
	// ------------------------------------------------------------------------------------------------------------

	@DisplayName( "audioSilence makes a file of silence of the length asked and refuses silly lengths" )
	@Test
	void testSilence() {
		IStruct made = struct( "result = tools.toolSilence( seconds=1.5, channels=1, sampleRate=8000 )" );
		assertThat( seconds( made ) ).isWithin( 0.001 ).of( 1.5 );
		assertThat( made.get( Key.of( "sampleRate" ) ) ).isEqualTo( 8000 );
		assertThat( errorType( "tools.toolSilence( 0 )" ) ).isEqualTo( "AudioToolInvalidArgument" );
		assertThat( errorType( "tools.toolSilence( 100000 )" ) ).isEqualTo( "AudioToolInvalidArgument" );
	}

	@DisplayName( "audioVoices lists the voices of the catalog with the fields an agent needs" )
	@Test
	void testVoices() {
		Array voices = ( Array ) run( "result = tools.toolVoices( language='en_US' )" );
		assertThat( voices ).isNotEmpty();
		IStruct first = ( IStruct ) voices.get( 0 );
		for ( String key : new String[] { "id", "name", "language", "quality", "license", "installed" } ) {
			assertThat( first.containsKey( Key.of( key ) ) ).isTrue();
		}
		assertThat( ( ( Array ) run( "result = tools.toolVoices( language='xx_XX' )" ) ) ).isEmpty();
	}

	@DisplayName( "audioSpeak fails cleanly when the voice is missing and downloads are off" )
	@Test
	void testSpeakWithoutDownloads() {
		useNoDownloads();
		useSpeechHome( dir.resolve( "speech-home" ) );
		assertThrows( BoxRuntimeException.class, () -> runtime.executeSource( "tools.toolSpeak( 'hello', 'en_US-joe-medium' )", context ) );
		assertThat( Files.exists( dir.resolve( "out" ) ) ).isFalse();
	}

	@DisplayName( "audioMux checks both paths before it needs FFmpeg" )
	@Test
	void testMuxGuard() {
		assertThat( errorType( "tools.toolMux( dir & '/missing.mp4', tone )" ) ).isEqualTo( "AudioToolNotFound" );
		assertThat( errorType( "tools.toolMux( tone, '/etc/passwd' )" ) ).isEqualTo( "AudioToolAccessDenied" );
	}

	// ------------------------------------------------------------------------------------------------------------
	// Path guard
	// ------------------------------------------------------------------------------------------------------------

	@DisplayName( "a path outside the allowed folders is denied for reading and writing" )
	@Test
	void testOutsideDenied() {
		assertThat( errorType( "tools.toolInfo( '/etc/passwd' )" ) ).isEqualTo( "AudioToolAccessDenied" );
		assertThat( errorType( "tools.toolTrim( tone, 0, 1, '/etc/bx-audio-test.wav' )" ) ).isEqualTo( "AudioToolAccessDenied" );
		assertThat( errorType( "tools.toolInfo( dir & '/../../etc/passwd' )" ) ).isEqualTo( "AudioToolAccessDenied" );
	}

	@DisplayName( "a symlink cannot lead out of the allowed folders" )
	@Test
	void testSymlinkDenied() throws Exception {
		Path outside = Files.createTempDirectory( "bx-audio-outside" );
		try {
			Files.copy( Path.of( tone ), outside.resolve( "secret.wav" ) );
			Path link = dir.resolve( "link.wav" );
			try {
				Files.createSymbolicLink( link, outside.resolve( "secret.wav" ) );
			} catch ( UnsupportedOperationException | java.io.IOException e ) {
				Assumptions.abort( "symbolic links are not available here" );
			}
			variables.put( Key.of( "link" ), link.toString().replace( '\\', '/' ) );
			assertThat( errorType( "tools.toolInfo( link )" ) ).isEqualTo( "AudioToolAccessDenied" );
		} finally {
			Files.deleteIfExists( outside.resolve( "secret.wav" ) );
			Files.deleteIfExists( outside );
		}
	}

	@DisplayName( "an existing output is never overwritten, nor the input" )
	@Test
	void testNoOverwrite() throws Exception {
		Files.writeString( dir.resolve( "taken.wav" ), "keep me" );
		assertThat( errorType( "tools.toolTrim( tone, 0, 1, dir & '/taken.wav' )" ) ).isEqualTo( "AudioToolExists" );
		assertThat( Files.readString( dir.resolve( "taken.wav" ) ) ).isEqualTo( "keep me" );
		assertThat( errorType( "tools.toolTrim( tone, 0, 1, tone )" ) ).isEqualTo( "AudioToolExists" );
	}

	@DisplayName( "a missing input and an output without an extension are reported" )
	@Test
	void testBadPaths() {
		assertThat( errorType( "tools.toolInfo( dir & '/nothing.wav' )" ) ).isEqualTo( "AudioToolNotFound" );
		assertThat( errorType( "tools.toolTrim( tone, 0, 1, dir & '/noextension' )" ) ).isEqualTo( "AudioToolInvalidArgument" );
	}

	@DisplayName( "with no allowed paths set, the temp folder and the working directory are allowed" )
	@Test
	void testDefaultRoots() {
		Array roots = ( Array ) run( "result = new bxModules.bxaudio.models.tools.AudioTools( {} ).getGuard().getRoots()" );
		// The default output folder lies in the temp folder, so it adds no root
		assertThat( roots ).hasSize( 2 );
		assertThat( roots.get( 1 ).toString().replace( '\\', '/' ) ).isEqualTo( Path.of( "." ).toAbsolutePath().normalize().toString().replace( '\\', '/' ) );
	}

}
