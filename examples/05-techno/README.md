# Techno

A hypnotic four on the floor groove at 132 BPM in F sharp minor. A hard kick on every beat, a rumble bass between the kicks, a resonant acid line, offbeat hats, claps with a short room, and F sharp minor seventh stabs with a dotted echo. The bass, acid line and stabs duck on every kick.

```bash
boxlang techno.bxs                         # 32 bars, about 60 seconds, out/techno.wav
boxlang techno.bxs --bpm=138 --bars=64     # faster and twice as long
```

The arrangement is intro, groove, a breakdown that drops the kick, a peak with the acid line, and an outro. It scales to any length that is a multiple of 4 bars (at least 16) by putting each section at the same fraction of the track.

| Setting | Default | |
|---|---|---|
| `--out` | `out/techno.wav` | Where to write the audio |
| `--bars` | 32 | Length, rounded to a multiple of 4, at least 16 |
| `--bpm` | 132 | Tempo |

## What it shows

| Feature | Where |
|---|---|
| One chord for the whole track, the way techno works: movement comes from rhythm and filter, not harmony | `stabNotes`, `root` |
| A rumble: a low filtered saw plus a sine, placed on the offbeat between kicks | `rumble` |
| An acid line from resonant low pass saws, with the filter opened more on accented steps | `acid()`, `acidAccents` |
| Sidechain pumping on three stems driven by the kick times | `audioPump` |
| A breakdown that takes the kick out, then a riser and a crash into the peak | `sectionOf()`, `audioDrum( type="riser" )` |

## Checked by measurement

Measured on the 16 bar render that the tests use.

| What | Result |
|---|---|
| Pulse of the low end at the beat | 0.90, so a kick on every beat. Weaker at the bar (0.64) because the breakdown has no kick |
| Highs (above 5 kHz) relative to the whole | 22 dB down |
| Transients | 3.5, the most percussive piece in the catalog |
| Breakdown | less than half the level of the groove, the kick is gone |
| Ending | fades to silence |

The tests render a 16 bar version and check these properties, and that the same settings give the identical file.
