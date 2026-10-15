---
title: Synthesis and effects
order: 2
summary: Make sound from nothing with oscillators, noise, envelopes and drums, then shape it with filters, echo and reverb.
---

Sounds are made from code, with no samples to download or license. Generators return mono buffers at a known level, so they mix predictably.

## Oscillators and notes

`audioFreq` turns a note name or MIDI number into Hz (A4 is 440). `audioOsc` makes a tone. `sine`, `saw`, `square` and `triangle` are band limited, so they do not alias; `pulse` is the naive digital pulse that arcade chips used, and `width` picks the duty cycle (0.125, 0.25 and 0.5 are the classic ones).

<!-- run -->
```js
note = audioOsc( shape="saw", freq=audioFreq( "A3" ), seconds=1 );     // 220 Hz
sub  = audioOsc( shape="sine", freq=audioFreq( 33 ), seconds=1 );      // MIDI 33 is A1
nes  = audioOsc( shape="pulse", freq=audioFreq( "C5" ), seconds=0.5, width=0.25 );

println( audioFreq( "A4" ) & " " & audioFreq( 60.1 ) );               // 440 and ten cents above C4
audioSave( audioMix( [ note, sub ] ), "tone.wav" );
```

`audioOsc` with `shape="additive"` takes `partials`, a list of `[ multiple, amplitude ]` pairs, for organs and bells.

## Shape it

An envelope gives a tone a start and an end. `audioEnvelope` is a quick linear attack followed by an exponential decay, which is how plucks and drums behave. Filters take away the highs or lows.

<!-- run -->
```js
pluck = audioOsc( "saw", audioFreq( "A1" ), 0.4 );
pluck = audioEnvelope( pluck, 0.003, 0.12 );               // attack 3 ms, decay time constant 120 ms
pluck = audioFilter( pluck, "lowpass", 900, 2 );           // darker, with a resonant edge
audioSave( pluck, "pluck.wav" );
```

| Filter | Does |
|---|---|
| `lowpass` | Removes highs above the cutoff |
| `highpass` | Removes lows below the cutoff |
| `bandpass` | Keeps a band around the cutoff |
| `notch` | Removes a narrow band around the cutoff |

`q` sets the resonance: 0.7071 is flat, higher peaks at the cutoff, and for `bandpass` and `notch` it sets how narrow they are.

## Noise, sweeps and drums

`audioNoise` is white, pink, brown or blue and repeatable with a seed. `audioSweep` glides between two pitches, the core of a kick or a laser. `audioDrum` makes `kick`, `snare`, `hat`, `openhat`, `clap`, `tom`, `crash` and `riser`.

<!-- run -->
```js
kick  = audioDrum( type="kick", decay=0.9 );
snare = audioDrum( type="snare", seed=3 );
wind  = audioFade( audioNoise( seconds=3, color="pink", seed=7 ), 1, 1, "smooth" );
laser = audioSweep( 1800, 200, 0.25 );
```

## Edit

| Function | Does |
|---|---|
| `audioGain( audio, db )` | Change the volume |
| `audioPan( audio, pan )` | Place from -1 (left) to 1 (right) with a constant power law |
| `audioFade( audio, fadeIn, fadeOut, curve )` | Fade in and out, `linear` or `smooth` |
| `audioTrim( audio, start, end )` | Cut a section, in seconds |
| `audioConcat( audios )` | Join buffers end to end |
| `audioMix( audios )` | Play buffers at the same time |
| `audioReverse( audio )` | Play it backwards |
| `audioChannels( audio, n )` / `audioResample( audio, rate )` | Convert mono and stereo, or the sample rate |

## Effects

Echo and reverb return a longer buffer so the tail rings out instead of being cut off. `mix` is a dry and wet balance from 0 to 1.

<!-- run -->
```js
snare = audioChannels( audioDrum( type="snare" ), 2 );
echoed = audioDelay( audio=snare, time=0.28, feedback=0.45, mix=0.3, pingPong=true );
hall   = audioReverb( audio=echoed, room=0.7, damp=0.4, mix=0.25 );
wide   = audioChorus( audio=hall, rate=0.8, depth=0.003, mix=0.4 );
audioSave( audioSaturate( wide ), "snare-hall.wav" );
```

`audioDelay` repeats until they are 60 dB down, at most ten seconds, unless you give a `tail`. `audioReverb` is a Freeverb design: eight damped combs into four allpass filters with a stereo spread. A large room builds level on sustained sounds, so lower the mix. `audioSaturate` rounds off peaks with a tanh curve, a warm limiter for the final mix.

## Where to go from here

The [examples](../examples/index.md) are complete pieces, each a single readable script. [Timeline mixing](mixing.md) puts sounds in time.
