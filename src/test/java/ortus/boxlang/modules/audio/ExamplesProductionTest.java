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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.audio.analysis.Loudness;
import ortus.boxlang.modules.audio.codec.WavCodec;
import ortus.boxlang.modules.audio.dsp.MasteringTest;
import ortus.boxlang.modules.audio.ffmpeg.Ffmpeg;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * The trailer and game audio examples, which use the loudness, stretch, mux, mixer and sprite features end to end
 */
public class ExamplesProductionTest extends ExamplesTestBase {

	/**
	 * A stand in for a recorded voice: three short bursts of a tone with gaps, as speech has
	 */
	private static Path narration( Path dir ) throws Exception {
		AudioBuffer	burst	= MasteringTest.sine( 180, 0.8, -12, 1 );
		AudioBuffer	gap		= AudioBuffer.silenceSeconds( 0.4, 1, 48000 );
		Path		file	= dir.resolve( "narration.wav" );
		WavCodec.write( ortus.boxlang.modules.audio.dsp.Dsp.concat( List.of( burst, gap, burst, gap, burst ) ), file, 16 );
		return file;
	}

	@DisplayName( "the trailer is exactly as long as asked, mastered to the streaming target under the true peak ceiling, with chapters" )
	@Test
	void testTrailer( @TempDir Path dir ) throws Exception {
		useNoDownloads();
		Path	voice	= narration( dir );
		Path	out		= dir.resolve( "trailer.wav" );
		render( "13-trailer/trailer.bxs", Struct.of( "out", out.toString(), "seconds", 14, "voiceFile", voice.toString() ) );
		AudioBuffer		audio	= WavCodec.read( out );
		Loudness.Result	r		= Loudness.measure( audio );
		assertThat( audio.getDurationSeconds() ).isWithin( 0.01 ).of( 14.0 );
		assertThat( audio.getChannels() ).isEqualTo( 2 );
		assertThat( r.integrated() ).isWithin( 0.6 ).of( -14.0 );
		assertThat( r.truePeak() ).isAtMost( -0.8 );
		String chapters = Files.readString( dir.resolve( "trailer.chapters.txt" ) );
		assertThat( chapters ).startsWith( "0:00 Music in\n0:02 Voice-over\n" );
		// A recording gives no words to time, so there are no captions
		assertThat( Files.exists( dir.resolve( "trailer.srt" ) ) ).isFalse();
	}

	@DisplayName( "a quieter target gives a quieter trailer, and the voice makes the trailer longer when it has to" )
	@Test
	void testTrailerTargetsAndLength( @TempDir Path dir ) throws Exception {
		useNoDownloads();
		Path	voice	= narration( dir );
		Path	out		= dir.resolve( "podcast.wav" );
		render( "13-trailer/trailer.bxs", Struct.of( "out", out.toString(), "seconds", 3, "target", "podcast", "voiceFile", voice.toString() ) );
		AudioBuffer audio = WavCodec.read( out );
		assertThat( Loudness.measure( audio ).integrated() ).isWithin( 0.6 ).of( -16.0 );
		// 1.5 s lead, 3.2 s of voice and its room reverb tail, 2.5 s of room
		assertThat( audio.getDurationSeconds() ).isWithin( 0.5 ).of( 8.6 );
	}

