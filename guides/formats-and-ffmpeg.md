---
title: Formats and FFmpeg
order: 10
summary: WAV, MP3 and FLAC are built in. OGG, Opus, AAC and video use FFmpeg, which is found on your computer or downloaded.
---

| Format | Read | Write | Needs |
|---|---|---|---|
| WAV (PCM 8, 16, 24, 32 bit and float) | yes | 16, 24 bit and float | nothing |
| FLAC | yes | 16 and 24 bit | nothing |
| MP3 | yes | yes | JLayer and jump3r (LGPL jars shipped with the module) and the JDK's `java.desktop` |
| OGG Vorbis, Opus, AAC, M4A, and any other format | yes | `.ogg` `.oga` `.opus` `.m4a` `.mp4` `.aac` | FFmpeg |
| The audio of any video | yes | the sound onto a video | FFmpeg |

Files are read by their content, not their extension, and written by the extension of the name you give. `audioSave( audio, "x.ogg", 16, 96 )` writes Ogg Vorbis at 96 kbps, and `audioLoad( "clip.mp4" )` reads the sound of a video.

## MP3 and its length

An MP3 encoder adds silence at the start (its delay) and pads the end to a whole frame, and a decoder adds a little delay of its own, so a plain round trip gives a sound that is longer and starts about 50 ms late. BxAudio writes the standard Info frame with a LAME tag that records the delay and the padding, and cuts them when it reads: `audioSave` to MP3 and `audioLoad` give back the same number of frames, with a sound where it was. FFmpeg and other players that read the tag play the file gapless too. A file from another LAME or FFmpeg encoder is cut by its own tag. A file with no tag (some older encoders) is read as it is, since nothing says how much is padding.

## FLAC

The FLAC encoder and decoder are written for this module, in plain Java. Written files were checked against FFmpeg: it decodes them to identical samples, with the checksums valid. Music compresses to 40 to 55 percent of the size of the WAV. The decoder reads FLAC from other encoders too, including their LPC compression.

## FFmpeg

FFmpeg is a separate program that the module starts as a process. It is looked for in this order:

1. The module setting `ffmpeg` (a path to the program, or to the folder that has it)
2. The `BX_AUDIO_FFMPEG` environment variable
3. The copy installed in `~/.bxaudio/ffmpeg`
4. The `PATH`

If none is found, the module can download one for your computer (about 66 MB on Linux and macOS, 110 MB on Windows) into `~/.bxaudio/ffmpeg`. Every file is checked against a pinned size and SHA-256 before it is used, and nothing is installed if a check fails. This happens by itself the first time something needs FFmpeg, unless `autoInstall` is `false`. To do it ahead of time or see the state:

```bash
bx-audio ffmpeg            # is it available, which version, which encoders
bx-audio ffmpeg install    # download it now, with progress
```

```js
status = audioFfmpeg();     // available, source, path, version, encoders, installable, downloadSizeMB
audioFfmpegInstall( ( name, done, total ) => println( name & " " & int( done * 100 / total ) & "%" ) );
```

Downloads are available for Linux and macOS on amd64 and arm64 (builds by [martin-riedl.de](https://ffmpeg.martin-riedl.de/)) and Windows on amd64 (builds by [gyan.dev](https://www.gyan.dev/ffmpeg/builds/)). On any other system install FFmpeg yourself, or point the module at it.

> **Licensing.** FFmpeg and its builds carry their own licenses, LGPL or GPL depending on how they were built, and these builds include GPL components. The module never bundles or links FFmpeg. You download it from its provider onto your computer, and it runs as its own program. If your use needs a particular FFmpeg license, install your own build and set `ffmpeg` to it. See [Licenses](../licenses.md).

## Video

```js
info = videoInfo( "clip.mp4" );       // seconds, size, video {codec,width,height,fps}, audio {codec,channels,sampleRate}
sound = audioLoad( "clip.mp4" );      // the first audio stream, as 32 bit float at its own rate
audioMux( video="clip.mp4", audio=newSound, output="scored.mp4" );
```

`audioMux` copies the picture without re-encoding it. `webm` output uses Opus, everything else AAC. See [Trailers and video](trailers-and-video.md).

## Markers

`audioMarkers` writes the points in time an editor or a player shows, from `{ time, label, duration }` structs:

<!-- run -->
```js
markers = [ { time: 0, label: "Intro" }, { time: 12.5, label: "Verse" }, { time: 65, label: "Chorus" } ];
println( audioMarkers( markers, "youtube" ) );
audioMarkers( markers, "edl", "markers.edl", 25 );       // DaVinci Resolve markers at 25 fps
```

| Format | Gives |
|---|---|
| `json`, `csv` | The list, each with its duration |
| `edl` | A CMX 3600 EDL with Resolve marker comments, at `fps` |
| `srt`, `vtt` | Markers as caption cues, each lasting until the next |
| `ffmetadata` | FFmpeg chapter metadata, to embed with `ffmpeg -i in.mp4 -i markers.txt -map_metadata 1` |
| `youtube` | `0:00 Intro` lines for a video description |

## Settings

| Setting | Variable | Does |
|---|---|---|
| `ffmpeg` | `BX_AUDIO_FFMPEG` | Path to the FFmpeg to use |
| `autoInstall` | | `false` forbids all downloads (FFmpeg, the speech engine, voices) |
| `home` | `BX_AUDIO_HOME` | Where downloads are kept, `~/.bxaudio` by default |
