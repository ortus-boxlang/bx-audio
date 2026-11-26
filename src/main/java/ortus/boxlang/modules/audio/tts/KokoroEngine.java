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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * Speaks with Kokoro, a text to speech model of 82 million parameters that runs on ONNX Runtime, which the engine installer already
 * downloads for the Piper voices. Only Kokoro v1.0 is supported: its model takes the phoneme tokens, a style vector and a speed, and gives
 * 24 kHz audio.
 * <p>
 * The text becomes phonemes with the same dictionary the Piper voices use ({@link PiperEngine#phonemize}), then {@link KokoroPhonemes} puts
 * them in the spelling Kokoro was trained on. The voice is one row of the voices pack, picked by how many tokens there are, as the reference
 * implementation (kokoro-onnx) does. Silence at the start and end of each sentence is trimmed and sentences are joined with a short pause,
 * which is what the reference does too.
 * <p>
 * ONNX Runtime is driven by reflection, like the Voices library, since nothing here compiles against it.
 */
public final class KokoroEngine implements SpeechEngine {

	/**
	 * What the model produces
	 */
	public static final int							SAMPLE_RATE		= 24000;

	/**
	 * The pause between two sentences, in seconds: the reference's default
	 */
	private static final double						SENTENCE_PAUSE	= 0.25;

	/**
	 * The engine of this JVM, created on first use and cleared by {@link #shutdown()}
	 */
	private static KokoroEngine						instance;

	/**
	 * The Voices engine, which provides ONNX Runtime and the phonemizer
	 */
	private final PiperEngine						piper;

	/**
	 * Where the voices are stored, which the phonemizer needs for its dictionaries
	 */
	private final Path								voicesDir;

	/**
	 * The ONNX sessions opened so far, by model path. The model is large, so it is loaded once.
	 */
	private final Map<String, Object>				sessions		= new HashMap<>();

	/**
	 * The ONNX Runtime environment, created on the first run
	 */
	private Object									environment;

	/**
	 * The styles of the voices read so far, by voice id
	 */
	private final Map<String, KokoroVoices.Styles>	styles			= new HashMap<>();

	/**
	 * Use {@link #get(PiperEngine, Path)}
	 *
	 * @param piper     The Voices engine, which provides ONNX Runtime and the phonemizer
	 * @param voicesDir Where the voices are
	 */
	private KokoroEngine( PiperEngine piper, Path voicesDir ) {
		this.piper		= piper;
		this.voicesDir	= voicesDir;
	}

	/**
	 * The engine of this JVM
	 *
	 * @param piper     The Voices engine, which provides ONNX Runtime and the phonemizer
	 * @param voicesDir Where the voices are
	 *
	 * @return The engine, created on first use, and created again when the Voices engine has changed
	 */
	public static synchronized KokoroEngine get( PiperEngine piper, Path voicesDir ) {
		if ( instance == null || instance.piper != piper ) {
			instance = new KokoroEngine( piper, voicesDir );
		}
		return instance;
	}

	/**
	 * Close the model, if one was loaded. Called when the module unloads.
	 */
	public static synchronized void shutdown() {
		if ( instance != null ) {
			for ( Object session : instance.sessions.values() ) {
				if ( session instanceof AutoCloseable closeable ) {
					try {
						closeable.close();
					} catch ( Exception ignored ) {
						// the engine is going away either way
					}
				}
			}
			instance.sessions.clear();
			instance.styles.clear();
			instance = null;
		}
	}

	/**
	 * Speak a text with a Kokoro voice. The text is split into sentences, each is phonemized, mapped to the phonemes Kokoro was trained on, run
	 * through the model, trimmed, and the sentences are joined with a short pause.
	 *
	 * @param voice The Kokoro voice
	 * @param store Where its model and voices pack are
	 * @param text  What to say
	 * @param speed 1 is normal, above 1 is faster
	 *
	 * @return The speech, mono at {@link #SAMPLE_RATE}
	 *
	 * @throws AudioException when the voice has nothing it can say in the text, or the model fails
	 */
	@Override
	public synchronized AudioBuffer speak( VoiceInfo voice, VoiceStore store, String text, double speed ) {
		try {
			KokoroPhonemes		phonemes	= KokoroPhonemes.get();
			KokoroVoices.Styles	styles		= this.styles.computeIfAbsent( voice.id(), id -> KokoroVoices.read( store.configFile( voice ), id ) );
			List<float[]>		pieces		= new ArrayList<>();
			for ( String sentence : sentences( text ) ) {
				// Spanish has its own rules in this module, English goes through the Voices phonemizer
				String mapped = voice.language().startsWith( "es" )
				    ? phonemes.known( SpanishPhonemizer.phonemize( sentence, Speech.latinAmericanAccent() ) )
				    : phonemes.map( voice.language(), this.piper.phonemize( voice.language(), this.voicesDir, sentence ) );
				for ( String chunk : chunks( mapped ) ) {
					long[] tokens = phonemes.tokens( chunk );
					if ( tokens.length == 0 ) {
						continue;
					}
					pieces.add( trim( run( store.modelFile( voice ), tokens, styles.forTokens( tokens.length ), ( float ) speed ) ) );
				}
			}
			if ( pieces.isEmpty() ) {
				throw new AudioException( AudioException.SPEECH_FAILED, "The voice [" + voice.id() + "] has nothing it can say in that text" );
			}
			return new AudioBuffer( new float[][] { join( pieces ) }, SAMPLE_RATE );
		} catch ( InvocationTargetException e ) {
			Throwable cause = e.getCause() == null ? e : e.getCause();
			throw new AudioException( AudioException.SPEECH_FAILED, "The voice [" + voice.id() + "] could not speak that: " + cause, cause );
		} catch ( ReflectiveOperationException e ) {
			throw new AudioException( AudioException.SPEECH_FAILED, "Kokoro could not be started: " + e, e );
		}
	}

	/**
	 * A text as sentences, so that each is spoken with its own phrasing and none is longer than the model takes
	 *
	 * @param text Any text, with or without line breaks
	 *
	 * @return The sentences, in order, none blank
	 */
	static List<String> sentences( String text ) {
		List<String> result = new ArrayList<>();
		for ( String line : text.split( "\\R+" ) ) {
			for ( String sentence : line.trim().split( "(?<=[.!?])\\s+" ) ) {
				if ( !sentence.isBlank() ) {
					result.add( sentence.trim() );
				}
			}
		}
		return result;
	}

	/**
	 * Phonemes as pieces of at most the model's limit, cut at a space, or at the limit when a word is longer than that
	 *
	 * @param phonemes The mapped phonemes of one sentence
	 *
	 * @return The pieces, in order, none longer than the model takes
	 */
	static List<String> chunks( String phonemes ) {
		List<String>	result	= new ArrayList<>();
		String			rest	= phonemes.trim();
		int				limit	= KokoroPhonemes.maxTokens() - 10;
		while ( rest.length() > limit ) {
			int cut = rest.lastIndexOf( ' ', limit );
			cut = cut <= 0 ? limit : cut;
			result.add( rest.substring( 0, cut ).trim() );
			rest = rest.substring( cut ).trim();
		}
		if ( !rest.isEmpty() ) {
			result.add( rest );
		}
		return result;
	}

	/**
	 * Run the model: tokens, style and speed in, audio out
	 *
	 * @param model  The model file
	 * @param tokens The tokens of one piece of speech, without the padding
	 * @param style  The style of the voice for that many tokens
	 * @param speed  1 is normal, above 1 is faster
	 *
	 * @return The audio samples at {@link #SAMPLE_RATE}
	 *
	 * @throws ReflectiveOperationException when ONNX Runtime cannot be driven, or the model fails
	 */
	private float[] run( Path model, long[] tokens, float[] style, float speed ) throws ReflectiveOperationException {
		ClassLoader	loader		= this.piper.loader();
		Class<?>	envType		= loader.loadClass( "ai.onnxruntime.OrtEnvironment" );
		Class<?>	tensorType	= loader.loadClass( "ai.onnxruntime.OnnxTensor" );
		if ( this.environment == null ) {
			this.environment = envType.getMethod( "getEnvironment" ).invoke( null );
		}
		Object session = this.sessions.get( model.toString() );
		if ( session == null ) {
			Class<?>	optionsType	= loader.loadClass( "ai.onnxruntime.OrtSession$SessionOptions" );
			Object		options		= optionsType.getConstructor().newInstance();
			session = envType.getMethod( "createSession", String.class, optionsType ).invoke( this.environment, model.toString(), options );
			this.sessions.put( model.toString(), session );
		}
		// v1.0 names the token input "tokens"; some exports call it "input_ids"
		@SuppressWarnings( "unchecked" )
		Set<String>	inputNames	= ( Set<String> ) session.getClass().getMethod( "getInputNames" ).invoke( session );
		String		tokenName	= inputNames.contains( "input_ids" ) ? "input_ids" : "tokens";
		Method		ofLongs		= tensorType.getMethod( "createTensor", envType, LongBuffer.class, long[].class );
		Method		ofFloats	= tensorType.getMethod( "createTensor", envType, FloatBuffer.class, long[].class );
		// the model wants a zero before and after the tokens
		long[]		padded		= new long[ tokens.length + 2 ];
		System.arraycopy( tokens, 0, padded, 1, tokens.length );
		Map<String, Object>	inputs	= new HashMap<>();
		List<AutoCloseable>	opened	= new ArrayList<>();
		try {
			Object tokenTensor = ofLongs.invoke( null, this.environment, LongBuffer.wrap( padded ), new long[] { 1, padded.length } );
			opened.add( ( AutoCloseable ) tokenTensor );
			Object styleTensor = ofFloats.invoke( null, this.environment, FloatBuffer.wrap( style ), new long[] { 1, style.length } );
			opened.add( ( AutoCloseable ) styleTensor );
			Object speedTensor = ofFloats.invoke( null, this.environment, FloatBuffer.wrap( new float[] { speed } ), new long[] { 1 } );
			opened.add( ( AutoCloseable ) speedTensor );
			inputs.put( tokenName, tokenTensor );
			inputs.put( "style", styleTensor );
			inputs.put( "speed", speedTensor );
			Object result = session.getClass().getMethod( "run", Map.class ).invoke( session, inputs );
			opened.add( ( AutoCloseable ) result );
			Object	value	= result.getClass().getMethod( "get", int.class ).invoke( result, 0 );
			Object	audio	= value.getClass().getMethod( "getValue" ).invoke( value );
			return audio instanceof float[][] rows ? rows[ 0 ] : ( float[] ) audio;
		} finally {
			for ( AutoCloseable each : opened ) {
				try {
					each.close();
				} catch ( Exception ignored ) {
					// the native memory is freed when the engine goes
				}
			}
		}
	}

	/**
	 * Cut the silence from both ends, the way librosa's {@code trim} does with its defaults: frames of 2048 samples every 512, and everything
	 * quieter than 60 dB below the loudest frame is silence.
	 *
	 * @param audio The samples of one piece of speech
	 *
	 * @return The samples between the first and the last frame that is not silence, or the audio unchanged when it is all silent
	 */
	static float[] trim( float[] audio ) {
		int			frame	= 2048;
		int			hop		= 512;
		int			frames	= 1 + audio.length / hop;
		double[]	rms		= new double[ frames ];
		double		loudest	= 0;
		for ( int f = 0; f < frames; f++ ) {
			// frames are centered on f * hop, with zeros beyond the ends
			int		from	= f * hop - frame / 2;
			double	sum		= 0;
			for ( int i = from; i < from + frame; i++ ) {
				if ( i >= 0 && i < audio.length ) {
					sum += audio[ i ] * audio[ i ];
				}
			}
			rms[ f ]	= Math.sqrt( sum / frame );
			loudest		= Math.max( loudest, rms[ f ] );
		}
		if ( loudest <= 0 ) {
			return audio;
		}
		int	first	= -1;
		int	last	= -1;
		for ( int f = 0; f < frames; f++ ) {
			double db = 20 * Math.log10( Math.max( rms[ f ], 1e-5 ) / Math.max( loudest, 1e-5 ) );
			if ( db > -60 ) {
				if ( first < 0 ) {
					first = f;
				}
				last = f;
			}
		}
		if ( first < 0 ) {
			return audio;
		}
		int	start	= first * hop;
		int	end		= Math.min( audio.length, ( last + 1 ) * hop );
		return java.util.Arrays.copyOfRange( audio, start, Math.max( start, end ) );
	}

	/**
	 * Join pieces of speech with a pause between each two
	 *
	 * @param pieces The speech of each sentence, in order
	 *
	 * @return One array of samples, with {@link #SENTENCE_PAUSE} seconds of silence between the pieces
	 */
	private static float[] join( List<float[]> pieces ) {
		int	pause	= ( int ) Math.round( SENTENCE_PAUSE * SAMPLE_RATE );
		int	total	= 0;
		for ( float[] piece : pieces ) {
			total += piece.length;
		}
		total += pause * ( pieces.size() - 1 );
		float[]	out	= new float[ total ];
		int		at	= 0;
		for ( int i = 0; i < pieces.size(); i++ ) {
			System.arraycopy( pieces.get( i ), 0, out, at, pieces.get( i ).length );
			at += pieces.get( i ).length + ( i < pieces.size() - 1 ? pause : 0 );
		}
		return out;
	}

}
