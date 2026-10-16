# Trailer soundtrack

The whole job of scoring a short video, in one script: music cut to the exact length of the picture, a voice-over with captions, the music ducked under the voice, the mix mastered to a loudness target, chapter markers for the editor, and the finished video with the new sound. This is what used to need a Python script, ffmpeg and a loudness tool.

```bash
boxlang trailer.bxs                                        # 15 seconds, out/trailer.wav, captions and chapters beside it
boxlang trailer.bxs --video=clip.mp4 --videoOut=out/clip-scored.mp4   # the length is the video's, the sound goes on it
boxlang trailer.bxs --text="Coming this autumn. Only in cinemas." --voice=kristin
boxlang trailer.bxs --voiceFile=narration.wav --music=cue.wav --target=podcast
```

The first run downloads the speech engine and the voice (see [docs/voices.md](../../docs/voices.md)). Muxing onto a video needs FFmpeg, which `bx-audio ffmpeg install` downloads if your computer has none.

| Setting | Default | |
|---|---|---|
| `--seconds` | 15 | Length of the trailer, unless `--video` gives it |
| `--video` | none | A video to score. The picture is copied, never re-encoded. |
| `--videoOut` | next to the audio | Where the scored video goes |
| `--text` | three short lines | What the voice says |
| `--voice` | `kristin` | A voice from `bx-audio voices` |
| `--speed` | `0.96` | How fast the voice speaks, 1 is its own pace |
| `--voiceFile` | none | Your own narration instead of speech (no captions then) |
| `--music` | built-in cue | Your own music, cut to length |
| `--target` | `streaming` | `streaming` -14 LUFS, `podcast` -16, `broadcast` -23, or a number |
| `--captions` | beside the audio | `.srt` or `.vtt` |
| `--install` | `true` | May the engine and voice be downloaded |

## The steps

1. **The length.** With `--video`, `videoInfo` reads it. The script makes the trailer longer only if the voice would not fit.
2. **The voice.** `audioSpeakCues` speaks the text sentence by sentence with the pauses asked for, and returns when each caption line is spoken. The voice is levelled to -17 dBFS RMS (by loudness, not by peak), cleaned with a high pass and given a touch of room.
3. **The music.** The built-in cue is built on a timeline, or your file is loaded. `audioFitTo` stretches it to the exact length without changing its pitch.
4. **The mix.** The voice has its own track and `audioDuck` lowers the music while it speaks.
5. **The master.** `audioMaster` reaches the loudness target with a true-peak limiter. The script prints the loudness before and after.
6. **Captions and chapters.** `audioSubtitles` writes SRT or WebVTT starting where the voice does, and `audioMarkers` writes a chapter list.
7. **The video.** `audioMux` puts the sound on the picture.

## What it shows

| Feature | Where |
|---|---|
| Fit music to a video's length | `audioFitTo( music, total )` |
| Captions that follow the voice | `audioSpeakCues`, `audioSubtitles` |
| Loudness measuring and mastering | `audioLoudness`, `audioMaster` |
| Chapter markers | `audioMarkers` |
| Sound onto a video | `videoInfo`, `audioMux` |
