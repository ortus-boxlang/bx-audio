---
title: Analysis
order: 9
summary: Find the tempo and beats, look at the spectrum, and find silence.
---

## Tempo and beats

`audioBpm` finds the tempo and where the beats fall. It builds an onset curve from the change of the spectrum, finds the tempo from its autocorrelation (with a mild preference for the tempi people tap around 120, and for the faster of two equally strong candidates), then tracks the beats with dynamic programming.

<!-- run -->
```js
kick = audioDrum( type="kick", seconds=0.5 );
bar  = audioConcat( [ kick, kick, kick, kick, kick, kick, kick, kick ] );   // a kick every 0.35 s, which is 171 BPM
r = audioBpm( bar );
println( r.bpm & " BPM, confidence " & r.confidence & ", " & r.beats.len() & " beats" );
```

| Key | Meaning |
|---|---|
| `bpm` | The tempo; `0` when there is no clear pulse (silence, a drone) |
| `confidence` | 0 to 1: how much the pulse stands out |
| `beats` | Beat times in seconds |

`minBpm` and `maxBpm` (default 60 and 200) limit the search. Tempo detection is a guess: music that is half or double a common tempo can be read either way, and `confidence` tells you how sure it is. A click track is found within 1.5 BPM.

## Spectrum

`audioSpectrum` returns the level in dB of log spaced bands. `audioSpectrogram` gives the bands over time for drawing, and `audioFft` the raw frequencies and magnitudes of one window.

<!-- run -->
```js
tone = audioOsc( "sine", 1000, 1 );
bands = audioSpectrum( audio=tone, bands=8, minHz=100, maxHz=10000 );
println( bands );                                     // the band holding 1 kHz is the loudest

fft = audioFft( audio=tone, size=4096 );              // fft.frequencies, fft.magnitudes
grid = audioSpectrogram( audio=tone, bands=16, frames=32 );    // 32 slices of 16 bands
```

Levels are -120 for nothing. `audioFft` magnitudes are scaled so a full scale sine reads 1.

## Silence

<!-- run -->
```js
tone = audioOsc( "sine", 440, 1, 44100 );
gap = audioSilence( seconds=1, channels=1 );
clip = audioConcat( [ gap, tone, gap, tone ] );

println( audioDetectSilence( audio=clip, thresholdDb=-50, minSeconds=0.3 ) );  // [ {start 0, end 1}, {start 2, end 3} ]
trimmed = audioTrimSilence( audio=clip, thresholdDb=-50, pad=0.1 );           // cut off the ends, keep 0.1 s
```

Silence is judged on the peak over 10 ms windows across all channels. `audioTrimSilence` removes it from the start and the end only.

## Loudness

See [Loudness and mastering](loudness-and-mastering.md) for `audioLoudness`. For a waveform to draw, `audioWaveform( audio, points )` returns the loudest level in each of `points` equal slices.

```bash
bx-audio bpm song.wav --beats
bx-audio silence interview.wav --thresholdDb=-45 --min=0.5
bx-audio waveform song.wav
```
