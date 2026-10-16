# Sampler

Recordings turned into instruments. Two short recordings are made in the script, a plucked saw and a steady pad, and each goes to `audioSampler()`. `audioNote()` then plays them at any pitch: the pluck plays a melody and a bass, and the pad plays four chords (Am, F, C, G) and holds each for a whole bar by looping its steady middle.

```bash
boxlang sampler.bxs                          # 8 bars at 96 BPM, about 23 seconds, out/sampler.wav
boxlang sampler.bxs --bars=16 --seed=21      # longer, with a different melody
```

To play your own recording, load it and use it instead of `pluck` or `pad`, for example `pluck = audioLoad( "piano-c4.wav" )`, and give `rootNote` the note it sounds at.

| Setting | Default | |
|---|---|---|
| `--out` | `out/sampler.wav` | Where to write the audio |
| `--bars` | 8 | Length, at least 4. The four chords repeat |
| `--bpm` | 96 | Tempo |
| `--seed` | 7 | Seed for the walk of the melody and its rests |

## What it shows

| Feature | Where |
|---|---|
| A recording as an instrument, played at any note | `audioSampler( audio, rootNote )` and `audioNote( sampler, note )` |
| A chord from one call | `audioNote( note = chord.pad )` |
| A held note from a short recording | `audioSampler( loop = true, loopStart, loopEnd, crossfade )` |
| Attack and release so notes do not click | `attack` and `release` of `audioNote` |
| Velocity as loudness | the accents of the melody |
| Notes placed on a timeline like any sound | `audioAdd( sound = audioNote( ... ) )` |
