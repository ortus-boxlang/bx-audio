---
title: Loudness and mastering
order: 5
summary: Measure loudness the EBU R128 way and deliver to streaming, podcast or broadcast targets with a true-peak limiter.
---

Peak level tells you how close a file is to clipping. It does not tell you how loud it sounds. Streaming services, podcasts and broadcasters measure **loudness** instead, in LUFS (loudness units relative to full scale), and turn loud tracks down to a target. Deliver at the target and nothing is changed for you.

## Measure

<!-- run -->
```js
mix = audioGain( audioNoise( seconds=8, color="pink" ), -14 );
r = audioLoudness( mix );
println( "integrated " & r.integrated & " LUFS, range " & r.range & " LU, true peak " & r.truePeakDb & " dBFS" );
```

`audioLoudness` follows ITU-R BS.1770 and EBU R128 (K-weighting, gated 400 ms blocks, true peak from a 4 times oversampled signal) and returns:

| Key | Meaning |
|---|---|
| `integrated` | Loudness of the whole program in LUFS, with silence and very quiet passages gated out. The number services use |
| `range` | Loudness range (LRA) in LU: how much the loudness moves |
| `truePeakDb` | Highest peak between samples, dBFS |
| `samplePeakDb` | Highest sample, dBFS |
| `momentaryMax` / `shortTermMax` | Loudest 400 ms and loudest 3 s, LUFS |

Silence reads -120. A clip shorter than 400 ms is measured as one block of its own length, and `shortTermMax` reads -120 below 3 s. This implementation agrees with FFmpeg's `ebur128` filter to within 0.1 LU on rendered music, and with the EBU reference tones (a stereo sine at -23 dBFS reads -23 LUFS).

## Master to a target

<!-- run -->
```js
mix = audioGain( audioNoise( seconds=8, color="pink" ), -22 );
final = audioMaster( audio=mix, target="streaming", truePeakDb=-1 );
println( audioLoudness( final ).integrated );   // -14
```

`audioMaster` sets the gain to reach the target, then a look-ahead limiter holds the peaks under the true-peak ceiling. The limiter lowers the loudness a little, so the gain is corrected and the pass repeated until the target is hit within 0.05 LU. It stops after four passes. If the ceiling makes the target unreachable (very peaky material or a very loud target), the result is quieter than the target, so check it with `audioLoudness`.

| Target | LUFS | Used for |
|---|---|---|
| `streaming` | -14 | Spotify, YouTube, Apple Music and most video platforms |
| `podcast` | -16 | Podcasts |
| `game` | -16 | Game audio |
| `broadcast` | -23 | EBU R128 television and radio |
| `cinema` | -24 | Film trailers and cinema |

Pass a number for any other level. `glue=true` compresses lightly first (2:1, slow) so a busy mix sounds more even. `audioNormalizeLoudness` does the same with a plain number of LUFS.

## The tools it is built from

You can use them directly:

<!-- run -->
```js
voice = audioNoise( seconds=4, color="pink", seed=3 );

voice = audioEq( voice, [
    { type: "highpass", freq: 90 },                       // no rumble
    { type: "peak", freq: 3000, gainDb: 3, q: 1 },          // presence
    { type: "highshelf", freq: 9000, gainDb: -2 }           // tame the hiss
] );
voice = audioCompress( audio=voice, thresholdDb=-20, ratio=3, attackMs=8, releaseMs=120 );
voice = audioLimit( audio=voice, ceilingDb=-1 );
```

| Function | Does |
|---|---|
| `audioCompress` | Turns loud parts down by `ratio` above `thresholdDb`, with attack, release and a soft knee. `makeupDb="auto"` matches the loudness of the input |
| `audioLimit` | A look-ahead limiter: no sample ends above `ceilingDb`, and the gain recovers over `releaseMs` |
| `audioEq` | Parametric bands: `peak`, `lowshelf`, `highshelf`, `lowpass`, `highpass`, `notch` |

## From the command line

```bash
bx-audio loudness mix.wav
bx-audio master mix.wav final.wav --target=podcast
bx-audio master mix.wav final.wav --target=-18 --truePeakDb=-2 --glue
```
