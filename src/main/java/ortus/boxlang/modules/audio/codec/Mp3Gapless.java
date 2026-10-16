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

import java.nio.charset.StandardCharsets;

/**
 * The Info frame that makes an MP3 gapless: the first frame of the file holds no sound, only the Xing or Info header with the frame count and the
 * LAME tag that says how many samples at the start (the encoder delay) and at the end (the padding) are not part of the sound. Without it a
 * decoder cannot know that the sound it hears starts a few thousand samples late and ends late too.
 *
 * <p>
 * This class reads that header and writes one. It does not use the codec jars.
 */
final class Mp3Gapless {

	/** The samples a decoder itself delays the sound by, which the LAME tag does not include */
	static final int			DECODER_DELAY	= 529;

	private static final int[]	BITRATE_V1		= { 0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320 };
	private static final int[]	BITRATE_V2		= { 0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160 };
	private static final int[]	RATE_V1			= { 44100, 48000, 32000 };

	private Mp3Gapless() {
	}

	/**
	 * What the Info frame of a file says
	 *
	 * @param infoLength Bytes of the file up to the end of the Info frame, 0 when there is none
	 * @param samples    Samples in an MPEG frame, 1152 or 576
	 * @param frames     The frame count it gives (without the Info frame itself), or -1
	 * @param delay      The encoder delay from the LAME tag, or -1 when there is no LAME tag
	 * @param padding    The padding from the LAME tag, or -1
	 */
	record Info( int infoLength, int samples, long frames, int delay, int padding ) {

		static final Info NONE = new Info( 0, 1152, -1, -1, -1 );
	}

	/**
	 * Read the first frame of the audio, after an ID3v2 tag when there is one
	 */
	static Info read( byte[] b ) {
		int at = id3Length( b );
		while ( at + 4 <= b.length && ! ( ( b[ at ] & 0xFF ) == 0xFF && ( b[ at + 1 ] & 0xE0 ) == 0xE0 ) ) {
			// stray bytes between the tag and the first frame are skipped, as far as a sensible gap
			if ( ++at > 8192 + id3Length( b ) ) {
				return Info.NONE;
			}
		}
		int size = frameSize( b, at );
		if ( size <= 0 || at + size > b.length ) {
			return Info.NONE;
		}
		boolean	v1		= ( ( b[ at + 1 ] >> 3 ) & 3 ) == 3;
		boolean	mono	= ( ( b[ at + 3 ] >> 6 ) & 3 ) == 3;
		int		xing	= at + 4 + ( v1 ? ( mono ? 17 : 32 ) : ( mono ? 9 : 17 ) );
		if ( xing + 8 > at + size ) {
			return Info.NONE;
		}
		String tag = new String( b, xing, 4, StandardCharsets.ISO_8859_1 );
		if ( !tag.equals( "Xing" ) && !tag.equals( "Info" ) ) {
			return Info.NONE;
		}
		int		samples	= v1 ? 1152 : 576;
		int		flags	= b[ xing + 7 ] & 0xFF;
		int		o		= xing + 8;
		long	frames	= -1;
		if ( ( flags & 1 ) != 0 && o + 4 <= at + size ) {
			frames	= int32( b, o );
			o		+= 4;
		}
		if ( ( flags & 2 ) != 0 ) {
			o += 4;
		}
		if ( ( flags & 4 ) != 0 ) {
			o += 100;
		}
		if ( ( flags & 8 ) != 0 ) {
			o += 4;
		}
		int	delay	= -1;
		int	padding	= -1;
		// The LAME tag: a nine byte encoder name, then 12 bytes later the 12 bit delay and the 12 bit padding
		if ( o + 24 <= at + size && printable( b, o, 4 ) ) {
			delay	= ( ( b[ o + 21 ] & 0xFF ) << 4 ) | ( ( b[ o + 22 ] & 0xFF ) >> 4 );
			padding	= ( ( b[ o + 22 ] & 0x0F ) << 8 ) | ( b[ o + 23 ] & 0xFF );
		}
		return new Info( at + size, samples, frames, delay, padding );
	}

