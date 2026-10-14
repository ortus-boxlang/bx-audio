# Examples catalog

Every example is a BoxLang script that synthesizes a complete piece of music from code, with no samples and no licensing issues. All of them are original compositions.

Run an example with the module installed:

```bash
boxlang examples/01-pop-drive/pop-drive.bxs
```

Audio is written to `out/` by default and is not committed. Each script takes `--out=`, and most take `--bars=` and `--bpm=`.

| # | Example | Style | Shows |
|---|---|---|---|
| 01 | [Pop Drive](01-pop-drive/) | Upbeat pop, 128 BPM | Voices, beat grid, kick pump, mastering. A checked port of a numpy script |
| 02 | [Lofi](02-lofi/) | Lofi hip hop, 78 BPM with swing | Swing, humanising, stems with their own effects, tape wobble, vinyl crackle |
| 03 | [House](03-house/) | House, 124 BPM | A full arrangement, sidechain pumping on four stems, filtered saw bass, stabs, riser and drop |
| 04 | [Ambient](04-ambient/) | Drone and pads, 48 BPM | Slow envelopes, inharmonic bells, noise swells, very large reverb and echo |

More are planned for techno, synthwave, cinematic trailer music, rock, jazz and chiptune in the style of 8-bit console games, plus game sound effects and a voice-over with ducked music.

`examples/` is covered by a test that renders each script with short settings. It checks the length, the level, that the same settings give the same file, and what each piece sounds like by measurement: how bright it is, whether it pulses on the beat, how percussive it is, and that it fades out. So the catalog cannot silently break or drift.

| Piece | Highs above 5 kHz | Low end pulse at the beat | Transient strength |
|---|---|---|---|
| Pop Drive | −9.5 dB | 0.85 | 3.6 |
| Lofi | −45.8 dB | −0.03 (none) | 1.8 |
| House | −20.5 dB | 0.88 | 2.8 |
| Ambient | −47.1 dB | not meaningful | 0.7 |

These are measured on the short renders that the tests use.