	@DisplayName( "with a video the trailer takes its length and the finished file has the new sound on the untouched picture" )
	@Test
	void testTrailerWithVideo( @TempDir Path dir ) throws Exception {
		Ffmpeg tools = Ffmpeg.locate();
		assumeTrue( tools != null, "FFmpeg is not installed here" );
		Path	video	= dir.resolve( "clip.mp4" );
		Process	p		= new ProcessBuilder( tools.getFfmpeg().toString(), "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc=duration=9:size=160x120:rate=25",
		    "-c:v", "mpeg4",
		    video.toString() ).inheritIO().start();
		p.waitFor();
		assumeTrue( Files.exists( video ), "FFmpeg could not make a test video" );
		Path scored = dir.resolve( "scored.mp4" );
		render( "13-trailer/trailer.bxs",
		    Struct.of( "out", dir.resolve( "t.wav" ).toString(), "video", video.toString(), "videoOut", scored.toString(), "voiceFile",
		        narration( dir ).toString() ) );
		IStruct info = ( IStruct ) runScript( "result = videoInfo( '" + scored.toString().replace( "\\", "/" ) + "' )" );
		assertThat( ( Double ) info.get( Key.of( "seconds" ) ) ).isWithin( 0.2 ).of( 9.0 );
		assertThat( ( ( IStruct ) info.get( Key.of( "video" ) ) ).get( Key.of( "codec" ) ) ).isEqualTo( "mpeg4" );
		assertThat( ( ( IStruct ) info.get( Key.of( "audio" ) ) ).get( Key.of( "codec" ) ) ).isEqualTo( "aac" );
	}

	private Object runScript( String source ) {
		runtime.executeSource( source, context );
		return variables.get( Key.of( "result" ) );
	}

	@DisplayName( "the game session renders with music, effects, a crossfade and a fade out, and writes its assets" )
	@Test
	void testGameAudio( @TempDir Path dir ) throws Exception {
		Path	out		= dir.resolve( "session.wav" );
		Path	folder	= dir.resolve( "assets" );
		render( "14-game-audio/game.bxs", Struct.of( "out", out.toString(), "folder", folder.toString() ) );
		AudioBuffer session = WavCodec.read( out );
		assertThat( session.getDurationSeconds() ).isWithin( 0.05 ).of( 11.5 );
		assertThat( session.getChannels() ).isEqualTo( 2 );
		assertThat( session.getPeak() ).isLessThan( 1.0f );
		// The music is playing from the start, the explosion is the loudest moment, and the end has faded to nothing
		float[] left = session.channel( 0 );
		assertThat( rms( left, 0.5, 1.0, session.getSampleRate() ) ).isGreaterThan( 0.01 );
		double boom = rms( left, 7.5, 7.9, session.getSampleRate() );
		assertThat( boom ).isGreaterThan( rms( left, 6.5, 7.0, session.getSampleRate() ) );
		assertThat( rms( left, 11.2, 11.5, session.getSampleRate() ) ).isLessThan( 0.002 );
		assertThat( Files.exists( folder.resolve( "sfx.wav" ) ) ).isTrue();
		assertThat( Files.readString( folder.resolve( "sfx.json" ) ) ).contains( "\"jump1\"" );
		AudioBuffer	loop	= WavCodec.read( folder.resolve( "music-loop.wav" ) );
		AudioBuffer	boss	= WavCodec.read( folder.resolve( "boss-loop.wav" ) );
		// The boss loop is 15 percent faster, so shorter
		assertThat( boss.getDurationSeconds() ).isWithin( 0.05 ).of( loop.getDurationSeconds() / 1.15 );
	}

	@DisplayName( "the game session is repeatable: the same seed gives the same file" )
	@Test
	void testGameRepeatable( @TempDir Path dir ) throws Exception {
		Path	first	= dir.resolve( "a.wav" );
		Path	second	= dir.resolve( "b.wav" );
		render( "14-game-audio/game.bxs", Struct.of( "out", first.toString(), "folder", "" ) );
		render( "14-game-audio/game.bxs", Struct.of( "out", second.toString(), "folder", "" ) );
		assertThat( Files.readAllBytes( second ) ).isEqualTo( Files.readAllBytes( first ) );
	}

	private static double rms( float[] data, double from, double to, int rate ) {
		double	sum	= 0;
		int		a	= ( int ) ( from * rate ), b = ( int ) ( to * rate );
		for ( int i = a; i < b; i++ ) {
			sum += data[ i ] * data[ i ];
		}
		return Math.sqrt( sum / ( b - a ) );
	}

}
