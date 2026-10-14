# Chiptune

Original music for an imaginary 8 bit console, made the way those machines made it: two pulse wave channels (one plays the melody, one plays arpeggios), a triangle wave for the bass, and a noise channel for the drums. Nothing else: no reverb, no filters (except the echo on the cave track), no samples. Every track is original, written for this module.

```bash
boxlang chiptune.bxs                        # the overworld track, 2 loops, out/chiptune-overworld.wav
boxlang chiptune.bxs --track=boss --loops=3
boxlang chiptune.bxs --track=victory --out=jingle.mp3
```

| Track | Style | Tempo | Key | Length of one loop |
|---|---|---|---|---|
| `overworld` | Bouncy, bright adventure theme | 150 BPM | C major | 16 bars |
| `cave` | Sparse and echoing, narrow pulse | 90 BPM | A minor | 8 bars |
| `boss` | Fast and driving, sixteenth note bass | 170 BPM | D minor | 16 bars |
| `title` | A fanfare into a rising theme | 120 BPM | G major | 8 bars |
| `victory` | A short jingle that ends on a held chord | 140 BPM | C major | 8 bars |

| Setting | Default | |
|---|---|---|
| `--track` | `overworld` | One of the tracks above |
| `--out` | `out/chiptune-<track>.wav` | Where to write the audio |
| `--loops` | the track's own (2, or 1 for `title` and `victory`) | How many times the whole form is played |
| `--bpm` | the track's own | Tempo |

## How a track is written

A track is data, not code. It has its chords, one per bar of a four bar phrase, and its melody written as text. Each bar of a melody is 16 tokens, one per sixteenth note: a note name starts a note, `-` holds it, and `.` is a rest.

```
D5 D5 F5 D5 A5 D5 F5 D5 D5 D5 F5 A5 G5 F5 E5 D5      # a bar of the boss theme
```

The arpeggio, the bass and the drums are written from the chords and a one bar pattern each, so a track is a few lines. The script fails with the track, phrase and bar if a bar does not have 16 tokens, so a typo cannot go unnoticed.

## What it shows

| Feature | Where |
|---|---|
| Naive pulse waves with a duty cycle, the 12.5%, 25% and 50% of those consoles | `pulse()`, `audioOsc( shape="pulse", width= )` |
| A notation that is text, read by a short parser | `readBar()` |
| Chord names turned into notes and a bass line | `chordNotes()` |
| A kick made of a fast falling pitch, a snare and a hat from noise | `drumSounds` |
| An echo as the one effect, on the cave track | `audioDelay` |
| Sections as data: a form such as A, A, B, A, repeated | `form`, `loops` |

## Checked by measurement

Measured on one loop of each track, as the tests render them.

| What | Result |
|---|---|
| Highs (above 5 kHz) relative to the whole | between 12 and 17 dB down, the brightest music in the catalog, from the pulse and noise channels |
| Pulse of the low end at the beat | 0.80 for overworld, with a strong bar pulse (0.76) |
| Harmony | on the first beat of a bar, 97% of overworld melody notes and every victory note are in the bar's chord, 87% to 89% for title and cave, 69% for the faster boss line that runs through the scale. All melody notes are in the track's key, with at most a leading tone (G sharp in A minor, C sharp in D minor, F sharp in G major) |
| Ending | silent after the last note, the cave's echo rings on for about 4 seconds |

The tests render every track and check the level, the length, the brightness, the ending, that the same settings give the identical file, and that an unknown track is refused.
