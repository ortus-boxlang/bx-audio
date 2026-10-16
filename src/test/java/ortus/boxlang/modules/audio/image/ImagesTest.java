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
package ortus.boxlang.modules.audio.image;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.synth.Synth;
import ortus.boxlang.modules.audio.types.AudioBuffer;
import ortus.boxlang.modules.audio.util.AudioException;

class ImagesTest {

	private static AudioBuffer sine( double hz, double seconds ) {
		return Dsp.gain( Synth.oscillator( "sine", hz, seconds, 44100, 1, null, 0.5, 0 ), -6 );
	}

	private static BufferedImage decode( byte[] png ) throws Exception {
		return ImageIO.read( new ByteArrayInputStream( png ) );
	}

	private static BufferedImage waveform( AudioBuffer audio, int w, int h, int color, int background ) throws Exception {
		return decode( Png.encode( w, h, WaveformImage.pixels( audio, new WaveformImage.Options( w, h, color, background, false, false ) ) ) );
	}

	@Test
	@DisplayName( "A PNG made by the encoder is read back by ImageIO with the same pixels" )
	void png() throws Exception {
		int[] pixels = new int[ 4 * 3 ];
		for ( int i = 0; i < pixels.length; i++ ) {
			pixels[ i ] = ( ( i * 20 ) << 24 ) | ( i * 15 << 16 ) | ( 255 - i * 10 << 8 ) | i;
		}
		BufferedImage image = decode( Png.encode( 4, 3, pixels ) );
		assertThat( image.getWidth() ).isEqualTo( 4 );
		assertThat( image.getHeight() ).isEqualTo( 3 );
		for ( int i = 0; i < pixels.length; i++ ) {
			assertThat( image.getRGB( i % 4, i / 4 ) ).isEqualTo( pixels[ i ] );
		}
	}

	@Test
	@DisplayName( "A loud sound fills the middle of the waveform and the top and bottom are left empty, silence is only a thin centre line" )
	void waveformShape() throws Exception {
		int				color	= Colors.parse( "ff0000", "color" );
		BufferedImage	loud	= waveform( sine( 100, 1 ), 100, 80, color, Colors.parse( "ffffff", "background" ) );
		// a 100 Hz sine at -6 dB reaches half way to the top, so the middle of every column is red and the top row is the background
		int				middle	= loud.getRGB( 50, 40 );
		assertThat( middle & 0xFFFFFF ).isEqualTo( 0xFF0000 );
		assertThat( loud.getRGB( 50, 0 ) ).isEqualTo( 0xFFFFFFFF );
		assertThat( loud.getRGB( 50, 79 ) ).isEqualTo( 0xFFFFFFFF );

		BufferedImage	silence	= waveform( AudioBuffer.silenceSeconds( 1, 1, 44100 ), 50, 40, color, 0 );
		int				filled	= 0;
		for ( int y = 0; y < 40; y++ ) {
			if ( ( silence.getRGB( 25, y ) >>> 24 ) > 0 ) {
				filled++;
			}
		}
		// the background is transparent, and a line of one pixel sits in the middle
		assertThat( silence.getRGB( 25, 0 ) >>> 24 ).isEqualTo( 0 );
		assertThat( filled ).isIn( java.util.List.of( 1, 2 ) );
	}

	@Test
	@DisplayName( "Quiet is lower than loud, and a sound that is only in the second half draws nothing in the first" )
	void waveformScales() throws Exception {
		int				color	= Colors.parse( "000000", "color" );
		AudioBuffer		half	= Dsp.gain( sine( 100, 1 ), -20 );
		BufferedImage	quiet	= waveform( half, 60, 100, color, 0 );
		BufferedImage	loud	= waveform( sine( 100, 1 ), 60, 100, color, 0 );
		assertThat( covered( quiet, 30 ) ).isLessThan( covered( loud, 30 ) );

		AudioBuffer		late	= ortus.boxlang.modules.audio.dsp.Dsp.concat( java.util.List.of( AudioBuffer.silenceSeconds( 1, 1, 44100 ), sine( 100, 1 ) ) );
		BufferedImage	image	= waveform( late, 100, 100, color, 0 );
		assertThat( covered( image, 10 ) ).isAtMost( 2.0 );
		assertThat( covered( image, 90 ) ).isGreaterThan( 20.0 );
	}

	private static double covered( BufferedImage image, int x ) {
		double total = 0;
		for ( int y = 0; y < image.getHeight(); y++ ) {
			total += ( image.getRGB( x, y ) >>> 24 ) / 255.0;
		}
		return total;
	}

	@Test
	@DisplayName( "Split gives each channel its own half, bars leave gaps" )
	void splitAndBars() throws Exception {
		AudioBuffer		left	= sine( 100, 1 );
		AudioBuffer		right	= AudioBuffer.silenceSeconds( 1, 1, 44100 );
		AudioBuffer		stereo	= new AudioBuffer( new float[][] { left.channel( 0 ), right.channel( 0 ) }, 44100 );
		int				color	= Colors.parse( "000000", "color" );
		BufferedImage	image	= decode( Png.encode( 80, 80, WaveformImage.pixels( stereo, new WaveformImage.Options( 80, 80, color, 0, false, true ) ) ) );
		double			top		= 0;
		double			bottom	= 0;
		for ( int y = 0; y < 40; y++ ) {
			top		+= ( image.getRGB( 40, y ) >>> 24 ) / 255.0;
			bottom	+= ( image.getRGB( 40, y + 40 ) >>> 24 ) / 255.0;
		}
		// the left channel is loud, the right one is a thin line
		assertThat( top ).isGreaterThan( 8 * bottom );

		BufferedImage bars = decode( Png.encode( 80, 40, WaveformImage.pixels( sine( 100, 1 ), new WaveformImage.Options( 80, 40, color, 0, true, false ) ) ) );
		assertThat( bars.getRGB( 0, 20 ) >>> 24 ).isEqualTo( 255 );
		assertThat( bars.getRGB( 3, 20 ) >>> 24 ).isEqualTo( 0 );
	}

