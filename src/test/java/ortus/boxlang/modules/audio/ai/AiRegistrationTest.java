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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.BaseIntegrationTest;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;

/**
 * The audio tools reach bx-ai through its global tool registry. These tests load the real bx-ai module, which the Gradle task unzipBxAi
 * puts in build/test-modules/bxai, and check the tools register, describe themselves and run through the registry the way an agent's
 * tool call does. They run in order, because the first one needs bx-ai to be absent. Without the module folder they skip themselves.
 */
@TestMethodOrder( MethodOrderer.OrderAnnotation.class )
public class AiRegistrationTest extends BaseIntegrationTest {

	static final Path			BXAI		= Path.of( "build/test-modules/bxai" ).toAbsolutePath();
	static final Key			AI			= Key.of( "bxai" );

	private static final String	REGISTERED	= "aiToolRegistry().getKeys().filter( ( k ) => k.endsWith( '@bxaudio' ) )";

	/**
	 * Tell the module every module is active, which is when it tries to register its tools
	 */
	private void announceActivated() {
		runtime.getInterceptorService().announce( Key.of( "afterModuleActivations" ), new ortus.boxlang.runtime.types.Struct() );
	}

	/**
	 * Call a function of the module's ModuleConfig
	 */
	private Object callConfig( String name ) {
		return ( ( ortus.boxlang.runtime.modules.BoxModuleConfig ) moduleRecord.moduleConfig ).getBxClass()
		    .dereferenceAndInvoke( runtime.getRuntimeContext(), Key.of( name ), new Object[] {}, false );
	}

	private void assumeBxAi() {
		Assumptions.assumeTrue( Files.exists( BXAI.resolve( "ModuleConfig.bx" ) ), "bx-ai is not unzipped in build/test-modules, run unzipBxAi" );
	}

	private Object run( String source ) {
		runtime.executeSource( source, context );
		return variables.get( result );
	}

	@DisplayName( "without bx-ai the module loads and registering does nothing" )
	@Test
	@Order( 1 )
	void testWithoutBxAi() {
		assertThat( runtime.getModuleService().hasModule( AI ) ).isFalse();
		announceActivated();
		assertThat( callConfig( "registerAiTools" ) ).isEqualTo( false );
		assertThat( moduleRecord.settings.containsKey( Key.of( "ai" ) ) ).isTrue();
		// The audio functions are still all there
		assertThat( run( "result = audioSilence( 1 ).getChannels()" ) ).isEqualTo( 2 );
	}

	@DisplayName( "once bx-ai is active the tools register under the bxaudio module name" )
	@Test
	@Order( 2 )
	void testRegisters() {
		assumeBxAi();
		loadBxAi();
		announceActivated();

		Array keys = ( Array ) run( "result = " + REGISTERED );
		assertThat( keys ).hasSize( 26 );
		// getByModule hands an agent every audio tool at once
		assertThat( ( ( Array ) run( "result = aiToolRegistry().getByModule( 'bxaudio' )" ) ) ).hasSize( 26 );
		for ( String key : new String[] { "audioInfo", "audioTrim", "audioConcat", "audioMaster", "audioSpeak", "audioVoices", "audioMux", "videoInfo" } ) {
			assertThat( keys ).contains( key + "@bxaudio" );
		}
	}

	@DisplayName( "registering again leaves the registry as it was" )
	@Test
	@Order( 3 )
	void testIdempotent() {
		assumeBxAi();
		announceActivated();
		announceActivated();
		assertThat( ( ( Array ) run( "result = " + REGISTERED ) ) ).hasSize( 26 );
	}

	@DisplayName( "every tool has a description and every argument has its own description" )
	@Test
	@Order( 4 )
	void testSchemas() {
		assumeBxAi();
		runtime.executeSource(
		    """
		    problems = [];
		    aiToolRegistry().getKeys().filter( ( k ) => k.endsWith( '@bxaudio' ) ).each( ( k ) => {
		    	var tool = aiToolRegistry().get( k );
		    	if ( tool.getDescription().len() < 20 ) { problems.append( k & ": description" ); }
		    	var props = tool.getArgumentsSchema().properties;
		    	props.each( ( name, def ) => {
		    		// A missing hint falls back to the argument name itself
		    		if ( def.description == name ) { problems.append( k & "." & name & ": no description" ); }
		    	} );
		    } );
		    result = problems;
		    """,
		    context );
		assertThat( ( ( Array ) variables.get( result ) ) ).isEmpty();
	}

