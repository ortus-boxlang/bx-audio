---
title: Storage
order: 11
summary: Where the module keeps what it downloads, how to see it, move it and clear it.
---

The module itself is small. What is big is downloaded when you first need it, and kept in one folder in your home directory so every BoxLang installation, project and user session shares it:

```text
~/.bxaudio/
├── engine/     the text to speech engine (about 148 MB)
├── voices/     the voices you downloaded (63 to 114 MB each)
├── ffmpeg/     FFmpeg, if your computer has none
└── cache/      things that can be rebuilt
```

## See it

```bash
bx-audio where
```

```text
bx-audio home: /home/you/.bxaudio

FOLDER   SIZE       WHAT
engine   148.6 MB   Text to speech engine jars
voices   63.2 MB    Downloaded voices
ffmpeg   -          FFmpeg, when the system has none
cache    -          Rendered samples and other things that can be rebuilt
```

```js
audioStorage();    // { home, totalMB, folders: [ { kind, path, description, exists, sizeMB } ] }
```

The web [explorer](explorer.md) shows the same location in its footer, read only.

## Clear it

```bash
bx-audio clean voices            # shows what would go and asks you to add --yes
bx-audio clean voices --yes
bx-audio clean all --yes
```

```js
audioStorageClear( "voices" );   // returns the megabytes freed; kinds: engine, voices, ffmpeg, cache, all
```

Everything is downloaded again the next time it is needed. `bx-audio remove joe` deletes one voice, `bx-audio remove engine` the engine.

## Move it

The folder is, in order of priority: the module setting `home`, the `bxaudio.home` Java system property, the `BX_AUDIO_HOME` environment variable, then `~/.bxaudio`.

```bash
export BX_AUDIO_HOME=/data/bxaudio
```

In `boxlang.json`:

```json
{ "modules": { "bxaudio": { "settings": { "home": "/data/bxaudio", "autoInstall": true } } } }
```

Use it for a server with a data disk, for a container (mount the folder as a volume so the downloads survive), or to pre-load a machine that has no internet: copy a populated `~/.bxaudio` to it, then set `autoInstall` to `false`.

## How the speech engine is loaded

The engine's jars are not on any class path. They are added to the module's own class loader the first time they are needed, so they stay separate from the rest of the runtime, and a native library (ONNX Runtime) is loaded once per JVM.
