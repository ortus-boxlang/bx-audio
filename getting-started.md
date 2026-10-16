---
title: Getting started
order: 2
summary: Install the module, make a first sound, and use the bx-audio command line.
---

## Install

You need [BoxLang](https://boxlang.ortusbooks.com/getting-started/installation) on Java 21 or newer.

```bash
# BoxLang operating system binary
install-bx-module bx-audio

# CommandBox
box install bx-audio
```

`install-bx-module` also installs the `bx-audio` command and its bash tab completion. Check the installation:

```bash
bx-audio doctor
```

`doctor` checks MP3, FFmpeg, the sound card, the examples and the explorer, and tells you what is missing and how to fix it. Without the installer, every command also runs as `boxlang module:bxaudio <command>`.

## Your first sound

Audio is passed around as an **audio buffer**. Every function that changes audio returns a new buffer and leaves the one you gave it alone. Save this as `first.bxs` and run `boxlang first.bxs`:

```js
// A two second A minor chord with a fade, saved as WAV and MP3
notes = [ "A3", "C4", "E4" ].map( ( n ) => audioOsc( "saw", audioFreq( n ), 2 ) );
chord = audioFilter( audioMix( notes ), "lowpass", 1800, 0.8 );
chord = audioFade( audioGain( chord, -10 ), 0.05, 0.6, "smooth" );

audioSave( chord, "chord.wav" );
audioSave( chord, "chord.mp3" );
println( audioInfo( "chord.wav" ) );
```

Then try the command line on it:

```bash
bx-audio info chord.wav               # format, length, peak, RMS
bx-audio loudness chord.wav           # LUFS, loudness range, true peak
bx-audio waveform chord.wav           # one line of loudness
bx-audio master chord.wav loud.wav    # reach -14 LUFS with a true-peak limiter
```

## Hear the examples

The module ships fifteen complete pieces. Render one to a file, or open the [explorer](guides/explorer.md) and play them in a browser:

```bash
bx-audio pieces                                # the list
bx-audio render synthwave --out=night.mp3      # synthesize one
bx-audio explorer                              # the web player, on the BoxLang MiniServer
```

The [examples page](examples/index.md) has a player for every piece.

## Speak something

```bash
bx-audio speak "Hello from BoxLang." --voice=joe --out=hello.mp3
```

The first time, this downloads the speech engine (about 148 MB) and the voice (63 MB), checks them against checksums and keeps them in `~/.bxaudio`. After that it works offline. See [Speech and subtitles](guides/speech-and-subtitles.md) and the list of [voices](voices.md).

## What next

- Make music: [Synthesis and effects](guides/synthesis-and-effects.md), [Timeline mixing](guides/mixing.md)
- Make a video soundtrack: [Trailers and video](guides/trailers-and-video.md)
- Make a game sound: [Game audio](guides/games.md)
- Look something up: the [function reference](reference/functions/index.md) and the [command line](reference/cli.md)