	/**
	 * The Info frame for a stream of constant bitrate frames, with a LAME tag that gives the delay and the padding
	 *
	 * @param stream  The encoded frames
	 * @param samples The length of the sound in samples per channel
	 *
	 * @return The frame to put in front of the stream, or an empty array when the stream is not in the form expected
	 */
	static byte[] infoFrame( byte[] stream, long samples ) {
		if ( stream.length < 4 || ( stream[ 0 ] & 0xFF ) != 0xFF || ( stream[ 1 ] & 0xE0 ) != 0xE0 ) {
			return new byte[ 0 ];
		}
		boolean	v1		= ( ( stream[ 1 ] >> 3 ) & 3 ) == 3;
		boolean	mono	= ( ( stream[ 3 ] >> 6 ) & 3 ) == 3;
		int		spf		= v1 ? 1152 : 576;
		int		size	= frameSize( stream, 0 );
		if ( size < 4 + 32 + 120 + 24 ) {
			return new byte[ 0 ];
		}
		long	frames	= 0;
		int		pos		= 0;
		while ( pos + 4 <= stream.length && ( stream[ pos ] & 0xFF ) == 0xFF && ( stream[ pos + 1 ] & 0xE0 ) == 0xE0 ) {
			int next = frameSize( stream, pos );
			if ( next <= 0 || pos + next > stream.length ) {
				break;
			}
			frames++;
			pos += next;
		}
		// The encoder holds back one frame and the 576 samples of its own delay: what the decoder drops after the Info frame is these and its own
		int		delay	= spf + 576;
		long	padding	= frames * spf - delay - samples;
		if ( padding < 0 || padding > 4095 || delay > 4095 ) {
			return new byte[ 0 ];
		}
		byte[] frame = new byte[ size ];
		System.arraycopy( stream, 0, frame, 0, 4 );
		int xing = 4 + ( v1 ? ( mono ? 17 : 32 ) : ( mono ? 9 : 17 ) );
		put( frame, xing, "Info" );
		frame[ xing + 7 ] = 0x0F;
		put32( frame, xing + 8, frames );
		put32( frame, xing + 12, size + ( long ) pos );
		for ( int i = 0; i < 100; i++ ) {
			frame[ xing + 16 + i ] = ( byte ) Math.min( 255, Math.round( i * 256.0 / 100 ) );
		}
		int tag = xing + 120;
		// jump3r is a port of LAME, and players such as ffmpeg only act on the delay of a tag that names LAME
		put( frame, tag, "LAME3.98 " );
		frame[ tag + 21 ]	= ( byte ) ( delay >> 4 );
		frame[ tag + 22 ]	= ( byte ) ( ( ( delay & 0x0F ) << 4 ) | ( padding >> 8 ) );
		frame[ tag + 23 ]	= ( byte ) ( padding & 0xFF );
		return frame;
	}

	/**
	 * Bytes of a frame, from its header, or 0 when the header is not a layer III one
	 */
	static int frameSize( byte[] b, int at ) {
		if ( at + 4 > b.length ) {
			return 0;
		}
		int	versionBits	= ( b[ at + 1 ] >> 3 ) & 3;
		int	layerBits	= ( b[ at + 1 ] >> 1 ) & 3;
		int	bitrateIdx	= ( b[ at + 2 ] >> 4 ) & 0xF;
		int	rateIdx		= ( b[ at + 2 ] >> 2 ) & 3;
		int	padding		= ( b[ at + 2 ] >> 1 ) & 1;
		if ( versionBits == 1 || layerBits != 1 || bitrateIdx == 0 || bitrateIdx == 15 || rateIdx == 3 ) {
			return 0;
		}
		boolean	v1		= versionBits == 3;
		int		rate	= RATE_V1[ rateIdx ] / ( v1 ? 1 : versionBits == 2 ? 2 : 4 );
		int		kbps	= v1 ? BITRATE_V1[ bitrateIdx ] : BITRATE_V2[ bitrateIdx ];
		return ( v1 ? 144 : 72 ) * kbps * 1000 / rate + padding;
	}

	private static int id3Length( byte[] b ) {
		if ( b.length >= 10 && b[ 0 ] == 'I' && b[ 1 ] == 'D' && b[ 2 ] == '3' ) {
			int size = ( ( b[ 6 ] & 0x7F ) << 21 ) | ( ( b[ 7 ] & 0x7F ) << 14 ) | ( ( b[ 8 ] & 0x7F ) << 7 ) | ( b[ 9 ] & 0x7F );
			return 10 + size + ( ( b[ 5 ] & 0x10 ) != 0 ? 10 : 0 );
		}
		return 0;
	}

	private static boolean printable( byte[] b, int at, int count ) {
		for ( int i = 0; i < count; i++ ) {
			int c = b[ at + i ] & 0xFF;
			if ( c < 0x20 || c > 0x7E ) {
				return false;
			}
		}
		return true;
	}

	private static long int32( byte[] b, int at ) {
		return ( ( b[ at ] & 0xFFL ) << 24 ) | ( ( b[ at + 1 ] & 0xFFL ) << 16 ) | ( ( b[ at + 2 ] & 0xFFL ) << 8 ) | ( b[ at + 3 ] & 0xFFL );
	}

	private static void put( byte[] to, int at, String text ) {
		byte[] bytes = text.getBytes( StandardCharsets.ISO_8859_1 );
		System.arraycopy( bytes, 0, to, at, bytes.length );
	}

	private static void put32( byte[] to, int at, long value ) {
		to[ at ]		= ( byte ) ( value >> 24 );
		to[ at + 1 ]	= ( byte ) ( value >> 16 );
		to[ at + 2 ]	= ( byte ) ( value >> 8 );
		to[ at + 3 ]	= ( byte ) value;
	}

}