	@DisplayName( "the schema types match what a model must send" )
	@Test
	@Order( 5 )
	void testSchemaTypes() {
		assumeBxAi();
		IStruct	trim	= ( IStruct ) run( "result = aiToolRegistry().get( 'audioTrim@bxaudio' ).getArgumentsSchema()" );
		IStruct	props	= ( IStruct ) trim.get( Key.of( "properties" ) );
		assertThat( ( ( IStruct ) props.get( Key.of( "path" ) ) ).get( Key.of( "type" ) ) ).isEqualTo( "string" );
		assertThat( ( ( IStruct ) props.get( Key.of( "start" ) ) ).get( Key.of( "type" ) ) ).isEqualTo( "number" );
		assertThat( ( ( Array ) trim.get( Key.of( "required" ) ) ) ).containsExactly( "path" );

		IStruct	eq		= ( IStruct ) run( "result = aiToolRegistry().get( 'audioEq@bxaudio' ).getArgumentsSchema()" );
		IStruct	eqProps	= ( IStruct ) eq.get( Key.of( "properties" ) );
		assertThat( ( ( IStruct ) eqProps.get( Key.of( "bands" ) ) ).get( Key.of( "type" ) ) ).isEqualTo( "array" );
		IStruct	concat		= ( IStruct ) run( "result = aiToolRegistry().get( 'audioConcat@bxaudio' ).getArgumentsSchema()" );
		IStruct	concatProps	= ( IStruct ) concat.get( Key.of( "properties" ) );
		assertThat( ( ( IStruct ) concatProps.get( Key.of( "paths" ) ) ).get( Key.of( "type" ) ) ).isEqualTo( "array" );
	}

	@DisplayName( "a tool called through the registry runs and answers as JSON, like an agent's tool call" )
	@Test
	@Order( 6 )
	void testInvokeThroughRegistry( @TempDir Path dir ) {
		assumeBxAi();
		variables.put( Key.of( "tone" ), dir.resolve( "tone.wav" ).toString().replace( '\\', '/' ) );
		runtime.executeSource(
		    """
		    audioSave( audioOsc( shape="sine", freq=440, seconds=2, sampleRate=22050 ), tone );
		    info = jsonDeserialize( aiToolRegistry().get( 'audioInfo@bxaudio' ).invoke( { path : tone } ) );
		    trimmed = jsonDeserialize( aiToolRegistry().get( 'audioTrim@bxaudio' ).invoke( { path : tone, start : 0.5, end : 1.5 } ) );
		    result = [ info.seconds, trimmed.seconds, fileExists( trimmed.path ) ];
		    """,
		    context );
		Array answers = ( Array ) variables.get( result );
		assertThat( ( ( Number ) answers.get( 0 ) ).doubleValue() ).isWithin( 0.001 ).of( 2.0 );
		assertThat( ( ( Number ) answers.get( 1 ) ).doubleValue() ).isWithin( 0.001 ).of( 1.0 );
		assertThat( answers.get( 2 ) ).isEqualTo( true );
	}

	@DisplayName( "the default path policy holds through the registry" )
	@Test
	@Order( 7 )
	void testGuardThroughRegistry() {
		assumeBxAi();
		org.junit.jupiter.api.Assertions.assertThrows( Exception.class,
		    () -> runtime.executeSource( "aiToolRegistry().get( 'audioInfo@bxaudio' ).invoke( { path : '/etc/passwd' } )", context ) );
	}

	@DisplayName( "the ai.enabled setting switches registration off" )
	@Test
	@Order( 8 )
	void testKillSwitch() {
		assumeBxAi();
		IStruct ai = ( IStruct ) moduleRecord.settings.get( Key.of( "ai" ) );
		run( "aiToolRegistry().unregisterByModule( 'bxaudio' ); result = true" );
		ai.put( Key.of( "enabled" ), false );
		try {
			announceActivated();
			assertThat( ( ( Array ) run( "result = " + REGISTERED ) ) ).isEmpty();
		} finally {
			ai.put( Key.of( "enabled" ), true );
		}
		announceActivated();
		assertThat( ( ( Array ) run( "result = " + REGISTERED ) ) ).hasSize( 26 );
	}

	@DisplayName( "the unload step takes the tools out of the registry" )
	@Test
	@Order( 9 )
	void testUnregisters() {
		assumeBxAi();
		callConfig( "unregisterAiTools" );
		assertThat( ( ( Array ) run( "result = " + REGISTERED ) ) ).isEmpty();
		// And the tools come back, so nothing after this test depends on the order
		assertThat( callConfig( "registerAiTools" ) ).isEqualTo( true );
		assertThat( ( ( Array ) run( "result = " + REGISTERED ) ) ).hasSize( 26 );
	}

	@AfterAll
	static void unloadBxAi() {
		if ( runtime != null && runtime.getModuleService().hasModule( AI ) ) {
			runtime.getModuleService().getRegistry().remove( AI );
		}
	}

	private void loadBxAi() {
		if ( runtime.getModuleService().hasModule( AI ) ) {
			return;
		}
		ModuleRecord bxai = new ModuleRecord( BXAI.toString() );
		runtime.getModuleService().getRegistry().put( AI, bxai );
		bxai.loadDescriptor( runtime.getRuntimeContext() ).register( runtime.getRuntimeContext() ).activate( runtime.getRuntimeContext() );
	}

}
