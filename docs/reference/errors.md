---
title: Errors
order: 6
summary: The error types the module throws, and what each one means.
---

Every error the module throws has a `type` that starts with `bxaudio.`. The type is stable and part of the API: it is never renamed, while the message may be improved at any time. Catch by type instead of reading the message.

```javascript
try {
    audio = audioLoad( "interview.ogg" );
} catch( "bxaudio.ffmpegMissing" e ) {
    println( "Install FFmpeg first: bx-audio ffmpeg install" );
} catch( "bxaudio.fileNotFound" e ) {
    println( "No such file" );
}
```

| Type | Thrown when | What to do |
|---|---|---|
| `bxaudio.invalidArgument` | An argument has a value or a type the function cannot use, such as a negative frequency or something that is not audio. | Fix the call. The message says which argument and what is allowed. |
| `bxaudio.fileNotFound` | A file you named does not exist. | Check the path. Relative paths are relative to the running script. |
| `bxaudio.io` | A file could not be read, written, moved or deleted: no permission, a full disk, a read only folder. | Fix the folder or the permissions. The cause is attached. |
| `bxaudio.unsupportedFormat` | The format, encoding, bit depth, channel count or sample rate is one the module cannot handle, or the file is not audio at all. | Convert the file, see [Formats and FFmpeg](../guides/formats-and-ffmpeg.md). |
| `bxaudio.corruptAudio` | The file claims to be audio but is damaged or cut short. | Get a good copy. |
| `bxaudio.tooLarge` | The audio is too long to hold in memory. | Cut it into parts first. |
| `bxaudio.ffmpegMissing` | A feature needs FFmpeg (OGG, Opus, AAC, video) and FFmpeg is not installed, and could not be downloaded. | Run `bx-audio ffmpeg install` or `audioFfmpegInstall()`, or install it so it is on the PATH. See `audioFfmpeg()` for the state. |
| `bxaudio.ffmpegFailed` | FFmpeg is there but failed, timed out or could not be started. | The message has what FFmpeg said. |
| `bxaudio.engineNotInstalled` | You asked for speech and the speech engine is not downloaded, and `autoInstall` is off. | `bx-audio install engine`, or allow `autoInstall`. |
| `bxaudio.voiceNotInstalled` | The voice is known but not downloaded, and `autoInstall` is off. | `bx-audio install <voice>`. |
| `bxaudio.voiceNotFound` | No voice has that name, or it is a voice the module does not offer. | `audioVoices()` lists the voices. |
| `bxaudio.speechFailed` | The voice could not say the text. | Try other text, or another voice. |
| `bxaudio.downloadFailed` | A download failed, stalled or did not match its checksum. | Check the network and the proxy, then try again. |
| `bxaudio.internal` | Something the module ships is missing, such as a codec library. | Reinstall the module. |
| `bxaudio.cli` | A mistake on the command line: an unknown command or option, a missing argument. | Run `bx-audio help <command>`. |

## FFmpeg is optional, but some things need it

WAV, MP3 and FLAC work with no FFmpeg. Reading or writing OGG, Opus, AAC and M4A, and everything with video, needs FFmpeg. With `autoInstall` on (the default) the module downloads FFmpeg the first time it is needed. With it off, or where no download exists for the computer, the call throws `bxaudio.ffmpegMissing` and the message says how to get it. `audioFfmpeg()` tells you ahead of time, and `bx-audio doctor` checks everything at once.
