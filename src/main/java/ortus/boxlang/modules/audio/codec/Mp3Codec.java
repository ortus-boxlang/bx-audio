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

import java.util.Arrays;
import java.util.List;

import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

/**
 * MP3 reading and writing.
 * <p>
 * The decoder is JLayer and the encoder is jump3r, a Java port of LAME. Both are LGPL, so they are not part of this module's jar: they ship
 * as separate jars in the module's {@code libs} folder (see {@code licenses/THIRD-PARTY.md}), where they can be replaced or removed. This
 * class does not reference their types, it only reaches them through {@link Mp3Decoder} and {@link Mp3Encoder}, which the JVM loads when
 * MP3 is first used. Without the jars everything else in the module still works and MP3 gives a clear error.
 */
public final class Mp3Codec {

	/**
	 * Sample rates MP3 can hold: MPEG 1 (32, 44.1, 48 kHz), MPEG 2 (16, 22.05, 24 kHz) and MPEG 2.5 (8, 11.025, 12 kHz)
	 */
	public static final List<Integer>	SAMPLE_RATES	= List.of( 8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000 );

	/**
	 * Constant bitrates in kbps, which depend on the MPEG version, so on the sample rate
	 */
	private static final int[]			BITRATES_MPEG1	= { 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320 };
	private static final int[]			BITRATES_MPEG2	= { 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160 };

	private static final String			MISSING_MESSAGE	= "MP3 support needs the optional codec jars (jlayer and jump3r) in the module's libs folder. "
	    + "They are LGPL licensed and shipped separately, see licenses/THIRD-PARTY.md";

	private Mp3Codec() {
	}

	/**
	 * @return true if the decoder and encoder jars can be loaded
	 */
	public static boolean isAvailable() {
		try {
			Class.forName( "javazoom.jl.decoder.Bitstream", false, Mp3Codec.class.getClassLoader() );
			Class.forName( "de.sciss.jump3r.lowlevel.LameEncoder", false, Mp3Codec.class.getClassLoader() );
			return true;
		} catch ( ClassNotFoundException | LinkageError e ) {
			return false;
		}
	}

	/**
	 * Check that the optional codec jars are there
	 */
	public static void requireAvailable() {
		if ( !isAvailable() ) {
			throw new AudioException( AudioException.INTERNAL, MISSING_MESSAGE );
		}
	}

	/**
	 * Whether bytes look like an MP3 file: an ID3v2 tag, or the 11 bit frame sync that starts every MP3 frame
	 */
	public static boolean looksLikeMp3( byte[] bytes ) {
		if ( bytes.length < 4 ) {
			return false;
		}
		if ( bytes[ 0 ] == 'I' && bytes[ 1 ] == 'D' && bytes[ 2 ] == '3' ) {
			return true;
		}
		int	second	= bytes[ 1 ] & 0xFF;
		int	layer	= ( second >> 1 ) & 0x03;
		// 0xFF then three more set bits, with a layer that is not the reserved 00 (layer III is 01)
		return ( bytes[ 0 ] & 0xFF ) == 0xFF && ( second & 0xE0 ) == 0xE0 && layer != 0;
	}

	/**
	 * Decode MP3 bytes
	 */
	public static AudioBuffer decode( byte[] bytes ) {
		requireAvailable();
		return Mp3Decoder.decode( bytes );
	}

	/**
	 * Encode a buffer as a constant bitrate MP3
	 *
	 * @param audio       Mono or stereo, at one of {@link #SAMPLE_RATES}
	 * @param bitrateKbps The bitrate in kilobits per second, see {@link #bitratesFor}, or 0 for {@link #defaultBitrate}
	 */
	public static byte[] encode( AudioBuffer audio, int bitrateKbps ) {
		final int kbps = bitrateKbps == 0 ? defaultBitrate( audio.getSampleRate() ) : bitrateKbps;
		if ( audio.getChannels() > 2 ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT,
			    "MP3 holds mono or stereo audio but this buffer has " + audio.getChannels() + " channels. Use audioChannels() first" );
		}
		if ( !SAMPLE_RATES.contains( audio.getSampleRate() ) ) {
			throw new AudioException( AudioException.UNSUPPORTED_FORMAT,
			    "MP3 cannot hold a sample rate of " + audio.getSampleRate() + " Hz. Use audioResample() to one of " + SAMPLE_RATES );
		}
		int[] allowed = bitratesFor( audio.getSampleRate() );
		if ( Arrays.stream( allowed ).noneMatch( b -> b == kbps ) ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT,
			    "A bitrate of " + kbps + " kbps is not valid for " + audio.getSampleRate() + " Hz. Use one of " + Arrays.toString( allowed ) );
		}
		if ( audio.getFrames() == 0 ) {
			throw new AudioException( AudioException.INVALID_ARGUMENT, "Cannot make an MP3 from empty audio" );
		}
		requireAvailable();
		return Mp3Encoder.encode( audio, kbps );
	}

	/**
	 * The bitrate used when none is asked for: 192 kbps, or 128 kbps at sample rates below 32 kHz, where 192 does not exist
	 */
	public static int defaultBitrate( int sampleRate ) {
		return sampleRate >= 32000 ? 192 : 128;
	}

	/**
	 * The constant bitrates in kbps that are valid at a sample rate
	 */
	public static int[] bitratesFor( int sampleRate ) {
		return ( sampleRate >= 32000 ? BITRATES_MPEG1 : BITRATES_MPEG2 ).clone();
	}

}
