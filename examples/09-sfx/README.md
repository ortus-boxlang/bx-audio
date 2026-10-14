# Game sound effects

Ten sound effects for a game, made from nothing: pulse waves, triangle waves and noise bursts, the sounds arcade machines made. Each is a few lines of synthesis, so you can read how a sound is built and change it, then export the pack as files for your game.

```bash
boxlang sfx.bxs                              # the sampler: all ten, one after the other, out/sfx-sampler.wav
boxlang sfx.bxs --sound=coin --out=coin.wav  # one sound
boxlang sfx.bxs --folder=out/sfx             # an effects pack: every sound as its own file
boxlang sfx.bxs --sound=explosion --seed=7   # a different explosion
```

| Sound | Length | How it is made |
|---|---|---|
| `jump` | 0.20 s | A 25% pulse that glides up from 260 to 640 Hz |
| `coin` | 0.47 s | Two 50% pulse notes, B5 then E6, the second one ringing out |
| `laser` | 0.28 s | A thin 12.5% pulse that falls from 1800 to 200 Hz |
| `explosion` | 1.00 s | Low passed brown noise with a slow decay, over a deep falling thump |
| `powerup` | 0.36 s | A fast rising arpeggio, C major from C5 to G6 |
| `hit` | 0.12 s | A burst of white noise and a short falling thud |
| `blip` | 0.06 s | One short 50% pulse at 880 Hz, for menus |
| `alarm` | 0.64 s | A square wave alternating between 620 and 930 Hz, four times |
| `gameover` | 1.56 s | Three falling triangle notes and a long low one, G4, E4, C4, G3 |
| `footstep` | 0.12 s | A short thud of dark noise |

| Setting | Default | |
|---|---|---|
| `--sound` | `sampler` | One of the sounds above, or `sampler` for all of them with a pause after each |
| `--out` | `out/sfx-<sound>.wav` | Where to write the audio |
| `--folder` | none | Write every sound as `<name>.wav` into this folder instead |
| `--seed` | 1 | The seed of the noise in `explosion`, `hit` and `footstep`. The tones do not change |

Every sound is normalized to the same level (-2 dB) with a few milliseconds of fade at each end, so they sit together in a game and none of them clicks.

## What it shows

| Feature | Where |
|---|---|
| A pitch glide that never clicks: small steps with the phase carried from one to the next | `glide()`, the `phase` argument of `audioOsc` |
| Pulse waves with a duty cycle, and square and triangle waves | `note()`, `audioOsc` |
| Noise shaped by an envelope and a filter | `explosion`, `hit`, `footstep` |
| A sound as a function in a struct, so a sound is a name and a recipe | `sounds` |
| An effects pack exported as one file per sound | `--folder` |

## Checked by measurement

| What | Result |
|---|---|
| Level | every sound peaks at 0.794, which is -2 dB |
| Jump | the pitch is at least 1.4 times higher in the last third than in the first |
| Laser | the pitch falls to under 0.4 of where it started |
| Power up | the pitch at the end is at least 1.8 times the pitch at the start |
| Coin | the first note measures 988 Hz and the second 1319 Hz |
| Alarm | alternates 620, 930 and 620 Hz |
| Explosion | the first third is at least ten times louder than the last |
| Same settings | give the identical file, and the seed changes only the noise sounds |

The tests render every sound and the sampler and check these.
