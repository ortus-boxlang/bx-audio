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
| `audioGain( audio, gainDb )` | Change the volume |
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

## Play a recording as an instrument

`audioSampler` turns a recording into an instrument and `audioNote` plays it. A note is the recording played faster or slower, the way a tape keyboard does, so a higher note is also shorter. The notes are audio buffers, so they go on a timeline like any other sound.

The recording here is made in the script so the example runs anywhere: a plucked saw. Use `audioLoad( "piano-c4.wav" )` for a real one.

<!-- run -->
```js
pluck = audioEnvelope( audio=audioOsc( shape="saw", freq=audioFreq( "C4" ), seconds=0.8, harmonics=10 ), attack=0.002, decay=0.2 );
keys  = audioSampler( audio=pluck, rootNote="C4" );

t = audioTimeline( bpm=100, beatsPerBar=4, sampleRate=44100, channels=2 );
melody = [ "C4", "E4", "G4", "E4", "A4", "G4", "E4", "C4" ];
for( i = 1; i <= melody.len(); i++ ){
    audioAdd( timeline=t, sound=audioNote( sampler=keys, note=melody[ i ], velocity=0.8 ), at=t.beat( i - 1 ) / 2, track="melody" );
}
// A chord is the notes together. Give each a lower velocity so the sum is not too loud.
audioAdd( timeline=t, sound=audioNote( sampler=keys, note=[ "C3", "E3", "G3" ], seconds=2, velocity=0.4 ), at=0, track="chord" );

mix = audioRender( timeline=t );
println( numberFormat( mix.getDurationSeconds(), "0.0" ) & " s, peak " & numberFormat( mix.getPeak(), "0.00" ) );
```

| Argument | What it does |
|---|---|
| `rootNote` | The note the recording sounds at: a name such as `C4` (middle C) or a MIDI number where 69 is A4. Fractions detune, `60.1` is ten cents above C4. |
| `seconds` | How long the note is held. `0`, the default, plays the recording through. |
| `velocity` | How hard the note is played, 0 to 1, which is its loudness. |
| `attack`, `release` | Seconds of fade in (default 3 ms, to avoid a click) and of fade out after the note was held (default 50 ms). |

**One recording per few notes.** A recording can be shifted up to three octaves each way, but it sounds most natural within a few semitones. Give several: `audioSampler( audio={ C2: low, C3: lower, C4: middle, C5: high } )` uses the nearest one for each note.

**Held notes.** A recording is as long as it is. For a pad, a string or an organ that has to last, turn on `loop=true`. The whole recording, or the part from `loopStart` to `loopEnd` (in seconds), is repeated until the note ends, with a short `crossfade` that hides the join. Choose a steady part, with no attack and no decay, for the loop.

## Where to go from here

The [examples](../examples/index.md) are complete pieces, each a single readable script. [Timeline mixing](mixing.md) puts sounds in time.
