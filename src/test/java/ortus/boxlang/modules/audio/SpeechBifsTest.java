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

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.tts.VoiceCatalog;
import ortus.boxlang.modules.audio.tts.VoiceInfo;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * The text to speech BIFs. Nothing here downloads anything except the one test that is switched on with BX_AUDIO_ONLINE_TESTS=1.
 */
public class SpeechBifsTest extends BaseIntegrationTest {

	private Object run( String source ) {
		runtime.executeSource( source, context );
		return variables.get( Key.of( "result" ) );
	}

	private String message( String source ) {
		return assertThrows( BoxRuntimeException.class, () -> run( source ) ).getMessage();
	}

	@DisplayName( "audioVoices lists the catalog with licenses, filters by language and shows what is installed" )
	@Test
	void testVoices( @TempDir Path home ) {
		useSpeechHome( home );
		Array all = ( Array ) run( "result = audioVoices()" );
		assertThat( all.size() ).isEqualTo( VoiceCatalog.load().all().size() );
		IStruct joe = ( IStruct ) ( ( Array ) run( "result = audioVoices( language='en_US' ).filter( ( v ) => v.name == 'joe' )" ) ).get( 0 );
		assertThat( joe.get( Key.of( "id" ) ) ).isEqualTo( "en_US-joe-medium" );
		assertThat( joe.get( Key.of( "license" ) ) ).isEqualTo( "CC0-1.0" );
		assertThat( joe.get( Key.of( "installed" ) ) ).isEqualTo( false );
		assertThat( ( ( Number ) joe.get( Key.of( "sizeMB" ) ) ).doubleValue() ).isWithin( 1.0 ).of( 63.2 );
		assertThat( ( ( Array ) run( "result = audioVoices( language='en_GB' )" ) ).size() ).isEqualTo( 0 );
		assertThat( run( "result = audioVoices( language='en_US' ).every( ( v ) => v.language == 'en_US' )" ) ).isEqualTo( true );
		assertThat( ( ( Array ) run( "result = audioVoices( installed=true )" ) ).size() ).isEqualTo( 0 );
	}

	@DisplayName( "audioVoices filters by status and license, and says why a voice is not offered" )
	@Test
	void testVoiceStatuses( @TempDir Path home ) {
		useSpeechHome( home );
		int offered = VoiceCatalog.load().all().size();
		assertThat( ( ( Array ) run( "result = audioVoices( status='downloadable' )" ) ).size() ).isEqualTo( offered );
		assertThat( ( ( Array ) run( "result = audioVoices( status='installed' )" ) ).size() ).isEqualTo( 0 );
		Array excluded = ( Array ) run( "result = audioVoices( status='excluded' )" );
		assertThat( excluded.size() ).isEqualTo( VoiceCatalog.load().excluded().size() );
		assertThat( ( ( Array ) run( "result = audioVoices( status='all' )" ) ).size() ).isEqualTo( offered + excluded.size() );
		IStruct ryan = ( IStruct ) ( ( Array ) run( "result = audioVoices( status='excluded' ).filter( ( v ) => v.id == 'en_US-ryan-high' )" ) ).get( 0 );
		assertThat( ryan.get( Key.of( "status" ) ) ).isEqualTo( "excluded" );
		assertThat( ryan.get( Key.of( "reason" ) ).toString() ).contains( "Non-commercial" );
		assertThat( ( ( Array ) run( "result = audioVoices( license='Apache-2.0' )" ) ).size() ).isEqualTo( 1 );
		assertThat( message( "result = audioVoices( status='bogus' )" ) ).contains( "Unknown status [bogus]" );
	}

