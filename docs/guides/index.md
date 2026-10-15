---
title: Guides
order: 1
summary: Task oriented guides, from making a first sound to scoring a video and building game audio.
---

| Guide | You want to |
|---|---|
| [Synthesis and effects](synthesis-and-effects.md) | Make sounds from nothing: oscillators, noise, drums, filters, echo and reverb |
| [Timeline mixing](mixing.md) | Arrange sounds on a beat grid, pump a bass to the kick, duck music under a voice |
| [Trailers and video](trailers-and-video.md) | Score a video: fit music to its length, add a voice-over and captions, master, put the sound on the picture |
| [Loudness and mastering](loudness-and-mastering.md) | Measure loudness and deliver to streaming, podcast or broadcast levels |
| [Time and pitch](time-and-pitch.md) | Make music longer or shorter without changing its pitch, or change pitch without changing length |
| [Speech and subtitles](speech-and-subtitles.md) | Speak text with open license voices, control the pace, and write captions timed to the words |
| [Game audio](games.md) | Play many sounds in real time, loop music, vary effects, and export audio sprites |
| [Analysis](analysis.md) | Find the tempo and beats, look at the spectrum, find silence |
| [Formats and FFmpeg](formats-and-ffmpeg.md) | Read and write WAV, MP3, FLAC, OGG, Opus and AAC, and read the audio of a video |
| [Storage](storage.md) | See and clear what the module downloaded, and move it |
| [The explorer](explorer.md) | Play every example in the browser |

The [function reference](../reference/functions/index.md) and the [command line reference](../reference/cli.md) list every function and command.

## Things that apply everywhere

**Audio buffers.** Sound is held in a buffer: channels, a sample rate and floating point samples. Every function that changes audio returns a new buffer. A buffer has `getChannels()`, `getFrames()`, `getSampleRate()`, `getDurationSeconds()`, `getPeak()`, `getRms()` and `copy()`.

**Repeatable.** Noise and every random choice take a seed, and the same seed gives the same sound on any machine.

**Arguments.** BoxLang does not allow positional and named arguments in the same call. Use all positional, `audioPump( t, "music", times, -4, 0.1 )`, or all named, `audioAdd( timeline=t, sound=kick, at=0, track="drums" )`.

**Decibels.** Gains, thresholds and ceilings are in dB, with 0 dB the loudest a file can hold ("full scale"). Loudness is in LUFS, the unit streaming services and broadcasters measure.
