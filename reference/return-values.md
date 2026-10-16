---
title: Return values
order: 5
summary: What every function gives back, with the exact keys of every struct.
---

Functions fall into five groups by what they return. Anything not listed here returns a new audio buffer.

## Audio buffers and timelines

| Function | Returns |
|---|---|
| Almost every function that changes or makes sound | A new audio buffer. Methods of the buffer: `getChannels()`, `getSampleRate()`, `getFrames()`, `getDurationSeconds()`, `getPeak()`, `getRms()`. |
| `audioSampler` | A sampler: an instrument to give to `audioNote()`. |
| `audioNote` | An audio buffer with the note or the chord, as long as it is held plus the release. |
| `audioTimeline` | A timeline. `timeline.beat( n )` and `timeline.bar( n )` turn beats and bars into seconds. |
| `audioAdd`, `audioPump`, `audioDuck`, `audioTrackGain` | The same timeline, so calls can be chained. |
| `audioVariations` | An array of audio buffers. |
| `audioMixer` | A mixer. `play( sound, options )` returns a playback, see [Realtime playback](../guides/games.md). |

## Numbers and text

| Function | Returns |
|---|---|
| `audioFreq` | Frequency in Hz, with A4 at 440. |
| `audioEncode` | The bytes of the encoded file, as binary data. |
| `audioDecode` | An audio buffer. |
| `audioSave`, `audioMux` | The absolute path of the file that was written. |
| `audioStorageClear` | Megabytes freed. |
| `audioVoiceRemove` | `true` if something was deleted, `false` if it was not installed. |
| `audioSubtitles`, `audioMarkers` | The text of the file, in the format you asked for. |
| `audioWaveformImage`, `audioSpectrogramImage` | With a `path`: the absolute path of the file written. Without: the SVG as a string, or the PNG as binary data. |
| `audioWaveform` | An array of numbers from 0 (silence) upwards, 1 is full scale. |
| `audioSpectrum` | An array of band levels in dB, lowest frequency first. `-120` means nothing. |
| `audioSpectrogram` | An array of frames, each an array of band levels in dB. |
| `audioBeats` | An array of times in seconds. |

## Structs

All keys below are always present unless marked optional.

### `audioTags( path )`

Reading gives every key, with an empty string for a tag the file does not have.

| Key | Type | Meaning |
|---|---|---|
| `format` | string | `mp3`, `flac` or `wav`. |
| `title`, `artist`, `album`, `albumArtist`, `composer`, `genre`, `year`, `track`, `disc`, `comment` | string | The tag, or `""`. `track` and `disc` can be `"3"` or `"3/12"`. |
| `cover` | struct or null | `{ mimeType, size, data }` with the picture as binary data, `null` when there is none. |
| `chapters` | array | Structs `{ start, end, title }` in seconds, empty when there are none. `end` is `null` when the chapter lasts until the next one starts (FLAC and WAV do not store ends). |

### `audioTags( path, tags )`

Writing gives back what happened.

| Key | Type | Meaning |
|---|---|---|
| `path` | string | The file. |
| `format` | string | `mp3`, `flac` or `wav`. |
| `written` | array | The tags that were stored, in the order `title`, `artist`, `album`, `albumArtist`, `composer`, `genre`, `year`, `track`, `disc`, `comment`, `cover`, `chapters`. |
| `ignored` | array | The tags this format has no place for. A WAV file ignores `albumArtist`, `disc` and `cover`. |

### `audioHttpResponse( source, request )`

| Key | Type | Meaning |
|---|---|---|
| `status` | integer | `200`, `206` (a range), `304` (not modified), `405` (not GET or HEAD) or `416` (range past the end). |
| `statusText` | string | The matching text, such as `Partial Content`. |
| `headers` | struct | Response headers: `Content-Type`, `Accept-Ranges`, `ETag`, `Cache-Control`, `Content-Length`, and when they apply `Content-Range`, `Last-Modified` and `Content-Disposition`. A `304` has no `Content-Type`. |
| `body` | binary | The bytes to send. Empty for `304`, `405`, `416` and `HEAD`. |

### `audioInfo( source, levels=false )`

