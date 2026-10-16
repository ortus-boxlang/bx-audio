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

## Long files and memory

**"Java heap space" on a long recording.** A buffer holds every sample as a 32 bit float, which is about 350 KB per second of stereo audio at 44.1 kHz (21 MB a minute, 1.3 GB an hour). Every function that changes audio returns a new buffer, so a chain of calls on a long file holds several copies at once. Mastering (`audioNormalizeLoudness`, `audioMaster`) holds the most, about four times the size of the buffer at its peak.

| Stereo, 44.1 kHz | Buffer | Heap to allow (5 times the buffer) |
|---|---|---|
| 10 minutes | 212 MB | 1 GB |
| 30 minutes | 635 MB | 3 GB |
| 1 hour | 1.3 GB | 6 GB |

A one hour stereo file passed through load, loudness, `audioNormalizeLoudness` and an MP3 encode finished with 6 GB of heap and ran out of memory at 3.4 GB.

Give the JVM more room with `-Xmx` (`java -Xmx8g`, or `JAVA_OPTS` for CommandBox and the BoxLang scripts), and let go of buffers you no longer need by assigning them an empty string. Time grows in proportion to length: loudness takes about 1.4 s for each minute of audio and an MP3 encode 3.5 s. If a file is longer than the memory you can spare, cut it with `audioTrim` into parts, process each and join them with `audioConcat`.

## Storage

**The disk is filling up.** `bx-audio where` shows what the module keeps and `bx-audio clean voices --yes` frees it, see [Storage](guides/storage.md).

## Still stuck

Open an issue at [github.com/ortus-boxlang/bx-audio](https://github.com/ortus-boxlang/bx-audio/issues) with the output of `bx-audio doctor` and `bx-audio version`.
