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
package ortus.boxlang.modules.audio.analysis;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.dsp.Dsp;
import ortus.boxlang.modules.audio.dsp.MasteringTest;
import ortus.boxlang.modules.audio.types.AudioBuffer;

/**
 * Spectrum, tempo and silence detection on signals with a known answer
 */
public class AnalysisTest {

	private static final int RATE = 44100;

	/**
	 * Short decaying clicks at a steady tempo, a kick like thump on each beat
	 */
	private static AudioBuffer clicks( double bpm, double seconds, double offset ) {
		int			frames	= ( int ) ( seconds * RATE );
		float[][]	data	= new float[ 1 ][ frames ];
		double		period	= 60.0 / bpm;
		for ( double t = offset; t < seconds - 0.1; t += period ) {
			int start = ( int ) ( t * RATE );
			for ( int i = 0; i < 4000 && start + i < frames; i++ ) {
				double env = Math.exp( -i / 600.0 );
				data[ 0 ][ start + i ] += ( float ) ( 0.8 * env * Math.sin( 2 * Math.PI * 90 * i / RATE ) );
			}
		}
		return new AudioBuffer( data, RATE );
	}

	@DisplayName( "the FFT finds the frequency of a sine at its bin" )
	@Test
	void testFft() {
		AudioBuffer		tone	= MasteringTest.sine( 1000, 1, -6, 1 );
		Spectrum.Bins	bins	= Spectrum.fft( tone, 4096, 0.1 );
		int				best	= 0;
		for ( int i = 1; i < bins.magnitudes().length; i++ ) {
			if ( bins.magnitudes()[ i ] > bins.magnitudes()[ best ] ) {
				best = i;
			}
		}
		assertThat( bins.frequencies()[ best ] ).isWithin( 15.0 ).of( 1000.0 );
		assertThat( bins.magnitudes()[ best ] ).isWithin( 0.1 ).of( 0.5 );
		assertThat( bins.magnitudes().length ).isEqualTo( 2049 );
	}

	@DisplayName( "band levels put the energy in the band that holds the tone" )
	@Test
	void testBands() {
		double[] levels = Spectrum.bands( MasteringTest.sine( 1000, 1, -6, 1 ), 8, 100, 10000, 0, 0 );
		assertThat( levels.length ).isEqualTo( 8 );
		int best = 0;
		for ( int i = 0; i < levels.length; i++ ) {
			if ( levels[ i ] > levels[ best ] ) {
				best = i;
			}
		}
		// Bands are log spaced from 100 to 10000 Hz, so 1000 Hz is on the edge of band 3 and 4
		assertThat( best ).isAnyOf( 3, 4 );
		assertThat( levels[ 0 ] ).isLessThan( levels[ best ] - 30 );
		double[][] grid = Spectrum.spectrogram( MasteringTest.sine( 1000, 1, -6, 1 ), 8, 5, 100, 10000 );
		assertThat( grid.length ).isEqualTo( 5 );
		assertThat( grid[ 0 ].length ).isEqualTo( 8 );
	}

	@DisplayName( "the tempo of a click track is found within a beat per minute, and beats land on the clicks" )
	@Test
	void testTempo() {
		for ( double bpm : new double[] { 90, 120, 140 } ) {
			Tempo.Result r = Tempo.detect( clicks( bpm, 24, 0.25 ), 60, 200 );
			assertThat( r.bpm() ).isWithin( 1.5 ).of( bpm );
			assertThat( r.confidence() ).isGreaterThan( 0.2 );
			double	period		= 60.0 / bpm;
			// Every beat time sits at the same distance after a click, within a few frames
			double	reference	= ( r.beats()[ 4 ] - 0.25 ) % period;
			for ( int i = 4; i < r.beats().length; i++ ) {
				double	offset	= ( r.beats()[ i ] - 0.25 ) % period;
				double	diff	= Math.abs( offset - reference );
				assertThat( Math.min( diff, period - diff ) ).isLessThan( 0.04 );
			}
			assertThat( r.beats().length ).isAtLeast( ( int ) ( 24 / period ) - 4 );
		}
	}

