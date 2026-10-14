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

More are planned for lofi, house, techno, synthwave, ambient, cinematic trailer music, rock, jazz and chiptune in the style of 8-bit console games, plus game sound effects and a voice-over with ducked music. They are added as the effects they need (delay, reverb, chorus) land.

`examples/` is covered by a test that renders each script with short settings and checks its length, level and that it is repeatable, so the catalog cannot silently break.
