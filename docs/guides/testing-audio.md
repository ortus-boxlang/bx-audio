---
title: Testing audio
order: 14
summary: Test code that makes sound: assert length, level, loudness, silence and pitch, and keep the tests repeatable.
---

Sound is numbers, so a test can check it. You do not need to listen to find out that a fade is missing, a file clips, or a clip is the wrong length. These checks run in any test framework; the examples use plain BoxLang `assert` and read like TestBox.

## Repeatable

Noise and every random choice take a seed, and the same seed gives the same samples on every machine. A test that makes noise must pass one.

<!-- run -->
```js
a = audioNoise( seconds=1, color="pink", seed=42 );
b = audioNoise( seconds=1, color="pink", seed=42 );
c = audioNoise( seconds=1, color="pink", seed=43 );
arrays = createObject( "java", "java.util.Arrays" );
assert arrays.equals( a.channel( 0 ), b.channel( 0 ) );        // same seed: the same samples
assert !arrays.equals( a.channel( 0 ), c.channel( 0 ) );       // another seed: another noise
println( "repeatable" );
```

## Length, channels and sample rate

<!-- run -->
```js
sound = audioFade( audio=audioOsc( shape="sine", freq=440, seconds=2 ), fadeIn=0.01, fadeOut=0.2 );
assert sound.getDurationSeconds() == 2;
assert sound.getChannels() == 1;
assert sound.getSampleRate() == 44100;
println( "shape ok" );
```

## Level

`getPeak()` is linear, 1 is the loudest a file can hold. A sound that peaks above 1 clips when saved. `audioLoudness` is what a listener hears: check that against the target you deliver to.

<!-- run -->
```js
mix = audioGain( audioNoise( seconds=8, color="pink", seed=1 ), -20 );
assert mix.getPeak() <= 1;                                         // no clipping

mastered = audioMaster( audio=mix, target="streaming", truePeakDb=-1 );
loud = audioLoudness( mastered );
assert abs( loud.integrated - -14 ) < 0.5;                        // on the target, give or take half a LU
assert loud.truePeakDb <= -0.9;                                   // under the ceiling
println( "level ok: " & numberFormat( loud.integrated, "0.0" ) & " LUFS" );
```

## Silence and gaps

`audioDetectSilence` returns the quiet stretches, so a test can check that a sound starts at once, a loop has no hole, or a pause is as long as it should be.

<!-- run -->
```js
voice = audioConcat( [ audioGain( audioNoise( seconds=1, seed=1 ), -10 ), audioSilence( seconds=1, channels=1 ), audioGain( audioNoise( seconds=1, seed=2 ), -10 ) ] );
gaps = audioDetectSilence( audio=voice, thresholdDb=-50, minSeconds=0.3 );
assert gaps.len() == 1;
assert abs( gaps[ 1 ].duration - 1 ) < 0.05;
println( "gap found at " & numberFormat( gaps[ 1 ].start, "0.00" ) & " s" );
```

## Pitch

`audioFft` gives the strongest frequency of a sound. Allow for the width of a bin: with a window of 4096 samples at 44100 Hz a bin is about 11 Hz wide.

<!-- run -->
```js
tone = audioOsc( shape="sine", freq=880, seconds=1 );
fft = audioFft( audio=tone, size=4096 );
strongest = 1;
for( i = 1; i <= fft.magnitudes.len(); i++ ){
    if( fft.magnitudes[ i ] > fft.magnitudes[ strongest ] ){
        strongest = i;
    }
}
assert abs( fft.frequencies[ strongest ] - 880 ) < 12;
println( "pitch ok: " & numberFormat( fft.frequencies[ strongest ], "0" ) & " Hz" );
```

## A file you wrote

Load it back and check it, the same way. A WAV is described from its header, so `audioInfo` is instant even for a long file; ask for `levels=true` to measure its peak.

```js
audioSave( audio=sound, path=tempFile );
info = audioInfo( source=tempFile, levels=true );
assert info.seconds == 2 && info.peak <= 1;
```

## Errors

The module throws errors with a `bxaudio.` type, so a test can expect the right one:

```js
expect( () => audioLoad( "missing.wav" ) ).toThrow( type="bxaudio.fileNotFound" );
```

See [Errors](../reference/errors.md) for the list.
