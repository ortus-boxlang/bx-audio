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
package ortus.boxlang.modules.audio.tags;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * The length of an MP3 file without decoding it: the frame count of the Xing or Info header when there is one, otherwise the size of the audio
 * divided by the constant bit rate of the first frame.
 */
final class Mp3Duration {

	private static final int[][]	BITRATE_V1_L3	= { { 0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320 } };
	private static final int[]		BITRATE_V1		= BITRATE_V1_L3[ 0 ];
	private static final int[]		BITRATE_V2		= { 0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160 };
	private static final int[]		RATE_V1			= { 44100, 48000, 32000 };

	private Mp3Duration() {
	}

	/**
	 * @return The length in seconds, or 0 when no MPEG audio frame is found
	 */
	static double of( FileChannel channel, long audioAt ) throws IOException {
		int			window	= 8192;
		ByteBuffer	buffer	= ByteBuffer.allocate( window );
		int			got		= 0;
		while ( buffer.hasRemaining() ) {
			int n = channel.read( buffer, audioAt + got );
			if ( n < 0 ) {
				break;
			}
			got += n;
		}
		byte[] b = buffer.array();
		for ( int i = 0; i + 4 <= got; i++ ) {
			if ( ( b[ i ] & 0xFF ) != 0xFF || ( b[ i + 1 ] & 0xE0 ) != 0xE0 ) {
				continue;
			}
			int	versionBits	= ( b[ i + 1 ] >> 3 ) & 3;
			int	layerBits	= ( b[ i + 1 ] >> 1 ) & 3;
			int	bitrateIdx	= ( b[ i + 2 ] >> 4 ) & 0xF;
			int	rateIdx		= ( b[ i + 2 ] >> 2 ) & 3;
			if ( versionBits == 1 || layerBits != 1 || bitrateIdx == 0 || bitrateIdx == 15 || rateIdx == 3 ) {
				continue;
			}
			boolean	v1		= versionBits == 3;
			int		rate	= RATE_V1[ rateIdx ] / ( v1 ? 1 : versionBits == 2 ? 2 : 4 );
			int		kbps	= v1 ? BITRATE_V1[ bitrateIdx ] : BITRATE_V2[ bitrateIdx ];
			int		spf		= v1 ? 1152 : 576;
			boolean	mono	= ( ( b[ i + 3 ] >> 6 ) & 3 ) == 3;
			// The Xing or Info header sits after the side information of the first frame
			int		xing	= i + 4 + ( v1 ? ( mono ? 17 : 32 ) : ( mono ? 9 : 17 ) );
			if ( xing + 12 <= got ) {
				String tag = new String( b, xing, 4, java.nio.charset.StandardCharsets.ISO_8859_1 );
				if ( ( tag.equals( "Xing" ) || tag.equals( "Info" ) ) && ( b[ xing + 7 ] & 1 ) != 0 ) {
					long frames = ( ( b[ xing + 8 ] & 0xFFL ) << 24 ) | ( ( b[ xing + 9 ] & 0xFF ) << 16 ) | ( ( b[ xing + 10 ] & 0xFF ) << 8 )
					    | ( b[ xing + 11 ] & 0xFF );
					return frames * spf / ( double ) rate;
				}
			}
			long		audioBytes	= channel.size() - ( audioAt + i );
			// An ID3v1 tag at the end is not audio
			ByteBuffer	last		= ByteBuffer.allocate( 3 );
			if ( channel.size() > 128 && channel.read( last, channel.size() - 128 ) == 3 && last.get( 0 ) == 'T' && last.get( 1 ) == 'A'
			    && last.get( 2 ) == 'G' ) {
				audioBytes -= 128;
			}
			return audioBytes * 8.0 / ( kbps * 1000.0 );
		}
		return 0;
	}

}
