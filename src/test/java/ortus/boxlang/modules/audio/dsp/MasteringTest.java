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
package ortus.boxlang.modules.audio.dsp;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.audio.analysis.Loudness;
import ortus.boxlang.modules.audio.types.AudioBuffer;

/**
 * Loudness, dynamics, EQ, time stretch and pitch, checked against signals whose answer is known
 */
public class MasteringTest {

	static final int RATE = 48000;

	/**
	 * A sine of the given peak level in dBFS on every channel
	 */
	public static AudioBuffer sine( double hz, double seconds, double peakDb, int channels ) {
		int			frames	= ( int ) ( seconds * RATE );
		float[][]	data	= new float[ channels ][ frames ];
		double		amp		= Math.pow( 10, peakDb / 20 );
		for ( int i = 0; i < frames; i++ ) {
			float v = ( float ) ( amp * Math.sin( 2 * Math.PI * hz * i / RATE ) );
			for ( int ch = 0; ch < channels; ch++ ) {
				data[ ch ][ i ] = v;
			}
		}
		return new AudioBuffer( data, RATE );
	}

	public static AudioBuffer noise( double seconds, double peakDb, long seed ) {
		Random		random	= new Random( seed );
		int			frames	= ( int ) ( seconds * RATE );
		float[][]	data	= new float[ 2 ][ frames ];
		double		amp		= Math.pow( 10, peakDb / 20 );
		for ( int i = 0; i < frames; i++ ) {
			// Bursts so the loudness varies over time like music
			double envelope = 0.3 + 0.7 * Math.abs( Math.sin( 2 * Math.PI * 2 * i / RATE ) );
			for ( int ch = 0; ch < 2; ch++ ) {
				data[ ch ][ i ] = ( float ) ( amp * envelope * ( random.nextDouble() * 2 - 1 ) );
			}
		}
		return new AudioBuffer( data, RATE );
	}

	/**
	 * Frequency by counting rising zero crossings of the first channel
	 */
	public static double frequency( AudioBuffer audio ) {
		float[]	data		= audio.channel( 0 );
		int		crossings	= 0;
		int		first		= -1, last = -1;
		for ( int i = 1; i < data.length; i++ ) {
			if ( data[ i - 1 ] < 0 && data[ i ] >= 0 ) {
				if ( first < 0 ) {
					first = i;
				}
				last = i;
				crossings++;
			}
		}
		return ( crossings - 1 ) * ( double ) audio.getSampleRate() / ( last - first );
	}

	@DisplayName( "a stereo sine reads its peak level in LUFS, the EBU reference" )
	@Test
	void testLoudnessReference() {
		for ( double level : new double[] { -23, -20, -10 } ) {
			assertThat( Loudness.measure( sine( 997, 20, level, 2 ) ).integrated() ).isWithin( 0.1 ).of( level );
		}
		// One channel carries half the power, 3 LU less
		assertThat( Loudness.measure( sine( 997, 20, -20, 1 ) ).integrated() ).isWithin( 0.1 ).of( -23.0 );
	}

	@DisplayName( "the gates ignore silence and very quiet passages" )
	@Test
	void testGating() {
		AudioBuffer	loud	= sine( 997, 10, -23, 2 );
		AudioBuffer	quiet	= sine( 997, 10, -60, 2 );
		AudioBuffer	gap		= AudioBuffer.silenceSeconds( 10, 2, RATE );
		assertThat( Loudness.measure( Dsp.concat( List.of( loud, gap, quiet ) ) ).integrated() ).isWithin( 0.1 ).of( -23.0 );
		assertThat( Loudness.measure( gap ).integrated() ).isEqualTo( Loudness.FLOOR );
		assertThat( Loudness.measure( sine( 997, 0.2, -20, 2 ) ).integrated() ).isEqualTo( Loudness.FLOOR );
	}

	@DisplayName( "true peak sees peaks between samples, and loudness range is zero for a steady tone" )
	@Test
	void testPeaksAndRange() {
		// A sine at a quarter of the sample rate with a phase that never lands on its crest: sample peak is lower than the real peak
		int			frames	= RATE;
		float[][]	data	= new float[ 1 ][ frames ];
		for ( int i = 0; i < frames; i++ ) {
			data[ 0 ][ i ] = ( float ) Math.sin( 2 * Math.PI * ( RATE / 4.0 ) * i / RATE + Math.PI / 4 );
		}
		Loudness.Result r = Loudness.measure( new AudioBuffer( data, RATE ) );
		assertThat( r.samplePeak() ).isWithin( 0.1 ).of( -3.0 );
		assertThat( r.truePeak() ).isGreaterThan( -0.5 );
		assertThat( Loudness.measure( sine( 997, 20, -20, 2 ) ).range() ).isLessThan( 0.1 );
		AudioBuffer varied = Dsp.concat( List.of( sine( 997, 15, -40, 2 ), sine( 997, 15, -20, 2 ) ) );
		assertThat( Loudness.measure( varied ).range() ).isWithin( 3.0 ).of( 20.0 );
	}

	@DisplayName( "normalizing to a loudness hits the target and keeps the peak under the ceiling" )
	@Test
	void testNormalizeLoudness() {
		for ( double target : new double[] { -14, -16, -23 } ) {
			AudioBuffer		out	= Mastering.toLoudness( noise( 12, -30, 1 ), target, -1 );
			Loudness.Result	r	= Loudness.measure( out );
			assertThat( r.integrated() ).isWithin( 0.5 ).of( target );
			assertThat( r.truePeak() ).isAtMost( -0.9 );
		}
		assertThat( Mastering.target( "streaming" ) ).isEqualTo( -14.0 );
		assertThat( Mastering.target( -18 ) ).isEqualTo( -18.0 );
	}

