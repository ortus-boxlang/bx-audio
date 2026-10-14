# Pop Drive

An upbeat 128 BPM pop soundtrack made entirely from synthesis: a four on the floor kick, offbeat hats and claps, a bass line, an arpeggio lead and a pad over a C G Am F progression. Every section is a whole number of bars, so video cuts land on the beat.

```bash
boxlang pop-drive.bxs                                  # 28 bars, 55 seconds, out/pop-drive.wav
boxlang pop-drive.bxs --style=drive --bpm=140          # darker A minor loop with a driving bass and clap rolls
boxlang pop-drive.bxs --bars=16 --out=short.wav
```

It renders in a few seconds. The same settings always give the same file.

## What it shows

| Feature | Where |
|---|---|
| Sound design from code: additive tones, band limited saws, seeded noise drums | `audioOsc`, `audioDrum`, `audioEnvelope` |
| Building a voice once and reusing it | `bassNote()`, `arpNote()`, `padFor()` caches |
| Summing voices into one sound | `audioMix` in the pad |
| Placing sounds on a beat grid with pan and gain | `audioTimeline`, `audioAdd`, `t.beat()` |
| A kick pumping the whole mix | `audioPump` |
| Mastering: fade out then a tanh limiter | `audioFade`, `audioSaturate` |

## Checked against the original

This is a port of a numpy script. Both were rendered at 28 bars and 128 BPM and compared with `compare-with-python.py`:

| | pop | drive |
|---|---|---|
| Length | identical, 2,425,500 frames | identical |
| Peak | 0.900 / 0.900 | 0.900 / 0.900 |
| RMS (original / port) | 0.1669 / 0.1817 | 0.2100 / 0.2091 |
| Octave bands, 20 Hz to 20 kHz | every band within 0.5 dB | every band within 0.5 dB |
| Loudness envelope correlation | 0.999 | 0.999 |
| Kick and bass envelope correlation | 1.000, lag 0 ms | 1.000, lag 0 ms |
| Hats and claps envelope correlation | 0.989 | 0.990 |

The port is not bit identical, because noise comes from a different random generator and the pan law differs slightly. The pop style is a steady 0.75 dB louder than the original in every section. That is a constant offset from the final peak normalisation, which is set by the single loudest random noise sample, so it is not a mix balance difference.
