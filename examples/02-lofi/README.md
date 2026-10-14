# Lofi

A slow, dusty beat to relax to: 78 BPM with swing, jazzy ii V I vi chords (Dm9, G13, Cmaj9, Am9) on a soft electric piano, a lazy boom bap drum pattern, a pentatonic melody through a dotted eighth echo, a round sub bass, and vinyl crackle and tape hiss.

```bash
boxlang lofi.bxs                           # 16 bars, about 53 seconds, out/lofi.wav
boxlang lofi.bxs --bpm=70 --swing=0.62     # slower and lazier
boxlang lofi.bxs --seed=21                 # same song, different human feel and crackle
```

The same settings always give the same file. The seed changes the small timing and volume differences, which strum, and where the crackle lands.

| Setting | Default | |
|---|---|---|
| `--out` | `out/lofi.wav` | Where to write the audio |
| `--bars` | 16 | Length, at least 6. The beat starts at bar 3, the melody plays from bar 5 and everything thins out for the last 2 bars |
| `--bpm` | 78 | Tempo |
| `--swing` | 0.58 | 0.5 is straight, higher swings the sixteenth notes harder |
| `--seed` | 7 | Seed for the human feel and the crackle |

## What it shows

| Feature | Where |
|---|---|
| A swing grid: odd sixteenths land late | `swung()` |
| Humanising with a seeded random generator: timing, volume, strummed chords | `rnd()`, `jitter()` |
| Each part on its own timeline with its own effects, then mixed | `newStem()`, `audioMix` |
| Chorus as tape wobble, a gentle pump on the kick, a dotted eighth ping pong echo | `audioChorus`, `audioPump`, `audioDelay` |
| Softening drums and a master with `audioFilter` instead of samples | `audioFilter` on every drum voice and the master |
| Texture from noise: crackle from short noise ticks at random moments, hiss from pink noise | `crackleBank`, `audioNoise` |

## Checked by measurement

| What | Result |
|---|---|
| Notes on the intended chord tones, every bar | 93 to 97% of the note energy |
| Highs (above 5 kHz) relative to the whole | 44 dB down, which is the dark, muffled sound |
| Pulse at the beat / at the bar | none (−0.03) / strong (0.73), so boom bap and not four on the floor |
| Transients | soft (1.8, against 2.8 for the house example and 0.7 for the ambient one) |
| Ending | fades to silence |

The tests render a 10 bar version and check the same properties.