	@Test
	@DisplayName( "The SVG is well formed XML with the size, the colors and a description" )
	void svg() throws Exception {
		String		svg	= WaveformImage.svg( sine( 100, 2 ),
		    new WaveformImage.Options( 120, 60, Colors.parse( "3b82f6", "color" ), Colors.parse( "0f172a", "background" ), false, false ) );
		Document	doc	= DocumentBuilderFactory.newInstance().newDocumentBuilder().parse( new ByteArrayInputStream( svg.getBytes( StandardCharsets.UTF_8 ) ) );
		assertThat( doc.getDocumentElement().getAttribute( "viewBox" ) ).isEqualTo( "0 0 120 60" );
		assertThat( doc.getDocumentElement().getAttribute( "aria-label" ) ).isEqualTo( "Waveform of 2 seconds" );
		assertThat( doc.getElementsByTagName( "path" ).getLength() ).isEqualTo( 2 );
		assertThat( svg ).contains( "#3b82f6" );
		assertThat( svg ).contains( "#0f172a" );
		String bars = WaveformImage.svg( sine( 100, 2 ), new WaveformImage.Options( 120, 60, Colors.parse( "3b82f6", "color" ), 0, true, false ) );
		assertThat( DocumentBuilderFactory.newInstance().newDocumentBuilder().parse( new ByteArrayInputStream( bars.getBytes( StandardCharsets.UTF_8 ) ) )
		    .getElementsByTagName( "rect" ).getLength() ).isEqualTo( 30 );
	}

	@Test
	@DisplayName( "A tone is one bright row of the spectrogram, at the height of its frequency" )
	void spectrogramTone() throws Exception {
		int				width	= 40;
		int				height	= 128;
		BufferedImage	image	= decode( Png.encode( width, height,
		    SpectrogramImage.pixels( sine( 1000, 2 ), new SpectrogramImage.Options( width, height, 40, 16000, 80, "gray" ) ) ) );
		int				best	= -1;
		int				bright	= -1;
		for ( int y = 0; y < height; y++ ) {
			int v = image.getRGB( 20, y ) & 255;
			if ( v > bright ) {
				bright	= v;
				best	= y;
			}
		}
		// 1 kHz on a log scale from 40 Hz to 16 kHz, with the low frequencies at the bottom
		double expected = height - 1 - Math.log( 1000.0 / 40 ) / Math.log( 16000.0 / 40 ) * height;
		assertThat( ( double ) best ).isWithin( 4.0 ).of( expected );
		assertThat( bright ).isAtLeast( 250 );
		// far from the tone it is dark
		assertThat( image.getRGB( 20, 5 ) & 255 ).isLessThan( 60 );
	}

	@Test
	@DisplayName( "Silence stays dark instead of every point being the loudest" )
	void spectrogramSilence() throws Exception {
		BufferedImage image = decode( Png.encode( 20, 20,
		    SpectrogramImage.pixels( AudioBuffer.silenceSeconds( 1, 1, 44100 ), new SpectrogramImage.Options( 20, 20, 40, 16000, 80, "gray" ) ) ) );
		for ( int y = 0; y < 20; y++ ) {
			assertThat( image.getRGB( 10, y ) & 255 ).isEqualTo( 0 );
		}
	}

	@Test
	@DisplayName( "Colors, color scales, format and size are checked with typed errors" )
	void validation() {
		assertThat( Colors.parse( "#fff", "color" ) ).isEqualTo( 0xFFFFFFFF );
		assertThat( Colors.parse( "3b82f680", "color" ) ).isEqualTo( 0x803b82f6 );
		assertThat( Colors.scale( "gray", 0 ) ).isEqualTo( 0xFF000000 );
		assertThat( Colors.scale( "gray", 1 ) ).isEqualTo( 0xFFFFFFFF );
		assertThat( Colors.scale( "magma", 2 ) ).isEqualTo( Colors.scale( "magma", 1 ) );
		assertThat( assertThrows( AudioException.class, () -> Colors.parse( "red", "color" ) ).getType() ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( ImageOutput.format( "", "out/wave.png", "svg" ) ).isEqualTo( "png" );
		assertThat( ImageOutput.format( "", "", "svg" ) ).isEqualTo( "svg" );
		assertThat( assertThrows( AudioException.class, () -> ImageOutput.format( "svg", "wave.png", "svg" ) ).getMessage() ).contains( "ends in .png" );
		assertThat( assertThrows( AudioException.class, () -> ImageOutput.format( "gif", "", "svg" ) ).getType() ).isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( assertThrows( AudioException.class, () -> ImageOutput.checkSize( 4, 100, 8192, 4096 ) ).getType() )
		    .isEqualTo( AudioException.INVALID_ARGUMENT );
		assertThat( assertThrows( AudioException.class, () -> ImageOutput.checkSize( 8192, 4096, 8192, 4096 ) ).getType() )
		    .isEqualTo( AudioException.TOO_LARGE );
	}

}
