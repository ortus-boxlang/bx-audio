# Ambient

Slow, spacious and without a beat. Four chords in D lydian (Dmaj9, Bm9, Gmaj9, A6) drift past as long pads that fade in over several seconds and overlap, a deep drone holds the root of each chord, sparse bell tones fall into the space, and slow swells of filtered noise move like wind. A very large reverb and a long ping pong echo blur it all together.

```bash
boxlang ambient.bxs                        # 12 bars at 48 BPM, about 79 seconds, out/ambient.wav
boxlang ambient.bxs --bars=24 --seed=11    # longer, with different bell moments
```

Rendering takes about 20 seconds, because the pads are long, detuned and run through a huge reverb.

| Setting | Default | |
|---|---|---|
| `--out` | `out/ambient.wav` | Where to write the audio |
| `--bars` | 12 | Length, at least 4. One bar is five seconds and each chord lasts two bars |
| `--bpm` | 48 | Tempo, which sets the length of a bar and the echo time |
| `--seed` | 5 | Seed for where the bells and their pitches fall |

## What it shows

| Feature | Where |
|---|---|
| Slow attacks: an envelope that takes a third of the chord to rise | `padNote()` with `audioEnvelope` |
| Filter movement without a moving filter: a different cutoff for each note and chord | `cutoff` in the pad loop |
| Inharmonic partials for bell tones: not whole multiples of the pitch | `bell()` with `audioOsc( shape="additive" )` |
| Unpitched texture: brown noise filtered and faded in and out as gusts | the wind loop, `audioNoise( color="brown" )` |
| Very large spaces: room 0.92 reverb with a 9 second tail and a 55% feedback echo | `audioReverb`, `audioDelay` |

## Checked by measurement

| What | Result |
|---|---|
| Notes on the intended chord tones, every 5 second bar | 87 to 98% of the note energy |
| Transients | 0.57 on the full piece (0.7 on the short test render), against 1.8 and above for the other examples |
| Highs (above 5 kHz) relative to the whole | 45 dB down |
| Ending | fades to silence |

The tests render a 4 bar version and check these properties.
