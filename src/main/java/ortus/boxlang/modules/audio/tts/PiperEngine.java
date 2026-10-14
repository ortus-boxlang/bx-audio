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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Speaks text with the Voices library (Piper models on ONNX Runtime). The library is not a dependency of this module: its jars are
 * downloaded on first use by {@link EngineInstaller} and loaded here in a class loader of their own, then driven by reflection. That keeps
 * 140 MB out of the module and keeps the library's own dependencies from clashing with the rest of the runtime.
 * <p>
 * ONNX Runtime loads a native library, and a JVM can load one only through a single class loader, so the loader and the engines are
 * created once per JVM and shared.
 */
public final class PiperEngine {

	private static PiperEngine			instance;

	private final ClassLoader			loader;
	/**
	 * One Chorus per language, since a Chorus owns the phonemizer of one language
	 */
	private final Map<String, Object>	choruses	= new HashMap<>();

	private PiperEngine( ClassLoader loader ) {
		this.loader = loader;
	}

	/**
	 * The engine of this JVM, created on the first call from the jars of the installer
	 */
	public static synchronized PiperEngine get( EngineInstaller installer ) {
		if ( instance == null ) {
			URL[] urls = installer.classpath();
			instance = new PiperEngine( new URLClassLoader( urls, ClassLoader.getPlatformClassLoader() ) );
		}
		return instance;
	}

	/**
	 * Speak a text
	 *
	 * @param voice The voice, which must be installed in the store
	 * @param store Where the voices are
	 * @param text  What to say
	 *
	 * @return Mono audio at the voice's sample rate
	 */
	public synchronized AudioBuffer speak( VoiceInfo voice, VoiceStore store, String text ) {
		try {
			Object	chorus		= chorusFor( voice.language(), store.getVoicesDir() );
			Object	model		= model( voice );
			Object	piperVoice	= call( chorus, "voice", model );
			Object	audio		= call( piperVoice, "say", text );
			float[]	samples		= ( float[] ) call( audio, "getSamples" );
			int		rate		= ( Integer ) call( audio, "getSampleRate" );
			return new AudioBuffer( new float[][] { samples }, rate );
		} catch ( InvocationTargetException e ) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			throw new BoxRuntimeException( "The voice [" + voice.id() + "] could not speak that: " + cause, cause );
		} catch ( ReflectiveOperationException e ) {
			throw new BoxRuntimeException( "The text to speech engine could not be started: " + e, e );
		}
	}

	private Object chorusFor( String language, Path voicesDir ) throws ReflectiveOperationException {
		if ( !language.equals( "en_US" ) ) {
			throw new BoxRuntimeException( "Only US English voices are supported so far, not [" + language + "]" );
		}
		Object chorus = this.choruses.get( language );
		if ( chorus == null ) {
			Object	dictionary	= callStatic( "org.pitest.voices.us.EnUsDictionary", "en_us" );
			Object	config		= callStatic( "org.pitest.voices.ChorusConfig", "chorusConfig", dictionary );
			config = call( config, "withBase", voicesDir );
			Class<?>		chorusClass	= this.loader.loadClass( "org.pitest.voices.Chorus" );
			Constructor<?>	constructor	= chorusClass.getConstructor( this.loader.loadClass( "org.pitest.voices.ChorusConfig" ) );
			chorus = constructor.newInstance( config );
			this.choruses.put( language, chorus );
		}
		return chorus;
	}

	/**
	 * A FileModel: the Voices library finds the voice at {@code <base>/<location>/<name>.onnx}, which is how VoiceStore lays it out
	 */
	private Object model( VoiceInfo voice ) throws ReflectiveOperationException {
		Class<?>		fetcherType		= this.loader.loadClass( "org.pitest.voices.download.ModelFetcher" );
		Object			fetcher			= Proxy.newProxyInstance( this.loader, new Class<?>[] { fetcherType }, ( proxy, method, args ) -> {
											throw new java.io.IOException( "The voice [" + voice.id() + "] is not installed" );
										} );
		Class<?>		languageType	= this.loader.loadClass( "org.pitest.voices.Language" );
		@SuppressWarnings( { "unchecked", "rawtypes" } )
		Object			language		= Enum.valueOf( ( Class<Enum> ) languageType, voice.language() );
		Object			handler			= callStatic( "org.pitest.voices.piper.PiperHandler", "piper" );
		Constructor<?>	constructor		= this.loader.loadClass( "org.pitest.voices.download.FileModel" ).getConstructor(
		    this.loader.loadClass( "org.pitest.voices.VoiceHandler" ), String.class, String.class, languageType, fetcherType, float.class );
		return constructor.newInstance( handler, voice.id(), voice.id(), language, fetcher, 1.0f );
	}

	/**
	 * Call an instance method by name and argument count. Voices types are not visible to this module, so the parameter types cannot be
	 * named.
	 */
	private Object call( Object target, String method, Object... args ) throws ReflectiveOperationException {
		return invoke( target.getClass(), target, method, args );
	}

	/**
	 * Call a static method of a class from the engine's class loader
	 */
	private Object callStatic( String className, String method, Object... args ) throws ReflectiveOperationException {
		return invoke( this.loader.loadClass( className ), null, method, args );
	}

	private static Object invoke( Class<?> type, Object target, String name, Object[] args ) throws ReflectiveOperationException {
		for ( Method method : type.getMethods() ) {
			if ( method.getName().equals( name ) && method.getParameterCount() == args.length && fits( method.getParameterTypes(), args ) ) {
				return method.invoke( target, args );
			}
		}
		throw new NoSuchMethodException( type.getName() + "." + name + " with " + args.length + " arguments" );
	}

	private static boolean fits( Class<?>[] types, Object[] args ) {
		for ( int i = 0; i < types.length; i++ ) {
			if ( !types[ i ].isInstance( args[ i ] ) ) {
				return false;
			}
		}
		return true;
	}

}
