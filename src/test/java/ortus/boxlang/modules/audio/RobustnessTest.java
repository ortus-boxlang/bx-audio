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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.analysis.Loudness;
import ortus.boxlang.modules.audio.analysis.Tempo;
import ortus.boxlang.modules.audio.codec.AudioFiles;
import ortus.boxlang.modules.audio.codec.FlacCodec;
import ortus.boxlang.modules.audio.codec.WavCodec;
import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.dsp.Dynamics;
import ortus.boxlang.modules.audio.dsp.Mastering;
import ortus.boxlang.modules.audio.dsp.Stretch;
import ortus.boxlang.modules.audio.ffmpeg.Markers;
import ortus.boxlang.modules.audio.realtime.Mixer;
import ortus.boxlang.modules.audio.synth.Drums;
import ortus.boxlang.modules.audio.tts.Subtitles;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.Numbers;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * The problems found in the code review, each with the input that showed it
 */
public class RobustnessTest {

	private static final int RATE = 44100;

	private static AudioBuffer sine( double hz, double seconds, double amplitude, int channels ) {
		int			frames	= ( int ) ( seconds * RATE );
		float[][]	data	= new float[ channels ][ frames ];
		for ( int ch = 0; ch < channels; ch++ ) {
			for ( int i = 0; i < frames; i++ ) {
				data[ ch ][ i ] = ( float ) ( amplitude * Math.sin( 2 * Math.PI * hz * i / RATE ) );
			}
		}
		return new AudioBuffer( data, RATE );
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Samples that are not numbers
	// ---------------------------------------------------------------------------------------------------------------

	@DisplayName( "NaN and infinity in the samples become silence, so one bad sample cannot ruin the rest of a file" )
	@Test
	void testNonFiniteSamples() {
		float[][] data = new float[ 1 ][ RATE ];
		java.util.Arrays.fill( data[ 0 ], 0.2f );
		data[ 0 ][ 1000 ]	= Float.POSITIVE_INFINITY;
		data[ 0 ][ 2000 ]	= Float.NaN;
		AudioBuffer audio = new AudioBuffer( data, RATE );
		assertThat( audio.channel( 0 )[ 1000 ] ).isEqualTo( 0f );
		assertThat( audio.channel( 0 )[ 2000 ] ).isEqualTo( 0f );
		AudioBuffer compressed = Dynamics.compress( audio, -18, 4, 10, 100, 6, 0 );
		assertThat( compressed.channel( 0 )[ 40000 ] ).isNotEqualTo( 0f );
		assertThat( Loudness.measure( audio ).integrated() ).isGreaterThan( -40.0 );
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Loudness and the true peak
	// ---------------------------------------------------------------------------------------------------------------

	@DisplayName( "a clip shorter than 400 ms is mastered like any other" )
	@Test
	void testShortClipMastering() {
		AudioBuffer	clip		= sine( 440, 0.3, 0.1, 2 );
		AudioBuffer	mastered	= Mastering.toLoudness( clip, -14, -1 );
		assertThat( Loudness.measure( mastered ).integrated() ).isWithin( 0.6 ).of( -14.0 );
		assertThat( Loudness.truePeakDb( mastered ) ).isLessThan( -0.9 );
	}

	@DisplayName( "the true peak ceiling holds for a noisy mix where the peaks between the samples go over the sample peaks" )
	@Test
	void testTruePeakCeiling() {
		Random		random	= new Random( 7 );
		float[][]	data	= new float[ 2 ][ RATE * 4 ];
		for ( int ch = 0; ch < 2; ch++ ) {
			for ( int i = 0; i < data[ ch ].length; i++ ) {
				data[ ch ][ i ] = ( float ) ( 0.35 * Math.sin( 2 * Math.PI * 8000 * i / RATE ) + 0.35 * Math.sin( 2 * Math.PI * 10500 * i / RATE + ch )
				    + 0.2 * ( random.nextDouble() * 2 - 1 ) );
			}
		}
		AudioBuffer mastered = Mastering.toLoudness( new AudioBuffer( data, RATE ), -6, -1 );
		assertThat( Loudness.truePeakDb( mastered ) ).isAtMost( -0.95 );
	}

	@DisplayName( "the limiter holds a loud tone at the quarter of the sample rate, where the peaks between samples are the highest" )
	@Test
	void testLimitTruePeak() {
		int			frames	= RATE;
		float[][]	data	= new float[ 1 ][ frames ];
		for ( int i = 0; i < frames; i++ ) {
			data[ 0 ][ i ] = ( float ) ( 3.0 * Math.sin( 2 * Math.PI * 11025 * i / RATE + Math.PI / 4 ) );
		}
		AudioBuffer input = new AudioBuffer( data, RATE );
		// The sample peak limiter alone leaves the peaks between samples over the ceiling, which is why this input is a good test
		assertThat( Loudness.truePeakDb( Dynamics.limit( input, -1, 100, 5 ) ) ).isGreaterThan( -0.5 );
		AudioBuffer limited = Dynamics.limitTruePeak( input, -1, 100, 5 );
		assertThat( Loudness.truePeakDb( limited ) ).isAtMost( -0.95 );
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Files
	// ---------------------------------------------------------------------------------------------------------------

	@DisplayName( "a FLAC file that does not know its length is read to the end" )
	@Test
	void testFlacUnknownLength() {
		AudioBuffer	audio	= sine( 440, 0.5, 0.5, 1 );
		byte[]		bytes	= FlacCodec.encode( audio, 16 );
		// The 36 bit total sits in the last 36 bits of the first 64 bit group after the sample rate (20 bits), channels (3) and depth (5)
		int			at		= 4 + 4 + 10;
		bytes[ at + 3 ] = ( byte ) ( bytes[ at + 3 ] & 0xF0 );
		for ( int i = 4; i <= 7; i++ ) {
			bytes[ at + i ] = 0;
		}
		AudioBuffer read = FlacCodec.decode( bytes );
		assertThat( read.getFrames() ).isEqualTo( audio.getFrames() );
		assertThat( read.channel( 0 )[ 5000 ] ).isWithin( 2.0f / 32768 ).of( audio.channel( 0 )[ 5000 ] );
	}

	@DisplayName( "a WAV whose data chunk says 0 bytes, as streaming writers leave it, is read to the end of the file" )
	@Test
	void testWavDataSizeZero() {
		AudioBuffer				audio	= sine( 440, 0.2, 0.5, 2 );
		ByteArrayOutputStream	out		= new ByteArrayOutputStream();
		WavCodec.write( audio, out, 16 );
		byte[] bytes = out.toByteArray();
		// 16 bit PCM has a 44 byte header with the data size in bytes 40 to 43
		ByteBuffer.wrap( bytes ).order( ByteOrder.LITTLE_ENDIAN ).putInt( 40, 0 );
		assertThat( WavCodec.decode( bytes ).getFrames() ).isEqualTo( audio.getFrames() );
	}

	@DisplayName( "an empty data chunk that is followed by another chunk is really empty" )
	@Test
	void testWavEmptyDataChunk() {
		AudioBuffer				audio	= sine( 440, 0.0, 0.5, 1 );
		ByteArrayOutputStream	out		= new ByteArrayOutputStream();
		WavCodec.write( audio, out, 16 );
		byte[]	header	= out.toByteArray();
		byte[]	list	= "LIST\u0004\u0000\u0000\u0000INFO".getBytes( StandardCharsets.ISO_8859_1 );
		byte[]	bytes	= new byte[ header.length + list.length ];
		System.arraycopy( header, 0, bytes, 0, header.length );
		System.arraycopy( list, 0, bytes, header.length, list.length );
		assertThat( WavCodec.decode( bytes ).getFrames() ).isEqualTo( 0 );
	}

	@DisplayName( "a WAV with the fmt chunk after the data chunk is read" )
	@Test
	void testWavFmtAfterData() {
		AudioBuffer				audio	= sine( 440, 0.1, 0.5, 1 );
		ByteArrayOutputStream	out		= new ByteArrayOutputStream();
		WavCodec.write( audio, out, 16 );
		byte[]		wav		= out.toByteArray();
		byte[]		fmt		= java.util.Arrays.copyOfRange( wav, 12, 36 );
		byte[]		data	= java.util.Arrays.copyOfRange( wav, 36, wav.length );
		ByteBuffer	swapped	= ByteBuffer.allocate( 12 + data.length + fmt.length ).order( ByteOrder.LITTLE_ENDIAN );
		swapped.put( wav, 0, 12 ).put( data ).put( fmt );
		assertThat( WavCodec.decode( swapped.array() ).getFrames() ).isEqualTo( audio.getFrames() );
	}

	@DisplayName( "16 bit audio that is already on the 16 bit grid is written as it is, and other audio is dithered the same way every time" )
	@Test
	void testDither() {
		ByteArrayOutputStream	first	= new ByteArrayOutputStream(), second = new ByteArrayOutputStream(), third = new ByteArrayOutputStream();
		AudioBuffer				quiet	= sine( 1000, 0.5, Math.pow( 10, -94.0 / 20 ), 1 );
		WavCodec.write( quiet, first, 16 );
		WavCodec.write( quiet, second, 16 );
		assertThat( second.toByteArray() ).isEqualTo( first.toByteArray() );
		// A sine this quiet rounds to three levels without dither. With dither the quantization noise is spread, so more levels appear.
		Set<Integer>	levels	= new HashSet<>();
		AudioBuffer		back	= WavCodec.decode( first.toByteArray() );
		for ( float v : back.channel( 0 ) ) {
			levels.add( Math.round( v * 32768 ) );
		}
		assertThat( levels.size() ).isGreaterThan( 3 );
		// Read back and written again: nothing changes, because it is on the grid
		WavCodec.write( back, third, 16 );
		assertThat( third.toByteArray() ).isEqualTo( first.toByteArray() );
	}

	@DisplayName( "a failed write leaves the file that was there, and no temporary file" )
	@Test
	void testAtomicWrite( @TempDir Path dir ) throws Exception {
		Path		target	= dir.resolve( "song.flac" );
		AudioBuffer	good	= sine( 440, 0.2, 0.5, 1 );
		AudioFiles.write( good, target, 16, 0 );
		byte[]		before	= Files.readAllBytes( target );
		// FLAC holds at most 8 channels, so this fails when the encoder runs
		AudioBuffer	tooMany	= sine( 440, 0.1, 0.5, 9 );
		assertThrows( BoxRuntimeException.class, () -> AudioFiles.write( tooMany, target, 16, 0 ) );
		assertThat( Files.readAllBytes( target ) ).isEqualTo( before );
		try ( var list = Files.list( dir ) ) {
			assertThat( list.map( p -> p.getFileName().toString() ).toList() ).containsExactly( "song.flac" );
		}
	}

	@DisplayName( "a FLAC file with an ID3 tag in front is read as FLAC" )
	@Test
	void testId3BeforeFlac( @TempDir Path dir ) throws Exception {
		AudioBuffer	audio	= sine( 440, 0.2, 0.5, 1 );
		byte[]		flac	= FlacCodec.encode( audio, 16 );
		byte[]		tag		= { 'I', 'D', '3', 3, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0 };
		byte[]		bytes	= new byte[ tag.length + flac.length ];
		System.arraycopy( tag, 0, bytes, 0, tag.length );
		System.arraycopy( flac, 0, bytes, tag.length, flac.length );
		Path file = dir.resolve( "tagged.flac" );
		Files.write( file, bytes );
		assertThat( AudioFiles.read( file ).getFrames() ).isEqualTo( audio.getFrames() );
	}

	@DisplayName( "a file that is not audio is refused without trying to download FFmpeg" )
	@Test
	void testNotAudio( @TempDir Path dir ) throws Exception {
		Path file = dir.resolve( "notes.txt" );
		Files.writeString( file, "just some text, nothing to hear here" );
		BoxRuntimeException e = assertThrows( BoxRuntimeException.class, () -> AudioFiles.read( file ) );
		assertThat( e.getMessage() ).contains( "is not an audio file" );
	}

	@DisplayName( "an extension that cannot be written is refused before any folder is made" )
	@Test
	void testExtensionCheckedFirst( @TempDir Path dir ) {
		Path target = dir.resolve( "made/by/mistake/song.xyz" );
		assertThrows( BoxRuntimeException.class, () -> AudioFiles.checkWritable( target ) );
		assertThat( Files.exists( dir.resolve( "made" ) ) ).isFalse();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Processing
	// ---------------------------------------------------------------------------------------------------------------

	@DisplayName( "a stretched clip keeps its attack: the first frame is not faded in" )
	@Test
	void testStretchKeepsTheStart() {
		float[][] data = new float[ 1 ][ RATE ];
		java.util.Arrays.fill( data[ 0 ], 0.5f );
		AudioBuffer stretched = Stretch.stretch( new AudioBuffer( data, RATE ), 1.5 );
		assertThat( stretched.channel( 0 )[ 0 ] ).isWithin( 0.05f ).of( 0.5f );
		assertThat( stretched.channel( 0 )[ 300 ] ).isWithin( 0.05f ).of( 0.5f );
	}

	@DisplayName( "a tempo range that makes no sense is refused with a message" )
	@Test
	void testTempoRange() {
		AudioBuffer audio = sine( 440, 1, 0.5, 1 );
		assertThrows( BoxRuntimeException.class, () -> Tempo.detect( audio, 0, 200 ) );
		assertThrows( BoxRuntimeException.class, () -> Tempo.detect( audio, 30, 0 ) );
		assertThrows( BoxRuntimeException.class, () -> Tempo.detect( audio, 200, 60 ) );
	}

	@DisplayName( "drum hits end quietly, with no click" )
	@Test
	void testDrumsEndQuietly() {
		for ( String kind : List.of( "kick", "tom", "snare", "hat", "openhat", "clap", "crash" ) ) {
			AudioBuffer	hit		= Drums.drum( kind, RATE, 1, 1, 1, 0.9, 1 );
			float[]		samples	= hit.channel( 0 );
			double		tail	= 0;
			for ( int i = samples.length - 8; i < samples.length; i++ ) {
				tail = Math.max( tail, Math.abs( samples[ i ] ) );
			}
			assertThat( Dsp.linearToDb( tail ) ).isLessThan( -45.0 );
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Mixer, markers, subtitles
	// ---------------------------------------------------------------------------------------------------------------

	@DisplayName( "play options that cannot work are refused when the sound starts, not on the audio thread" )
	@Test
	void testMixerOptions() {
		try ( Mixer mixer = new Mixer( RATE, 2, "null" ) ) {
			AudioBuffer sound = sine( 440, 0.5, 0.5, 1 );
			assertThrows( BoxRuntimeException.class, () -> mixer.play( sound, Map.of( "start", -1.0 ) ) );
			assertThrows( BoxRuntimeException.class, () -> mixer.play( sound, Map.of( "volume", Double.NaN ) ) );
			assertThrows( BoxRuntimeException.class, () -> mixer.play( sound, Map.of( "pan", Double.POSITIVE_INFINITY ) ) );
			assertThat( mixer.play( sound, Map.of( "start", 0.1 ) ).isPlaying() ).isTrue();
		}
	}

	@DisplayName( "markers: values in the FFmpeg metadata format are escaped, equal markers get their own cue, and a missing label is empty" )
	@Test
	void testMarkers() {
		String meta = Markers.format( List.of( new Markers.Marker( 0, "Part=1; the #best\nline", 0 ) ), "ffmetadata", 30, 5 );
		assertThat( meta ).contains( "title=Part\\=1\\; the \\#best\\\nline" );
		String srt = Markers.format( List.of( new Markers.Marker( 1, "A", 0 ), new Markers.Marker( 1, "A", 0 ), new Markers.Marker( 5, "B", 0 ) ), "srt", 30,
		    8 );
		assertThat( srt.split( "00:00:01,000 --> 00:00:05,000", -1 ).length ).isEqualTo( 3 );
		assertThat( Markers.format( List.of( new Markers.Marker( 1, null, 0 ) ), "json", 30, 5 ) ).contains( "\"label\" : \"\"" );
	}

	@DisplayName( "subtitle text cannot break the cue: no blank lines, no arrow, and WebVTT markup is escaped" )
	@Test
	void testSubtitleEscaping() {
		List<ortus.boxlang.modules.audio.tts.Narration.Cue>	cues	= List
		    .of( new ortus.boxlang.modules.audio.tts.Narration.Cue( 0, 2, "One\n\nTwo --> three <b> & more" ) );
		String												srt		= Subtitles.format( cues, "srt", 0 );
		assertThat( srt ).doesNotContain( "\n\nTwo" );
		assertThat( srt ).contains( "Two -> three" );
		assertThat( Subtitles.format( cues, "vtt", 0 ) ).contains( "&lt;b> &amp; more" );
	}

	@DisplayName( "numbers that arrive as text, as they do from JSON, are read; text that is not a number is explained" )
	@Test
	void testNumbers() {
		assertThat( Numbers.toDouble( "12.5", "the time" ) ).isEqualTo( 12.5 );
		assertThat( Numbers.toDouble( 3, "the time" ) ).isEqualTo( 3.0 );
		BoxRuntimeException e = assertThrows( BoxRuntimeException.class, () -> Numbers.toDouble( "soon", "the time of a marker" ) );
		assertThat( e.getMessage() ).contains( "the time of a marker" );
	}
}
