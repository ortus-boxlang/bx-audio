---
title: Conventions
order: 4
summary: How arguments are named, which units they use, and what the functions give back.
---

The same rules hold for every function, so a name tells you the unit.

## Units

| Suffix or name | Unit | Example |
|---|---|---|
| `...Db` | Decibels. Gain is relative, a level is relative to full scale (dBFS). | `gainDb`, `thresholdDb`, `ceilingDb`, `truePeakDb`, `targetDb`, `makeupDb`, `amountDb` |
| `...Ms` | Milliseconds, on the dynamics processors. | `attackMs`, `releaseMs`, `lookAheadMs` |
| `...Lufs` | Loudness in LUFS. | `targetLufs` |
| `...Hz` | Hertz. | `minHz`, `maxHz` |
| `seconds`, `at`, `tail`, `fadeIn`, `fadeOut`, `crossfade`, `pad` | Seconds. Anything about time that has no `Ms` is in seconds. | `at=2.5` |
| `level`, `mix`, `feedback`, `volume`, `pan`, `peak`, `rms` | Linear numbers, not decibels. `level`, `mix`, `feedback` and `volume` run from 0 to 1 (`audioDelay` takes a feedback up to 0.95), `pan` from -1 to 1. | `mix=0.3` |

A value in decibels is never a bare `db`, `gain` or `truePeak`. If the name has no `Db`, it is not decibels.

## Names

- Arguments are camelCase and are the same in every function that has them: `audio` is always the audio, `seconds` always a length, `sampleRate` always Hz.
- Functions that change audio give back a new buffer. The audio you passed in is never modified.
- Functions that build a timeline (`audioAdd`, `audioPump`, `audioDuck`, `audioTrackGain`) give back the same timeline so calls can be chained.
- Errors always have a `bxaudio.` type, see [Errors](errors.md).
- What each function returns is in [Return values](return-values.md).

## Command line

Command line options follow the same names: `--targetDb`, `--thresholdDb`, `--truePeakDb`. An option a command does not have is an error, so a misspelled or old option is never silently ignored.
