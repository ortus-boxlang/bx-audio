# ⚡︎ BoxLang Module: BoxLang Audio

```
|:------------------------------------------------------:|
| ⚡︎ B o x L a n g ⚡︎
| Dynamic : Modular : Productive
|:------------------------------------------------------:|
```

<blockquote>
	Copyright Since 2023 by Ortus Solutions, Corp
	<br>
	<a href="https://www.boxlang.io">www.boxlang.io</a> |
	<a href="https://www.ortussolutions.com">www.ortussolutions.com</a>
</blockquote>

<p>&nbsp;</p>

Audio synthesis, mixing, analysis, text-to-speech and playback for BoxLang, written for the JVM with no native tools to install.

> This module is under active development. What works today is listed below, the rest of the roadmap follows it.

## Installation

```bash
install-bx-module bx-audio
```

## What works today

| Function | Purpose |
|---|---|
| `audioSilence( seconds, channels=2, sampleRate=44100 )` | Create a blank audio buffer |
| `audioLoad( path )` | Read a WAV file (PCM 8/16/24/32 bit and 32/64 bit float) |
| `audioSave( audio, path, bitDepth=16 )` | Write a WAV file as 16 bit, 24 bit or 32 bit float |
| `audioInfo( audioOrPath )` | Channels, sample rate, frames, seconds, peak and RMS |
| `audioGain( audio, db )` | Change the volume in decibels |
| `audioPan( audio, pan )` | Place a sound from -1 (left) to 1 (right) with a constant power law. Mono becomes stereo, stereo acts as a balance |
| `audioFade( audio, fadeIn=0, fadeOut=0, curve="linear" )` | Fade in and out, `curve` is `linear` or `smooth` |
| `audioNormalize( audio, targetDb=-1 )` | Scale the peak to a target level |
| `audioTrim( audio, start=0, end )` | Cut a section, in seconds |
| `audioConcat( audios )` | Join buffers end to end, formats must match |
| `audioReverse( audio )` | Play it backwards |
| `audioChannels( audio, channels )` | Convert between mono and stereo |
| `audioResample( audio, sampleRate )` | Change the sample rate with a windowed sinc filter, with anti-aliasing when lowering it |

Every function returns a new buffer and leaves its input untouched.

### Timeline mixing

A timeline holds named tracks at one sample rate and a tempo grid. Sounds are placed on tracks, ducking and track volumes are applied when it is rendered, so the stored tracks are never altered.

| Function | Purpose |
|---|---|
| `audioTimeline( bpm=120, beatsPerBar=4, sampleRate=44100, channels=2 )` | Create a timeline. `t.beat( n )` and `t.bar( n )` turn beats and bars into seconds |
| `audioAdd( timeline, sound, at=0, track="main", db=0, pan=0 )` | Place a sound at a time. Other sample rates are converted automatically, mono sounds are panned |
| `audioBeats( timeline, start=0, end, step=1 )` | List moments on the beat grid in seconds, ready for `audioAdd( at= )` or `audioPump( times= )` |
| `audioTrackGain( timeline, track, db )` | Set the volume of a whole track |
| `audioPump( timeline, track, times, amountDb=-5, recovery=0.18 )` | Dip a track at given moments, like a kick drum sidechain |
| `audioDuck( timeline, track, trigger, amountDb=-12, thresholdDb=-40, attack=0.01, release=0.3 )` | Lower a track while another is loud, like music under a voice-over |
| `audioRender( timeline, tail=0 )` | Mix all tracks down to one buffer. The timeline can be changed and rendered again |
| `audioSaturate( audio, drive=2.2, level=0.9 )` | Peak-normalize and round off peaks with a tanh curve, a warm limiter for the final mix |

> BoxLang does not allow mixing positional and named arguments in one call. Use all positional (`audioPump( t, "music", times, -4, 0.1 )`) or all named (`audioAdd( timeline=t, sound=kick, at=t.beat( 4 ), track="drums" )`).

```js
t = audioTimeline( bpm=128 );
voice = audioLoad( "voice.wav" );   // any sample rate, it is converted for you

audioAdd( timeline=t, sound=music, at=0, track="music", db=-10 );
audioAdd( timeline=t, sound=voice, at=t.bar( 2 ), track="voice" );

audioPump( t, "music", audioBeats( t, 0, 64 ), -4, 0.15 );   // pump on every beat
audioDuck( t, "music", "voice", -9 );                         // and duck under the voice

mix = audioFade( audioRender( timeline=t, tail=1 ), 0, 1, "smooth" );
audioSave( audioSaturate( mix ), "out/trailer-mix.wav" );
```

An audio buffer also exposes `getChannels()`, `getFrames()`, `getSampleRate()`, `getDurationSeconds()`, `getPeak()`, `getRms()` and `copy()`.

```js
// Bring a mono 22.05kHz voice-over up to a 44.1kHz stereo mixdown format
voice = audioLoad( "voice.wav" );
voice = audioResample( voice, 44100 );
voice = audioChannels( voice, 2 );
voice = audioNormalize( voice, -3 );
voice = audioFade( audio=voice, fadeIn=0.05, fadeOut=0.3, curve="smooth" );
audioSave( voice, "out/voice-stereo.wav", 24 );
println( audioInfo( "out/voice-stereo.wav" ) );
```

## Roadmap

1. ~~DSP: gain, pan, fade, normalize, trim, concat, reverse, channels, resample~~ (done)
2. ~~Timeline mixing with a beat grid, ducking, and offline rendering~~ (done)
3. Synthesis: oscillators, envelopes, sequencing, drum voices
4. A catalog of example music scripts (pop, lofi, house, chiptune, ambient, cinematic and more, all original compositions)
5. Text-to-speech through a pluggable provider, with a content-hash cache
6. Analysis: loudness, silence detection, waveform data, FFT
7. Realtime playback
8. MP3 read and write as optional codec jars (LGPL, shipped separately from the module)

## Licensing

The module is Apache 2.0. Optional MP3 codecs are LGPL libraries shipped as separate, replaceable jars and are never bundled into the module jar.

## Development

```bash
./gradlew downloadBoxLang   # once, fetches the BoxLang jar used for compiling and tests
./gradlew spotlessApply test
```

## Ortus Sponsors

BoxLang is a professional open source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com).