	@DisplayName( "audioLicenses describes each license and counts the offered voices that use it" )
	@Test
	void testLicenses() {
		Array licenses = ( Array ) run( "result = audioLicenses()" );
		assertThat( licenses.size() ).isAtLeast( 5 );
		IStruct apache = ( IStruct ) run( "result = audioLicenses().filter( ( l ) => l.id == 'Apache-2.0' ).first()" );
		assertThat( apache.get( Key.of( "attribution" ) ) ).isEqualTo( true );
		assertThat( apache.get( Key.of( "commercial" ) ) ).isEqualTo( true );
		assertThat( ( ( Number ) apache.get( Key.of( "voices" ) ) ).intValue() ).isEqualTo( 1 );
		// every offered voice has a license we describe
		assertThat( run( "ids = audioLicenses().map( ( l ) => l.id ); result = audioVoices().every( ( v ) => ids.contains( v.license ) )" ) )
		    .isEqualTo( true );
	}

	@DisplayName( "audioSubtitles formats cues and writes the file, and audioSpeakCues explains what is missing" )
	@Test
	void testSubtitlesAndCues( @TempDir Path home ) throws Exception {
		useSpeechHome( home );
		variables.put( Key.of( "target" ), home.resolve( "out/captions.vtt" ).toString() );
		String text = ( String ) run(
		    "result = audioSubtitles( [ { start: 0, end: 1.5, text: 'Hello' }, { start: 2, end: 3, text: 'World' } ], 'vtt', target, 10 )" );
		assertThat( text ).startsWith( "WEBVTT\n\n00:00:10.000 --> 00:00:11.500\nHello" );
		assertThat( java.nio.file.Files.readString( home.resolve( "out/captions.vtt" ) ) ).isEqualTo( text );
		assertThat( message( "result = audioSubtitles( [ { start: 0, text: 'x' } ] )" ) ).contains( "Each cue must be a struct" );
		assertThat( message( "result = audioSubtitles( [], 'ass' )" ) ).contains( "Unknown subtitle format" );
		assertThat( message( "result = audioSpeakCues( text='Hello there.', voice='joe', autoInstall=false )" ) ).contains( "bx-audio install engine" );
		assertThat( message( "result = audioSpeakCues( text='Hello there.', voice='joe', split='word', autoInstall=false )" ) ).contains( "split must be" );
		assertThat( message( "result = audioSpeak( text='Hello.', voice='joe', autoInstall=false, speed=9 )" ) )
		    .containsMatch( "bx-audio install engine|speed must be" );
	}

	@DisplayName( "audioSpeechStatus says where everything lives and that nothing is installed yet" )
	@Test
	void testStatus( @TempDir Path home ) {
		useSpeechHome( home );
		IStruct status = ( IStruct ) run( "result = audioSpeechStatus()" );
		assertThat( status.get( Key.of( "home" ) ) ).isEqualTo( home.toAbsolutePath().toString() );
		assertThat( status.get( Key.of( "engineInstalled" ) ) ).isEqualTo( false );
		assertThat( ( ( Number ) status.get( Key.of( "engineSizeMB" ) ) ).doubleValue() ).isGreaterThan( 100.0 );
		assertThat( ( ( Array ) status.get( Key.of( "voices" ) ) ).size() ).isEqualTo( 0 );
	}

	@DisplayName( "audioSpeak explains what is missing and how to install it when downloads are not allowed" )
	@Test
	void testSpeakWithoutInstall( @TempDir Path home ) {
		useSpeechHome( home );
		String message = message( "audioSpeak( text='Hello', voice='joe', autoInstall=false )" );
		assertThat( message ).contains( "engine is not installed" );
		assertThat( message ).contains( "bx-audio install engine" );
		assertThat( message( "audioSpeak( text='Hello', voice='nope', autoInstall=false )" ) ).contains( "Unknown voice [nope]" );
		assertThat( message( "audioSpeak( text='  ', voice='joe', autoInstall=false )" ) ).contains( "nothing to say" );
		// a restricted voice is not in the catalog, so it cannot be asked for
		assertThat( message( "audioSpeak( text='Hello', voice='lessac', autoInstall=false )" ) ).contains( "The voice [lessac] is not offered" );
	}

