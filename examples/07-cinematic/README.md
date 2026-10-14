# Cinematic

Epic trailer music at 90 BPM in D minor (Dm, Bb, Gm, A). A low drone and a ticking clock open the tension, strings and a driving ostinato build it, a silent beat and a huge boom open the climax with taiko drums and a soaring theme, and the piece ends on a final hit that rings out into a long tail.

```bash
boxlang cinematic.bxs                      # 24 bars, about 71 seconds, out/cinematic.wav
boxlang cinematic.bxs --bpm=100 --bars=32  # faster and longer
```

The arrangement is tension, build, climax and ending, each a whole number of 4 bar phrases that scale with the length. The minimum is 16 bars: 4 bars of each.

| Setting | Default | |
|---|---|---|
| `--out` | `out/cinematic.wav` | Where to write the audio |
| `--bars` | 24 | Length, rounded to a multiple of 4, at least 16 |
| `--bpm` | 90 | Tempo |

## What it shows

| Feature | Where |
|---|---|
| Dynamics: the piece is quiet in the tension and loud in the climax, which is what trailer music is for | `sectionOf()` |
| A true silent beat before the boom: the mix is cut and re-joined, so reverb tails stop too | `audioTrim`, `audioSilence`, `audioConcat` |
| A boom made of a very slow, low kick and a sub sine, with a huge reverb | `boom`, `hitStem` |
| Strings from many detuned saws with a slow attack, an octave higher in the climax | `strings()` |
| A theme as data: one note per beat for each chord | `chords[].theme` |
| Sections that scale with the length, always whole phrases | `tensionEnd`, `buildEnd`, `endStart` |

## Checked by measurement

Measured on the 16 bar render that the tests use.

| What | Result |
|---|---|
| Level, tension against climax | the climax is at least 1.8 times louder than the opening |
| The silent beat | RMS 0, then the boom is above 0.1 RMS in its first half second |
| Transients | 4.2, the booms and taiko |
| Pulse of the low end at the beat | 0.43, not a dance track |
| Ending | the final hit rings out for about 7 seconds, then silence |

The tests render a 16 bar version and check these properties, and that the same settings give the identical file.
