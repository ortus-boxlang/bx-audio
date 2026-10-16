---
title: Game audio
order: 8
summary: A real time mixer with buses, loops and crossfades, varied effects, seamless loops and audio sprites.
---

A game needs to play many sounds at once, change them while they play, loop music without a click, and not repeat the same jump sound twenty times. The [game audio example](https://github.com/ortus-boxlang/bx-audio/tree/development/examples/14-game-audio) runs a whole scripted session; this page explains the pieces.

## The mixer

`audioMixer` opens a mixer. Each sound you play has its own volume, pan and pitch, belongs to a **bus** (music, effects, voice) with its own volume, and a master volume sits on top.

<!-- run -->
```js
mixer = audioMixer( output="memory", buses={ music: 0.7, sfx: 1.0 } );

jump = audioGain( audioSweep( 260, 760, 0.18 ), -8 );
song = audioGain( audioOsc( "triangle", audioFreq( "A2" ), 2 ), -10 );

theme = mixer.play( song, { bus: "music", loop: true, fadeIn: 0.3 } );
mixer.render( 0.5 );                                       // let half a second pass
mixer.play( jump, { bus: "sfx", pan: -0.4, pitch: 1.1 } );
mixer.setVolume( "music", 0.3 );                           // duck the music
session = mixer.render( 1.0 );

theme.stop( 0.5 );                                         // fade out and stop
mixer.close();
```

**Outputs.** `output` decides where the sound goes:

| Output | Does |
|---|---|
| `speakers` (default) | Plays to the sound card. Needs a sound card and the JVM's `java.desktop` module |
| `null` | Runs in real time with no sound. For servers and tests that need time to pass |
| `memory` | Nothing runs by itself. You call `mixer.render( seconds )` and get the audio back as a buffer: render a whole scripted session to a file |

**Play options** for `mixer.play( sound, options )`:

| Option | Does |
|---|---|
| `bus` | The bus name, default `main` |
| `volume`, `pan`, `pitch` | Level, -1 left to 1 right, and a speed factor (2 is an octave up) |
| `loop` | Repeat until stopped. `loopStart` and `loopEnd` (seconds in the sound) loop only a part: an intro, then a loop |
| `start` | Begin this many seconds into the sound |
| `fadeIn` | Fade in over seconds |

`play` returns a **playback** you can change while it plays: `setVolume`, `setPan`, `setPitch`, `fadeTo( level, seconds )`, `pause()`, `resume()`, `stop()` and `stop( fadeSeconds )`, with `isPlaying()`, `isFinished()` and `getPosition()`. Changes are applied smoothly across the next block, so there are no clicks. On the mixer, `crossfade( playback, newSound, seconds, options )` swaps music, `setVolume( bus, v )` and `setVolume( v )` set bus and master volume, `stopAll()` and `stopAll( bus, fade )` stop sounds, and `waitUntilSilent( seconds )` waits.

A sound is converted to the mixer's sample rate once and remembered, so playing it again is cheap.

## Play a sound now

```js
audioPlay( audioLoad( "fanfare.wav" ) );                 // waits until it is done
audioPlay( audio=sound, wait=false, volume=0.5 );        // returns a playback at once
```

```bash
bx-audio play fanfare.wav
```

## Seamless loops

A loop clicks when its end does not meet its start. `audioLoopify` folds the end into the start with a crossfade, so the join is smooth, and returns a buffer shorter by the crossfade. The fade keeps the level steady: a held pad does not swell in the middle of the join, and two unrelated sounds do not dip.

<!-- run -->
```js
pad = audioOsc( "saw", 437, 1.1 );            // 437 Hz does not fit a whole number of periods in 1.1 s
loop = audioLoopify( pad, 0.25 );              // 0.85 s, ends exactly where it starts
```

It works best on content that continues across the join: pads, ambience, music with a reverb tail.

## Effects that do not repeat

`audioVariations` makes slightly different copies of a sound: a random pitch (the speed moves with it, like a tape) and level, repeatable with a seed. Pick one at random each time the sound plays.

<!-- run -->
```js
step = audioEnvelope( audioNoise( seconds=0.15, seed=4, color="brown" ), 0.002, 0.05 );
steps = audioVariations( audio=step, count=5, pitchRange=1.5, gainRangeDb=2, seed=1 );
println( steps.len() & " footsteps" );
```

## Audio sprites

Browser and many engine games load all the effects as one file and a map of where each starts, because it is one download. `audioSprite` builds both, and writes the map in the [Howler.js](https://howlerjs.com/) format:

<!-- run -->
```js
sprite = audioSprite(
    sounds = [ jump: audioSweep( 260, 760, 0.18 ), coin: audioOsc( "sine", 988, 0.25 ), boom: audioNoise( seconds=1, color="brown" ) ],
    gap = 0.1,
    path = "sfx/sprite.wav"
);
println( sprite.sprites );
```

This writes `sfx/sprite.wav` and `sfx/sprite.json`:

```json
{ "src": [ "sprite.wav" ], "sprite": { "jump": [ 0, 180 ], "coin": [ 280, 250 ], "boom": [ 630, 1000 ] } }
```

The times are `[ start, duration ]` in milliseconds. Use an ordered struct, written with square brackets, when the order matters. All sounds must have the same channels and sample rate.

## Command line

```bash
bx-audio loopify pad.wav pad-loop.wav --crossfade=0.25
bx-audio sprite sfx/sprite.wav jump.wav coin.wav boom.wav --gap=0.1
```

## Not included

MIDI files are not rendered: the JDK's software synthesizer can only be driven offline through a package the JDK does not export. Sounds are synthesized or loaded from WAV, MP3, FLAC and, with FFmpeg, OGG, Opus and AAC.
