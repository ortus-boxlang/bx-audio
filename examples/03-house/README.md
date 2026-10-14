# House

A four on the floor dance track at 124 BPM in A minor (Am7, Fmaj7, Cmaj7, G6): a punchy kick on every beat, offbeat open hats, claps on two and four, shakers, a rolling offbeat bass, syncopated chord stabs with an echo, a pad, and an arpeggio in the drop. The bass, stabs, pad and arpeggio duck on every kick, which is what makes house pump.

```bash
boxlang house.bxs                          # 32 bars, about 63 seconds, out/house.wav
boxlang house.bxs --bpm=128 --bars=64      # faster and twice as long
```

The arrangement is intro, build, groove, breakdown with a riser, the drop, and an outro. It scales to any length that is a multiple of 4 bars (at least 16) by putting each section at the same fraction of the track.

| Setting | Default | |
|---|---|---|
| `--out` | `out/house.wav` | Where to write the audio |
| `--bars` | 32 | Length, rounded to a multiple of 4, at least 16 |
| `--bpm` | 124 | Tempo |

## What it shows

| Feature | Where |
|---|---|
| A whole arrangement from one function: which section a bar belongs to | `sectionOf()` |
| Sidechain pumping on four stems at once, driven by the kick times | `audioPump` on the bass, stab, pad and arp timelines |
| Filtered saws with a sine sub under them for the bass | `bassNote()`, `audioFilter` with resonance |
| Chord stabs and pads from detuned saws, cached per chord | `stab()`, `pad()` |
| Delay times that follow the tempo: a dotted eighth is `beat * 0.75` | `audioDelay` on the stabs and arp |
| Build ups and impacts: a two bar riser into the drop and a crash on its first bar | `audioDrum( type="riser" )`, `crash` |

## Checked by measurement

| What | Result |
|---|---|
| Pulse of the low end at the beat | 0.88, so a kick on every beat |
| Highs (above 5 kHz) relative to the whole | 20 dB down, bright from the hats, claps and shakers |
| Transients | 2.8, the most percussive of the new examples |
| Loudness by bar | about −16 dBFS RMS in the intro, about −12.5 through the groove and the drop (the drop peaks at −11.5 on its first bar), −13 to −14 in the breakdown, then stepping down to −16 and fading out in the outro |
| Ending | fades to silence |

The chord notes are the literal MIDI notes in `chords` and the pitch generation is covered by the synth tests. A pitch class analysis of the finished mix is blurred by the saws' overtones and the pad overlapping into the next chord, so it is not used as a check here.

The tests render a 16 bar version and check these properties.
