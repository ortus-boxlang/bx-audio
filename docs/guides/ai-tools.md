---
title: AI agent tools
order: 16
summary: Let a BoxLang AI agent trim, mix, master, analyze and speak audio, through the audio tools bx-audio adds to the bx-ai tool registry.
---

When the [BoxLang AI module](https://ai.ortusbooks.com) (`bx-ai`) is installed, BxAudio offers it 26 audio tools. Any agent can use them to inspect, edit, master and create audio files, with no code from you beyond naming the tools the agent may use.

You do not install anything extra. Install both modules, and the tools are in the bx-ai registry under the module name `bxaudio`:

```bash
install-bx-module bx-ai
install-bx-module bx-audio
```

Without bx-ai nothing changes: the module loads as before and the tools are simply not offered.

## Registered is not given

Registering a tool only makes it available by name. An agent can call a tool only when its `tools` list names it. An agent with no `tools`, or one that names none of these, never sees them.

<!-- Not run: an agent needs a configured AI provider -->
```js
agent = aiAgent(
    name : "sound-editor",
    tools: [ "audioInfo@bxaudio", "audioTrim@bxaudio", "audioNormalize@bxaudio" ]
)

agent.run( "Cut the first 5 seconds off intro.wav and normalize it" )
```

Pick the tools with the registry, by name:

```js
// Is it there?
aiToolRegistry().has( "audioTrim@bxaudio" )

// Every audio tool, to hand all of them to an agent
agent = aiAgent( name : "studio", tools: aiToolRegistry().getByModule( "bxaudio" ) )
```

Give an agent only what its job needs. A transcription helper has no use for `audioMux`.

## How the tools work

An agent cannot pass an audio buffer, so every tool works on files:

- It reads a file by **path**.
- It writes a **new** file and returns `{ path, seconds, channels, sampleRate }`.
- With no `outputPath` it chooses one in the output folder, named after the input, what was done and a short id, such as `intro-trim-3fa9c01b.wav`.
- The result keeps the input's format when it is WAV, MP3 or FLAC, and is WAV otherwise.
- Tools that inspect (`audioInfo`, `audioLoudness`, `audioBpm`, `audioDetectSilence`, `audioReadTags`, `videoInfo`) return what they found as a struct or an array.

Chain tools by passing each result's `path` to the next. An agent does this by itself.

## What an agent can touch

Paths from an agent are untrusted, so every path is checked before it touches the disk:

- It is made absolute and cleaned of `.` and `..`, and symlinks are followed, so a link or `../` cannot lead out of an allowed folder.
- It must lie under an allowed folder. With nothing configured these are the system temp folder and the working directory of the process.
- A new file is never written over an existing file, nor over its own input. The tool fails and says to choose another path, and the agent can.
- `audioReadTags` only reads. No tool changes a file in place.
- Lists are capped: 64 files for `audioConcat` and `audioMix`, 3600 seconds for `audioSilence`.

To widen or narrow the folders, set `ai.allowedPaths`, see [Configuration](../reference/configuration.md#ai-tool-settings).

## The tools

### Inspect

| Tool | Arguments | Returns |
|---|---|---|
| `audioInfo` | `path`, `levels` | channels, sample rate, frames, seconds, and peak and rms with `levels` |
| `audioLoudness` | `path` | integrated LUFS, loudness range, true peak |
| `audioBpm` | `path`, `minBpm`, `maxBpm` | `bpm`, `confidence`, `beatCount` and the first beats |
| `audioDetectSilence` | `path`, `thresholdDb`, `minSeconds` | an array of `{ start, end, duration }` |
| `audioReadTags` | `path` | title, artist, album and the other tags |
| `audioWaveformImage` | `path`, `outputPath`, `width`, `height` | the path of a PNG |
| `audioSpectrogramImage` | `path`, `outputPath`, `width`, `height` | the path of a PNG |
| `videoInfo` | `path` | length, size and the streams of any media file. Needs FFmpeg |

### Edit

| Tool | Arguments |
|---|---|
| `audioTrim` | `path`, `start`, `end`, `outputPath` |
| `audioTrimSilence` | `path`, `thresholdDb`, `pad`, `outputPath` |
| `audioConcat` | `paths`, `outputPath` |
| `audioMix` | `paths`, `outputPath` |
| `audioFade` | `path`, `fadeIn`, `fadeOut`, `curve`, `outputPath` |
| `audioStretch` | `path`, `rate`, `outputPath` (speed, same pitch) |
| `audioPitch` | `path`, `semitones`, `outputPath` (pitch, same length) |
| `audioReverse` | `path`, `outputPath` |
| `audioGain` | `path`, `gainDb`, `outputPath` |
| `audioConvert` | `path`, `format`, `sampleRate`, `channels`, `bitrate`, `outputPath` |

### Master

| Tool | Arguments |
|---|---|
| `audioNormalize` | `path`, `targetDb`, `outputPath` |
| `audioMaster` | `path`, `target` (a preset or LUFS), `truePeakDb`, `glue`, `outputPath` |
| `audioEq` | `path`, `bands`, `outputPath` |
| `audioReverb` | `path`, `room`, `damp`, `mix`, `outputPath` |

### Create

| Tool | Arguments |
|---|---|
| `audioVoices` | `language`, `installed`. Lists the voices `audioSpeak` can use |
| `audioSpeak` | `text`, `voice`, `speed`, `outputPath`. Text to speech on this machine, no cloud service |
| `audioSilence` | `seconds`, `channels`, `sampleRate`, `outputPath` |
| `audioMux` | `video`, `audio`, `keepOriginal`, `offset`, `outputPath`. Needs FFmpeg |

Each tool has the same name as the function it wraps, so the [function reference](../reference/functions/index.md) explains its arguments in depth.

## Local speech next to cloud speech

bx-ai has its own `speak@bxai`, which calls a cloud provider. `audioSpeak@bxaudio` runs a [Piper](speech-and-subtitles.md) voice on your machine, offline once the voice is downloaded, and costs nothing per call. Name the one you want in the agent's `tools`, or both and let the model choose from their descriptions.

The first `audioSpeak` of a voice may download the engine and the voice, unless `autoInstall` is `false`, see [Storage](storage.md).

## Example: a podcast clean up

```js
agent = aiAgent(
    name        : "podcast-editor",
    instructions: "You edit podcast recordings. Work on copies, report levels before and after.",
    tools       : [
        "audioInfo@bxaudio", "audioLoudness@bxaudio", "audioTrimSilence@bxaudio",
        "audioEq@bxaudio", "audioMaster@bxaudio"
    ]
)

agent.run( "Take episode-12.wav, remove the silence at both ends, cut the rumble below 80 Hz and master it for podcast delivery. Tell me the loudness before and after." )
```

A run like this calls `audioLoudness`, `audioTrimSilence`, `audioEq` with a `highpass` band at 80 Hz, `audioMaster` with `podcast`, and `audioLoudness` again.

## Turning it off

Set `ai.enabled` to `false` and the tools are never registered. See [Configuration](../reference/configuration.md#ai-tool-settings). Unloading the module removes them from the registry.

## Troubleshooting

| You see | Cause |
|---|---|
| `RegistryItemNotFoundException` for `audioTrim@bxaudio` | bx-ai or bx-audio is not loaded, or `ai.enabled` is `false`. Check `aiToolRegistry().has( "audioTrim@bxaudio" )` |
| `AudioToolAccessDenied` | The path is outside the allowed folders. Move the file, or add its folder to `ai.allowedPaths` |
| `AudioToolExists` | The output path is taken. Leave `outputPath` empty, or choose a new one |
| `AudioToolNotFound` | There is no file at the input path |
| `AudioToolInvalidArgument` | An empty list, too many files, a silence that is too long, or an output path with no extension |