	@DisplayName( "A setting can turn automatic downloads off for every call" )
	@Test
	void testAutoInstallSetting( @TempDir Path home ) {
		useSpeechHome( home );
		moduleRecord.settings.put( Key.of( "autoInstall" ), false );
		try {
			assertThat( message( "audioSpeak( text='Hello', voice='joe' )" ) ).contains( "engine is not installed" );
		} finally {
			moduleRecord.settings.put( Key.of( "autoInstall" ), true );
		}
	}

	@DisplayName( "audioVoiceInstall and audioVoiceRemove reject voices that are not in the catalog, and removing what is not installed says so" )
	@Test
	void testInstallRemoveErrors( @TempDir Path home ) {
		useSpeechHome( home );
		assertThat( message( "audioVoiceInstall( 'nope' )" ) ).contains( "Unknown voice [nope]" );
		assertThat( message( "audioVoiceRemove( 'nope' )" ) ).contains( "Unknown voice [nope]" );
		assertThat( run( "result = audioVoiceRemove( 'joe' )" ) ).isEqualTo( false );
		assertThat( run( "result = audioVoiceRemove( 'engine' )" ) ).isEqualTo( false );
	}

	@DisplayName( "A voice on disk at the size the catalog says shows as installed, and audioVoiceRemove deletes it" )
	@Test
	void testInstalledVoice( @TempDir Path home ) throws Exception {
		useSpeechHome( home );
		VoiceInfo	joe	= VoiceCatalog.load().find( "joe" );
		Path		dir	= home.resolve( "voices" ).resolve( joe.id() );
		Files.createDirectories( dir );
		// sparse files of the right size, so no 63 MB is written
		try ( RandomAccessFile model = new RandomAccessFile( dir.resolve( joe.id() + ".onnx" ).toFile(), "rw" );
		    RandomAccessFile config = new RandomAccessFile( dir.resolve( joe.id() + ".onnx.json" ).toFile(), "rw" ) ) {
			model.setLength( joe.model().size() );
			config.setLength( joe.config().size() );
		}
		Array installed = ( Array ) run( "result = audioVoices( installed=true )" );
		assertThat( installed.size() ).isEqualTo( 1 );
		assertThat( ( ( IStruct ) installed.get( 0 ) ).get( Key.of( "id" ) ) ).isEqualTo( "en_US-joe-medium" );
		assertThat( ( ( Array ) ( ( IStruct ) run( "result = audioSpeechStatus()" ) ).get( Key.of( "voices" ) ) ).get( 0 ) ).isEqualTo( "en_US-joe-medium" );

		assertThat( run( "result = audioVoiceRemove( 'joe' )" ) ).isEqualTo( true );
		assertThat( Files.exists( dir ) ).isFalse();
		assertThat( ( ( Array ) run( "result = audioVoices( installed=true )" ) ).size() ).isEqualTo( 0 );
	}

	@DisplayName( "Online: the engine and a voice are downloaded, and speech comes out" )
	@EnabledIfEnvironmentVariable( named = "BX_AUDIO_ONLINE_TESTS", matches = "1" )
	@Test
	void testSpeakOnline( @TempDir Path home ) {
		useSpeechHome( home );
		variables.put( Key.of( "events" ), new Array() );
		runtime.executeSource(
		    """
		    audio = audioSpeak( text="Meet BoxLang. Four hundred and twenty eight tests passed.", voice="joe", onProgress=( name, done, total ) => {
		    	events.append( name );
		    } );
		    result = [ audio.getChannels(), audio.getSampleRate(), audio.getDurationSeconds(), audio.getRms(), audio.getPeak() ];
		    """,
		    context );
		Array values = ( Array ) variables.get( result );
		assertThat( values.get( 0 ) ).isEqualTo( 1 );
		assertThat( values.get( 1 ) ).isEqualTo( 22050 );
		// about four seconds of speech for that sentence
		assertThat( ( ( Number ) values.get( 2 ) ).doubleValue() ).isWithin( 1.5 ).of( 4.0 );
		assertThat( ( ( Number ) values.get( 3 ) ).doubleValue() ).isGreaterThan( 0.02 );
		assertThat( ( ( Number ) values.get( 4 ) ).doubleValue() ).isLessThan( 1.0 );
		// it downloaded the engine jars and the voice, and told the callback about both
		Array events = ( Array ) variables.get( Key.of( "events" ) );
		assertThat( events.stream().anyMatch( e -> e.toString().startsWith( "onnxruntime" ) ) ).isTrue();
		assertThat( events.contains( "en_US-joe-medium" ) ).isTrue();
		// the second call needs no download
		variables.put( Key.of( "events" ), new Array() );
		runtime.executeSource( "audioSpeak( text='Again.', voice='joe', autoInstall=false, onProgress=( n, d, t ) => events.append( n ) )", context );
		assertThat( ( ( Array ) variables.get( Key.of( "events" ) ) ).size() ).isEqualTo( 0 );
	}

