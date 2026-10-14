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

An audio buffer also exposes `getChannels()`, `getFrames()`, `getSampleRate()`, `getDurationSeconds()`, `getPeak()`, `getRms()` and `copy()`.

```js
canvas = audioSilence( seconds=2 )
audioSave( canvas, "out/blank.wav" )
println( audioInfo( "out/blank.wav" ) )
```

## Roadmap

1. DSP: gain, pan, fade, normalize, trim, concat, resample
2. Timeline mixing with a beat grid, ducking, and offline rendering
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
