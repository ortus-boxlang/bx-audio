# Rock

A driving rock song at 128 BPM in E. Distorted power chords (palm muted chugging in the verse, open and ringing in the chorus), a bass guitar locked to the roots, a drum kit with fills and crashes, and a pentatonic guitar solo improvised by a seeded random number generator.

```bash
boxlang rock.bxs                          # 32 bars, about 65 seconds, out/rock.wav
boxlang rock.bxs --bars=48 --bpm=140      # longer and faster
boxlang rock.bxs --seed=9                 # a different solo
```

The arrangement is an intro (the riff alone for two bars, then the band), a verse, a chorus, a solo, and an ending on one big chord. It scales to any length that is a multiple of 4 bars (at least 16) by putting each section at the same fraction of the track.

| Setting | Default | |
|---|---|---|
| `--out` | `out/rock.wav` | Where to write the audio |
| `--bars` | 32 | Length, rounded to a multiple of 4, at least 16 |
| `--bpm` | 128 | Tempo |
| `--seed` | 3 | The seed of the solo. Nothing else changes |

| Section | Chords (one per bar) | Guitars |
|---|---|---|
| Intro | E E E E | Palm muted riff |
| Verse | E E G A | Palm muted eighth notes |
| Chorus | A B E E | Open power chords on beats one and three |
| Solo | E E G A | The verse riff, under the solo |
| Ending | A B E E, then one E | Open chords, then a long final E with a crash |

## How it is made

A guitar is not recorded, it is built. A power chord is the root, the fifth and the octave, each as two slightly detuned saw waves. All the chords of one guitar are mixed first and then saturated very hard, which is where the roughness of distortion comes from (the tones are distorted together, not one by one), and then cut like a speaker cabinet with a low pass at 4.2 kHz and a high pass at 90 Hz. Two takes, one in each ear, a few milliseconds apart, make it wide.

## What it shows

| Feature | Where |
|---|---|
| Distortion as saturation of a whole track, then a cabinet filter | `distort()`, `audioSaturate` |
| Two guitar takes panned apart | `guitarL`, `guitarR` |
| A section arc that scales with the length | `sectionOf()` |
| A seeded pentatonic solo that lands on the root or fifth on the strong beats | the `solo` block |
| Fills and crashes placed from the section boundaries | `startOfSection`, `nextSection` |

## Checked by measurement

Measured on the default 32 bars, as the tests render them.

| What | Result |
|---|---|
| Level by bar | the intro riff alone is about 20 dB below full scale RMS, the verse about 13 dB, the chorus about 10 dB, the solo about 12 dB, so the song gets louder into the chorus |
| Loudness and compression | RMS about -12 dBFS and a crest factor of 11 dB, a loud compressed sound |
| Highs (above 5 kHz) relative to the whole | 26 dB down, the cabinet filter |
| Same chord | two verse bars with the same chord are the same music (their samples correlate above 0.99), and a bar with another chord is not (below 0.9) |
| Seed | the music before the solo is identical whatever the seed, the solo bars are not, and the same seed gives the identical file |

Chord pitches are not asserted from the finished mix: heavy distortion puts energy at the sums and differences of the notes, and the fifth of an E power chord is the root of a B one, so pitch analysis of the output cannot tell the chords apart reliably. The chords are in the data, and the tests check that bars with the same chord are alike and that a chord change sounds different.

The tests render the full 32 bars and check these.
