---
title: Configuration
order: 3
summary: The module settings, environment variables and Java properties.
---

## Module settings

In `boxlang.json`, under `modules.bxaudio.settings`:

```json
{
  "modules": {
    "bxaudio": {
      "settings": {
        "home": "",
        "autoInstall": true,
        "ffmpeg": ""
      }
    }
  }
}
```

| Setting | Default | Does |
|---|---|---|
| `home` | `""` | Where the speech engine, the voices and FFmpeg are downloaded to. Empty means `~/.bxaudio` |
| `autoInstall` | `true` | May the module download the speech engine, a voice or FFmpeg when you first use something that needs it. `false` makes it fail with a message that says what to install |
| `ffmpeg` | `""` | Path to an FFmpeg you want it to use (the program, or its folder). Empty finds it from the variable, the home or the `PATH` |

## Environment variables

| Variable | Does |
|---|---|
| `BX_AUDIO_HOME` | The same as `home`. The setting wins over the variable |
| `BX_AUDIO_FFMPEG` | The same as `ffmpeg` |
| `BX_AUDIO_DEBUG` | `1` makes the command line print a stack trace instead of a one line message |

## Java system properties

`-Dbxaudio.home=/path` is the same as `BX_AUDIO_HOME`, for hosts where you control the JVM arguments but not the environment.

## Order of precedence for the home folder

1. The module setting `home`
2. The `bxaudio.home` system property
3. The `BX_AUDIO_HOME` variable
4. `~/.bxaudio`

## What lives in the home

See [Storage](../guides/storage.md): `engine/`, `voices/`, `ffmpeg/` and `cache/`.

## Where FFmpeg is looked for

1. The `ffmpeg` setting
2. `BX_AUDIO_FFMPEG`
3. `<home>/ffmpeg/<version>/`
4. The `PATH`

A configured path that does not work is an error, not a silent fall back to another FFmpeg.
