# Synthwave

A night drive at 100 BPM in A minor (Am, F, C, G). Pulsing eighth note bass, a kick on one and three with a pickup, a gated snare with a huge reverb, wide detuned saw pads with chorus, a bright arpeggio, and a lead melody with chorus and echo.

```bash
boxlang synthwave.bxs                      # 32 bars, about 86 seconds, out/synthwave.wav
boxlang synthwave.bxs --bpm=110 --bars=16  # faster and shorter
```

The arrangement is intro, verse, build, chorus with the lead, and an outro. It scales to any length that is a multiple of 4 bars (at least 16) by putting each section at the same fraction of the track.

| Setting | Default | |
|---|---|---|
| `--out` | `out/synthwave.wav` | Where to write the audio |
| `--bars` | 32 | Length, rounded to a multiple of 4, at least 16 |
| `--bpm` | 100 | Tempo |

## What it shows

| Feature | Where |
|---|---|
| A melody as data: `[ step, chord note, length ]` over four chords, two bars at a time | `lead` |
| A gated snare: the snare gets its own timeline and a very large reverb | `snareStem`, `audioReverb( room=0.9 )` |
| Wide pads and leads from detuned saws with `audioChorus` | `pad()`, `leadNote()` |
| Delay times that follow the tempo: a dotted eighth is `beat * 0.75` | `audioDelay` on the arp and lead |
| A light pump on the bass and pad, enough to breathe but not like house | `audioPump` with small amounts |
| A tom fill and a riser into the chorus | `tom`, `audioDrum( type="riser" )` |

## Checked by measurement

Measured on the 16 bar render that the tests use.

| What | Result |
|---|---|
| Pulse of the low end at the beat | 0.84, and 0.69 at the bar |
| Highs (above 5 kHz) relative to the whole | 22 dB down |
| Transients | 2.7 |
| Harmony | the share of energy on each chord's own notes is 0.44 for Am (its low bass overtones spread energy into other notes), 0.59 for F, 0.64 for C and 0.73 for G |
| Ending | fades to silence, the snare reverb rings on for about 9 seconds |

The tests render a 16 bar version and check these properties, and that the same settings give the identical file.
