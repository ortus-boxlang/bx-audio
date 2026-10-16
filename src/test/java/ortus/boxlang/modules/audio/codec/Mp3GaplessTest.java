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
package ortus.boxlang.modules.audio.codec;

import static com.google.common.truth.Truth.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.types.AudioBuffer;

/**
 * An MP3 gives back the length and the timing that went in: the encoder delay and the padding are written in an Info frame and cut on reading
 */
public class Mp3GaplessTest {

	private static AudioBuffer burst( int rate, int channels, int frames, int at ) {
		float[][] d = new float[ channels ][ frames ];
		for ( int c = 0; c < channels; c++ ) {
			// a short burst, so the place of its peak is clear
			for ( int i = 0; i < 40; i++ ) {
				d[ c ][ at + i ] = ( float ) ( 0.8 * Math.sin( 2 * Math.PI * 3000 * i / rate ) * Math.exp( -i / 10.0 ) );
			}
		}
		return new AudioBuffer( d, rate );
	}

	private static int peakAt( AudioBuffer audio ) {
		int		best	= 0;
		float	peak	= 0;
		for ( int i = 0; i < audio.getFrames(); i++ ) {
			if ( Math.abs( audio.channel( 0 )[ i ] ) > peak ) {
				peak	= Math.abs( audio.channel( 0 )[ i ] );
				best	= i;
			}
		}
		return best;
	}

	@Test
	@DisplayName( "Encoding and decoding gives the same length, with a burst where it was, at every rate and channel count" )
	void roundTrip() {
		for ( int rate : new int[] { 44100, 48000, 32000, 22050, 16000 } ) {
			for ( int channels : new int[] { 1, 2 } ) {
				AudioBuffer	src		= burst( rate, channels, 30000, 12000 );
				byte[]		mp3		= Mp3Codec.encode( src, rate >= 32000 ? 192 : 128 );
				AudioBuffer	back	= Mp3Codec.decode( mp3 );
				String		label	= rate + " Hz, " + channels + " channels";
				assertThat( back.getFrames() ).isEqualTo( 30000 );
				assertThat( back.getChannels() ).isEqualTo( channels );
				assertThat( Math.abs( peakAt( back ) - peakAt( src ) ) ).isAtMost( 2 );
				assertThat( new String( mp3, 0, 300, StandardCharsets.ISO_8859_1 ) ).contains( "Info" );
				assertThat( new String( mp3, 0, 300, StandardCharsets.ISO_8859_1 ) ).contains( "LAME" );
				assertThat( label ).isNotEmpty();
			}
		}
	}

	@Test
	@DisplayName( "The Info frame holds the frame count, the file size and the delay and padding that add up to the length" )
	void infoFrame() {
		AudioBuffer		src		= burst( 44100, 2, 20000, 5000 );
		byte[]			mp3		= Mp3Codec.encode( src, 192 );
		Mp3Gapless.Info	info	= Mp3Gapless.read( mp3 );
		assertThat( info.infoLength() ).isGreaterThan( 0 );
		assertThat( info.samples() ).isEqualTo( 1152 );
		assertThat( info.delay() ).isEqualTo( 1152 + 576 );
		assertThat( info.frames() * info.samples() - info.delay() - info.padding() ).isEqualTo( 20000L );
		// every frame after the Info frame is a whole frame, and the file ends where the last one does
		int	pos		= info.infoLength();
		int	count	= 0;
		while ( pos < mp3.length ) {
			int size = Mp3Gapless.frameSize( mp3, pos );
			assertThat( size ).isGreaterThan( 0 );
			pos += size;
			count++;
		}
		assertThat( pos ).isEqualTo( mp3.length );
		assertThat( ( long ) count ).isEqualTo( info.frames() );
	}

	@Test
	@DisplayName( "A file from another LAME encoder is cut by its own tag, to the length of the sound that was encoded" )
	void thirdPartyFile() throws Exception {
		byte[]			bytes	= Files.readAllBytes( Path.of( "src", "test", "resources", "mp3", "lame-tagged.mp3" ) );
		Mp3Gapless.Info	info	= Mp3Gapless.read( bytes );
		assertThat( info.delay() ).isEqualTo( 576 );
		// The file was made from exactly half a second at 44.1 kHz, 22050 frames, and 21 frames less the delay of 576 and the padding of 1566 is that
		assertThat( info.frames() * info.samples() - info.delay() - info.padding() ).isEqualTo( 22050L );
		assertThat( Mp3Codec.decode( bytes ).getFrames() ).isEqualTo( 22050 );
	}

	@Test
	@DisplayName( "A file with no Info frame is decoded as before, with nothing cut" )
	void untagged() {
		AudioBuffer		src		= burst( 44100, 1, 20000, 5000 );
		byte[]			mp3		= Mp3Codec.encode( src, 128 );
		Mp3Gapless.Info	info	= Mp3Gapless.read( mp3 );
		byte[]			bare	= java.util.Arrays.copyOfRange( mp3, info.infoLength(), mp3.length );
		assertThat( Mp3Gapless.read( bare ).infoLength() ).isEqualTo( 0 );
		AudioBuffer back = Mp3Codec.decode( bare );
		assertThat( back.getFrames() % 1152 ).isEqualTo( 0 );
		assertThat( back.getFrames() ).isGreaterThan( 20000 );
	}

}