	@DisplayName( "a limiter never lets a sample above the ceiling and leaves quiet audio alone" )
	@Test
	void testLimit() {
		AudioBuffer	loud	= noise( 3, -1, 2 );
		AudioBuffer	out		= Dynamics.limit( loud, -6, 100, 5 );
		assertThat( out.getPeak() ).isAtMost( ( float ) Math.pow( 10, -6 / 20.0 ) + 1e-6f );
		AudioBuffer	quiet	= sine( 440, 1, -20, 2 );
		AudioBuffer	same	= Dynamics.limit( quiet, -6, 100, 5 );
		assertThat( same.getPeak() ).isWithin( 1e-4f ).of( quiet.getPeak() );
		assertThat( same.getFrames() ).isEqualTo( quiet.getFrames() );
	}

	@DisplayName( "a compressor turns loud audio down by the ratio and does nothing at ratio 1" )
	@Test
	void testCompress() {
		AudioBuffer	tone	= sine( 440, 2, -6, 1 );
		// 12 dB over a -18 threshold at 4:1 becomes 3 dB over, so 9 dB of reduction once the attack has settled
		AudioBuffer	out		= Dynamics.compress( tone, -18, 4, 1, 50, 0, 0 );
		float		tail	= 0;
		for ( int i = out.getFrames() - 2000; i < out.getFrames(); i++ ) {
			tail = Math.max( tail, Math.abs( out.channel( 0 )[ i ] ) );
		}
		assertThat( 20 * Math.log10( tail ) ).isWithin( 0.5 ).of( -15.0 );
		AudioBuffer unchanged = Dynamics.compress( tone, -18, 1, 1, 50, 0, 0 );
		assertThat( unchanged.getPeak() ).isWithin( 1e-4f ).of( tone.getPeak() );
		AudioBuffer matched = Dynamics.compressMatched( noise( 3, -6, 5 ), -30, 4, 5, 80, 6 );
		assertThat( matched.getRms() ).isWithin( 0.002f ).of( noise( 3, -6, 5 ).getRms() );
	}

	@DisplayName( "an EQ band boosts its frequency and leaves a far one alone" )
	@Test
	void testEq() {
		Eq.Band		boost	= new Eq.Band( "peak", 1000, 12, 1 );
		AudioBuffer	at1k	= Eq.apply( sine( 1000, 1, -20, 1 ), List.of( boost ) );
		AudioBuffer	at100	= Eq.apply( sine( 100, 1, -20, 1 ), List.of( boost ) );
		assertThat( 20 * Math.log10( peakAfter( at1k ) ) - -20 ).isWithin( 0.5 ).of( 12.0 );
		assertThat( 20 * Math.log10( peakAfter( at100 ) ) - -20 ).isWithin( 0.5 ).of( 0.0 );
		AudioBuffer shelf = Eq.apply( sine( 8000, 1, -20, 1 ), List.of( new Eq.Band( "highshelf", 4000, -9, 0.707 ) ) );
		assertThat( 20 * Math.log10( peakAfter( shelf ) ) - -20 ).isWithin( 0.7 ).of( -9.0 );
	}

	private static double peakAfter( AudioBuffer audio ) {
		float peak = 0;
		for ( int i = audio.getFrames() / 2; i < audio.getFrames(); i++ ) {
			peak = Math.max( peak, Math.abs( audio.channel( 0 )[ i ] ) );
		}
		return peak;
	}

	@DisplayName( "stretching changes the length and keeps the pitch" )
	@Test
	void testStretch() {
		AudioBuffer tone = sine( 440, 3, -10, 2 );
		for ( double rate : new double[] { 0.5, 0.8, 1.25, 2.0, 3.0 } ) {
			AudioBuffer out = Stretch.stretch( tone, rate );
			assertThat( out.getDurationSeconds() ).isWithin( 0.02 ).of( 3.0 / rate );
			assertThat( frequency( out ) ).isWithin( 6.0 ).of( 440.0 );
			assertThat( out.getChannels() ).isEqualTo( 2 );
			assertThat( out.getPeak() ).isWithin( 0.15f ).of( tone.getPeak() );
		}
		assertThat( Stretch.stretch( tone, 1.0 ).getFrames() ).isEqualTo( tone.getFrames() );
	}

	@DisplayName( "fitTo hits the requested length exactly" )
	@Test
	void testFitTo() {
		AudioBuffer out = Stretch.fitTo( noise( 10, -10, 3 ), 8.4 );
		assertThat( out.getFrames() ).isEqualTo( ( int ) Math.round( 8.4 * RATE ) );
	}

	@DisplayName( "pitch shifting moves the frequency and keeps the length" )
	@Test
	void testPitch() {
		AudioBuffer tone = sine( 440, 3, -10, 1 );
		assertThat( frequency( Stretch.pitch( tone, 12 ) ) ).isWithin( 8.0 ).of( 880.0 );
		assertThat( frequency( Stretch.pitch( tone, -12 ) ) ).isWithin( 4.0 ).of( 220.0 );
		assertThat( frequency( Stretch.pitch( tone, 7 ) ) ).isWithin( 6.0 ).of( 440 * Math.pow( 2, 7 / 12.0 ) );
		assertThat( Stretch.pitch( tone, 5 ).getFrames() ).isEqualTo( tone.getFrames() );
	}

}
