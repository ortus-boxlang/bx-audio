---
title: Time and pitch
order: 6
summary: Change speed without pitch, pitch without speed, and fit audio to an exact length.
---

Slowing a recording down also lowers its pitch, because that is how playback speed works. Time stretching changes the length and keeps the pitch; pitch shifting changes the pitch and keeps the length. BoxLang Audio does both in plain Java with WSOLA (waveform similarity overlap-add).

<!-- run -->
```js
music = audioOsc( "saw", audioFreq( "A3" ), 4 );

faster  = audioStretch( music, 1.25 );        // 25 percent faster: 3.2 s, same pitch
slower  = audioStretch( music, 0.8 );         // 5 s, same pitch
exact   = audioFitTo( music, 3.5 );           // exactly 3.5 seconds
up      = audioPitch( music, 3 );             // a minor third higher, still 4 s
octave  = audioPitch( music, -12 );           // an octave lower
```

| Function | Range | Does |
|---|---|---|
| `audioStretch( audio, rate )` | 0.25 to 4 | Speed factor. `2` is twice as fast and half as long |
| `audioFitTo( audio, seconds )` | a quarter to four times the length (otherwise `bxaudio.invalidArgument`) | Stretch to an exact length |
| `audioPitch( audio, semitones )` | plus or minus 24 | Shift the pitch, length unchanged |

## How well does it work

WSOLA cuts the audio into overlapping windows of about 50 ms and lays them out again at a new spacing, each shifted a little to where it lines up best with the one before. That keeps tone and transients natural for moderate changes:

- Within about 25 percent the result is clean on music and speech. Past that, expect some roughness on dense chords and a slight echo on sustained notes.
- Changes beyond 2 times or under 0.5 times are made in two passes.
- Pitch shifting stretches by the opposite amount and then plays the result at the new speed. The character of a voice (its formants) moves with the pitch, so a voice shifted far sounds like a different, smaller or larger, person.

## Cutting music to a video

`audioFitTo` is for matching a picture. If the music is much too short, loop part of it instead of stretching it a long way: build a loop with [`audioLoopify`](games.md#seamless-loops) and repeat it.

## Command line

```bash
bx-audio stretch cue.wav cue-fit.wav --seconds=30
bx-audio stretch cue.wav slower.wav --rate=0.8
bx-audio pitch song.wav lower.wav --semitones=-2
```