| Key | Type | Meaning |
|---|---|---|
| `channels` | integer | 1 for mono, 2 for stereo. |
| `sampleRate` | integer | Hz. |
| `frames` | integer | Samples per channel. |
| `seconds` | number | Length. |
| `peak` | number | Highest sample, 0 to 1 for audio that does not clip. Optional: present for a buffer, and for a file only with `levels=true`. |
| `rms` | number | Average level, linear. Optional, same as `peak`. |

A WAV file is described from its header, so a file of any size answers at once. Other formats are decoded.

### `audioLoudness( audio )`

| Key | Type | Meaning |
|---|---|---|
| `integrated` | number | LUFS, gated: the number streaming services use. |
| `range` | number | Loudness range, LU. |
| `truePeakDb` | number | Highest peak between the samples, dBFS. |
| `samplePeakDb` | number | Highest sample, dBFS. |
| `momentaryMax` | number | Loudest 400 ms, LUFS. |
| `shortTermMax` | number | Loudest 3 s, LUFS. |

Silence, or audio shorter than 400 ms, reads `-120`.

### `audioBpm( audio )`

| Key | Type | Meaning |
|---|---|---|
| `bpm` | number | Tempo, `0` if there is no clear pulse. |
| `confidence` | number | 0 to 1. |
| `beats` | array | Beat times in seconds. |

### `audioFft( audio )`

| Key | Type | Meaning |
|---|---|---|
| `frequencies` | array | Hz, `size/2+1` values. |
| `magnitudes` | array | 1 is a full scale sine. |

### `audioDetectSilence( audio )`

An array of structs `{ start, end, duration }` in seconds.

### `audioSpeakCues( text, voice )`

| Key | Type | Meaning |
|---|---|---|
| `audio` | buffer | Mono speech. |
| `cues` | array | Structs `{ start, end, text }` in seconds. Pass them to `audioSubtitles()`. |
| `seconds` | number | Length. |

### `audioSprite( sounds )`

| Key | Type | Meaning |
|---|---|---|
| `audio` | buffer | The joined sounds. |
| `sprites` | struct | Name to `{ start, end, duration }` in seconds. |
| `path`, `mapPath` | string | Optional: the files written, when you gave a `path`. |

### `audioVoices()`

An array of structs: `id`, `name`, `language`, `quality`, `license`, `licenseText`, `shareAlike`, `source`, `sizeMB`, `installed`, `status` (`installed`, `downloadable` or `excluded`), `reasonKind` and `reason` (empty unless excluded).

### `audioLicenses()`

An array of structs: `id`, `name`, `url`, `commercial`, `attribution`, `shareAlike`, `summary` and `voices` (how many offered voices use it).

### `audioSpeechStatus()`

`home`, `engineInstalled`, `engineSizeMB`, `voicesDir` and `voices`, the ids of the installed voices.

### `audioVoiceInstall( name )`

`name`, `installed` and `path` of what was installed.

### `audioStorage()`

`home`, `totalMB` and `folders`, an array of structs `{ kind, path, description, exists, sizeMB }`.

### `audioFfmpeg()`

| Key | Type | Meaning |
|---|---|---|
| `available` | boolean | Is there a working FFmpeg. |
| `source` | string | `configured`, `installed` or `system`. Optional: only when available. |
| `path`, `probePath`, `version` | string | Optional: only when available. |
| `encoders` | array | The encoders it has, from `libvorbis`, `libopus`, `aac`, `libmp3lame` and `flac`. Optional: only when available. |
| `platform` | string | This computer. |
| `installable` | boolean | Is there a download for this computer. |
| `autoInstall` | boolean | May the module download it by itself. |
| `downloadSizeMB` | number | Size of that download. |
| `installDir` | string | Where it would be kept. |
| `problem` | string | Optional: why the `ffmpeg` setting does not work. |

### `audioFfmpegInstall()`

`installed`, `version`, `path` and `probePath`.

### `videoInfo( path )`

| Key | Type | Meaning |
|---|---|---|
| `path`, `format` | string | The file and its container. |
| `seconds` | number | Length. |
| `bitrate`, `sizeBytes` | number | Overall bit rate and file size. |
| `video` | struct or null | `{ codec, width, height, fps }`, `null` when there is no video. |
| `audio` | struct or null | `{ codec, channels, sampleRate }`, `null` when there is no audio. |
| `streams` | array | The raw stream list. |

## Errors

Anything can throw. The types are in [Errors](errors.md).