	@DisplayName( "audioVoices says what each license means for a product you sell, and filters by it" )
	@Test
	void testVoiceUsage( @TempDir Path home ) {
		useSpeechHome( home );
		IStruct joe = ( IStruct ) ( ( Array ) run( "result = audioVoices().filter( ( v ) => v.name == 'joe' )" ) ).get( 0 );
		assertThat( joe.get( Key.of( "usage" ) ) ).isEqualTo( "permissive" );
		assertThat( joe.get( Key.of( "commercial" ) ) ).isEqualTo( true );
		assertThat( joe.get( Key.of( "credit" ) ) ).isEqualTo( "" );
		IStruct sam = ( IStruct ) ( ( Array ) run( "result = audioVoices().filter( ( v ) => v.name == 'sam' )" ) ).get( 0 );
		assertThat( ( String ) sam.get( Key.of( "credit" ) ) ).contains( "Apache License 2.0" );
		assertThat( ( ( Array ) run( "result = audioVoices( usage='permissive' )" ) ).size() ).isEqualTo( VoiceCatalog.load().all().size() );
		assertThat( ( ( Array ) run( "result = audioVoices( usage='attribution,sharealike' )" ) ).size() ).isEqualTo( 0 );
		Array copyleft = ( Array ) run( "result = audioVoices( status='excluded', usage='copyleft' )" );
		assertThat( copyleft.size() ).isEqualTo( 2 );
		IStruct tom = ( IStruct ) run( "result = audioVoices( status='excluded', usage='copyleft' ).filter( ( v ) => v.name == 'tom' ).first()" );
		assertThat( tom.get( Key.of( "commercial" ) ) ).isEqualTo( false );
		assertThat( tom.get( Key.of( "usage" ) ) ).isEqualTo( "copyleft" );
		assertThat(
		    run( "result = audioVoices( status='all' ).every( ( v ) => v.keyExists( 'usage' ) && v.keyExists( 'commercial' ) && v.keyExists( 'credit' ) )" ) )
		    .isEqualTo( true );
		assertThat( message( "result = audioVoices( usage='free' )" ) ).contains( "Unknown usage [free]" );
	}

	@DisplayName( "audioCredits writes the credit text for one voice, a list or an array, and refuses voices that cannot be used" )
	@Test
	void testCredits( @TempDir Path home ) {
		useSpeechHome( home );
		assertThat( run( "result = audioCredits( 'joe' )" ) ).isEqualTo( "No credit is required for joe (CC0-1.0).\n" );
		String text = ( String ) run( "result = audioCredits( [ 'sam', 'joe' ] )" );
		assertThat( text ).startsWith( "Voices\n\n- Voice \"sam\" (en_US)" );
		assertThat( text ).contains( "No credit is required for joe (CC0-1.0)." );
		assertThat( run( "result = audioCredits( 'joe, en_US-joe-medium' )" ) ).isEqualTo( "No credit is required for joe (CC0-1.0), joe (CC0-1.0).\n" );
		assertThat( message( "result = audioCredits( 'nobody' )" ) ).contains( "Unknown voice [nobody]" );
		assertThat( message( "result = audioCredits( 'fr_FR-tom-medium' )" ) ).contains( "is not offered" );
		assertThat( message( "result = audioCredits( [] )" ) ).contains( "Name at least one voice" );
	}

}
