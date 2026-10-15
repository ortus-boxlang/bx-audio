---
title: Trailers and video
order: 4
summary: Score a video from start to finish with music cut to length, a voice-over with captions, mastering and the sound put on the picture.
---

This is the job that used to need a Python script, FFmpeg and a loudness tool. In BoxLang Audio it is one script, and the [trailer example](https://github.com/ortus-boxlang/bx-audio/tree/development/examples/13-trailer) does all of it.

```bash
boxlang examples/13-trailer/trailer.bxs --video=clip.mp4 --videoOut=scored.mp4
```

The steps, and the functions behind each:

| Step | Function |
|---|---|
| Know how long the picture is | [`videoInfo`](../reference/functions/ffmpeg-and-video.md#videoinfo) |
| Cut the music to exactly that | [`audioFitTo`](../reference/functions/time-and-pitch.md#audiofitto) |
| Speak the voice-over, with captions | [`audioSpeakCues`](../reference/functions/speech.md#audiospeakcues) |
| Duck the music under the voice | [`audioDuck`](../reference/functions/timeline.md#audioduck) |
| Master to a loudness target | [`audioMaster`](../reference/functions/loudness-and-dynamics.md#audiomaster) |
| Captions for the editor | [`audioSubtitles`](../reference/functions/speech.md#audiosubtitles) |
| Chapter markers | [`audioMarkers`](../reference/functions/ffmpeg-and-video.md#audiomarkers) |
| Sound on the picture | [`audioMux`](../reference/functions/ffmpeg-and-video.md#audiomux) |

## 1. The length of the picture

`videoInfo` reads any video with FFprobe: length, size, and the video and audio streams.

```js
info = videoInfo( "clip.mp4" );
println( info.seconds & " s, " & info.video.width & "x" & info.video.height & " at " & info.video.fps & " fps" );
```

FFmpeg is found on your computer or downloaded for you, see [Formats and FFmpeg](formats-and-ffmpeg.md).

## 2. Music cut to length

Music is rarely the length of the video. `audioFitTo` changes its length to exact seconds without changing its pitch.

```js
music = audioLoad( "cue.wav" );
music = audioFitTo( music, info.seconds );
```

It works best within about 25 percent. For bigger changes cut the music, or loop part of it with [`audioLoopify`](games.md#seamless-loops). See [Time and pitch](time-and-pitch.md).

## 3. A voice-over with captions

`audioSpeakCues` speaks the text sentence by sentence with the pauses you choose, and returns the audio together with the time each caption line is spoken.

```js
spoken = audioSpeakCues(
    text = "One module. Pure JVM. No Python. BoxLang Audio.",
    voice = "joe",
    pauses = { sentence: 0.45, paragraph: 0.9 }
);
voice = spoken.audio;      // mono
cues  = spoken.cues;       // [ { start, end, text }, ... ]
```

Level the voice by loudness rather than by its peak (speech has loud peaks and a low average), put it where you want it, and write the captions with the same offset:

```js
lead = 1.5;                                    // the voice comes in after 1.5 seconds
audioSubtitles( cues=cues, format="srt", path="trailer.srt", offset=lead );
```

See [Speech and subtitles](speech-and-subtitles.md).

## 4. The mix

Put the music and the voice on tracks of a timeline and let the music duck while the voice speaks:

```js
t = audioTimeline( bpm=100, sampleRate=44100, channels=2 );
audioAdd( timeline=t, sound=music, at=0, track="music" );
audioAdd( timeline=t, sound=voice, at=lead, track="voice" );
audioTrackGain( t, "music", -6 );
audioDuck( t, "music", "voice", -9 );
mix = audioTrim( audioRender( timeline=t ), 0, info.seconds );
```

## 5. Master

```js
final = audioMaster( audio=mix, target="streaming", truePeak=-1 );
println( audioLoudness( final ) );   // integrated -14 LUFS, true peak under -1 dBFS
```

`target` is `streaming` (-14 LUFS), `podcast` (-16), `game` (-16), `broadcast` (-23, EBU R128), `cinema` (-24), or a number. See [Loudness and mastering](loudness-and-mastering.md).

## 6. Chapters for the editor

```js
audioMarkers(
    markers = [ { time: 0, label: "Music in" }, { time: 1.5, label: "Voice-over" } ],
    format = "youtube",
    path = "chapters.txt"
);
```

Formats: `json`, `csv`, a DaVinci Resolve `edl`, `srt`, `vtt`, FFmpeg chapters (`ffmetadata`) and a `youtube` chapter list.

## 7. The finished video

```js
audioMux( video="clip.mp4", audio=final, output="scored.mp4" );
```

The picture is copied, never re-encoded, so it is fast and lossless, and the result is never longer than the video. `keepOriginal=true` mixes the video's own sound underneath (`originalDb` sets how far down), and `offset` delays the new audio.

## One command per step

Most steps are also commands: `bx-audio probe`, `stretch --seconds`, `speak --captions`, `master`, `mux`. See the [command line reference](../reference/cli.md).
