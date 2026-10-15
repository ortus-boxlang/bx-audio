---
title: Troubleshooting
order: 1
summary: Run bx-audio doctor first. Then find your error here.
---

Start with:

```bash
bx-audio doctor
```

It checks Java, `java.desktop`, MP3, FFmpeg, the sound card, the examples, the MiniServer and the speech setup, and says what to do about each problem. Set `BX_AUDIO_DEBUG=1` to see the full stack trace of an error instead of one line.

## MP3

**"MP3 ... needs ..." or MP3 calls fail.** MP3 reading uses JLayer and writing uses jump3r, two jars in the module's `libs/` folder, and writing needs the JDK's `java.desktop` module. Use a full JDK, not a stripped JRE. Without them WAV and FLAC still work.

**"sample rate ... audioResample".** MP3 has a fixed list of sample rates: 8, 11.025, 12, 16, 22.05, 24, 32, 44.1 and 48 kHz. Resample before saving: `audioSave( audioResample( audio, 44100 ), "x.mp3" )`.

## FFmpeg

**"... needs FFmpeg, which was not found."** OGG, Opus, AAC, M4A, video files and `audioMux` need FFmpeg. Run `bx-audio ffmpeg install` to download one, or install FFmpeg so it is on the `PATH`, or set the `ffmpeg` [setting](reference/configuration.md). If `autoInstall` is `false`, the module never downloads it for you.

**"The configured FFmpeg ... was not found or does not run."** The `ffmpeg` setting or `BX_AUDIO_FFMPEG` points to something that is not FFmpeg. Fix or clear it. A configured path never falls back silently to another FFmpeg.

**"FFmpeg failed: ..."** The message after it is FFmpeg's own. The usual causes are a missing encoder (an old or minimal FFmpeg without `libvorbis`, `libopus` or `aac`; `bx-audio ffmpeg` lists the encoders it has) and a file that is not media.

**There is no download for this computer.** Downloads exist for Linux and macOS on amd64 and arm64 and Windows on amd64. Install FFmpeg yourself on other systems.

**The download failed its check.** Every FFmpeg, speech engine and voice file is checked against a size and checksum. A mismatch deletes the file and stops: run the command again, and if it persists, your network may be rewriting downloads.

## Speech

**"The text to speech engine is not installed."** `autoInstall` is `false`, or you passed `autoInstall=false`. Run `bx-audio install engine` and `bx-audio install joe`.

**"The voice [x] is not offered: ..."** The voice is in the collection but not offered. The message says why, and [Voices](voices.md) lists them all.

**"Unknown voice [x]".** Use a name from `bx-audio voices`. A short name must be unique: `ljspeech` comes in two qualities, so use `en_US-ljspeech-medium`.

**The first call takes a long time.** It is downloading about 210 MB. Pass `onProgress` to `audioSpeak` to show it, or run `bx-audio install engine joe` ahead of time.

**Speech sounds slightly different on another machine.** The model runs the same, but it is floating point on a native library, so tiny differences are possible. Everything else in the module is bit for bit repeatable.

## Playing sound

**"There is no audio output device to play to."** The computer has no sound card or the JVM cannot see one (a server, a container, a CI machine). Use `audioMixer( output="memory" )` and `render( seconds )` to get the audio, or `output="null"` to keep time without sound.

**Nothing is heard but there is no error.** The system output may be muted or set to another device. `bx-audio play file.wav` is the simplest test.

## Time stretch and pitch

**Stretched music sounds rough or echoey.** Keep changes within about 25 percent. For larger changes, cut or loop the music instead (`audioLoopify`).

**A shifted voice sounds like someone else.** Pitch shifting moves the formants of a voice with the pitch. Keep it within a few semitones.

## Files and paths

**Relative paths.** The command line resolves paths against the folder you run it in. In scripts, they follow BoxLang's own rules for the working directory.

**"... positional and named arguments".** BoxLang does not allow both in one call. Use all positional or all named arguments.

## Storage

**The disk is filling up.** `bx-audio where` shows what the module keeps and `bx-audio clean voices --yes` frees it, see [Storage](guides/storage.md).

## Still stuck

Open an issue at [github.com/ortus-boxlang/bx-audio](https://github.com/ortus-boxlang/bx-audio/issues) with the output of `bx-audio doctor` and `bx-audio version`.