	@DisplayName( "silence has no tempo" )
	@Test
	void testNoTempo() {
		Tempo.Result r = Tempo.detect( AudioBuffer.silenceSeconds( 10, 1, RATE ), 60, 200 );
		assertThat( r.bpm() ).isEqualTo( 0.0 );
		assertThat( r.beats() ).isEmpty();
	}

	@DisplayName( "silence is found, reported with its length, and trimmed from the ends" )
	@Test
	void testSilence() {
		AudioBuffer			tone	= MasteringTest.sine( 440, 1, -10, 1 );
		AudioBuffer			gap		= AudioBuffer.silenceSeconds( 1, 1, 48000 );
		AudioBuffer			all		= Dsp.concat( List.of( gap, tone, gap, tone, gap ) );
		List<Silence.Range>	ranges	= Silence.detect( all, -50, 0.3 );
		assertThat( ranges ).hasSize( 3 );
		assertThat( ranges.get( 0 ).start() ).isWithin( 0.02 ).of( 0.0 );
		assertThat( ranges.get( 0 ).end() ).isWithin( 0.02 ).of( 1.0 );
		assertThat( ranges.get( 1 ).start() ).isWithin( 0.02 ).of( 2.0 );
		assertThat( ranges.get( 1 ).duration() ).isWithin( 0.03 ).of( 1.0 );
		assertThat( ranges.get( 2 ).end() ).isWithin( 0.02 ).of( 5.0 );
		assertThat( Silence.detect( all, -50, 1.5 ) ).isEmpty();
		AudioBuffer trimmed = Silence.trim( all, -50, 0.0 );
		assertThat( trimmed.getDurationSeconds() ).isWithin( 0.03 ).of( 3.0 );
		assertThat( Silence.trim( all, -50, 0.5 ).getDurationSeconds() ).isWithin( 0.03 ).of( 4.0 );
		assertThat( Silence.trim( gap, -50, 0 ).getFrames() ).isEqualTo( 0 );
	}

	@Test
	@DisplayName( "Surround channels weigh 1.41 and the LFE nothing, up to 7.1" )
	void testSurroundWeights() {
		assertThat( Loudness.channelWeights( 2 ) ).isEqualTo( new double[] { 1, 1 } );
		assertThat( Loudness.channelWeights( 6 ) ).isEqualTo( new double[] { 1, 1, 1, 0, 1.41, 1.41 } );
		assertThat( Loudness.channelWeights( 7 ) ).isEqualTo( new double[] { 1, 1, 1, 0, 1.41, 1.41, 1.41 } );
		assertThat( Loudness.channelWeights( 8 ) ).isEqualTo( new double[] { 1, 1, 1, 0, 1.41, 1.41, 1.41, 1.41 } );
		assertThat( Loudness.channelWeights( 10 ) ).isEqualTo( new double[] { 1, 1, 1, 0, 1.41, 1.41, 1.41, 1.41, 1, 1 } );
	}

	@Test
	@DisplayName( "The same tone is 1.5 dB louder on a 7.1 side surround than on the front left, and the LFE does not count" )
	void testSurroundLoudness() {
		float[] tone = new float[ RATE * 5 ];
		for ( int i = 0; i < tone.length; i++ ) {
			tone[ i ] = ( float ) ( 0.3 * Math.sin( 2 * Math.PI * 1000 * i / RATE ) );
		}
		double	front	= Loudness.integrated( withChannel( 8, 0, tone ) );
		double	side	= Loudness.integrated( withChannel( 8, 7, tone ) );
		assertThat( side - front ).isWithin( 0.05 ).of( 10 * Math.log10( 1.41 ) );
		assertThat( Loudness.integrated( withChannel( 8, 3, tone ) ) ).isLessThan( -69.0 );
	}

	private static AudioBuffer withChannel( int channels, int channel, float[] samples ) {
		float[][] data = new float[ channels ][ samples.length ];
		data[ channel ] = samples;
		return new AudioBuffer( data, RATE );
	}

}
