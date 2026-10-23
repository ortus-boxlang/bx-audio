---
title: Licenses
order: 2
summary: The license of the module, of the voices, of FFmpeg and of every library it uses, in plain words.
---

This page is a summary to help you choose. It is not legal advice, and the license text of each piece is what counts.

## The module

BoxLang Audio is licensed [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0). The examples are original compositions released with the module: you can use what they make.

## Voices

Each voice is a trained model with its own license, taken from the model card the author published. The module offers voices whose card states an open license, plus a few marked **check license** whose card is unclear or non-commercial: those speak and log a warning, and their `usage` keeps them out of any list filtered to the commercial classes. It shows you the license next to the voice. Run `bx-audio licenses` for this table, and `bx-audio voices <name>` for one voice.

| License | Commercial use | Credit | Share alike | What it asks |
|---|---|---|---|---|
| [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | yes | no | no | No restrictions. Credit is welcome, not required |
| [Public domain](https://creativecommons.org/publicdomain/mark/1.0/) | yes | no | no | No restrictions. The recordings are in the public domain |
| [The Unlicense](https://unlicense.org/) | yes | no | no | No restrictions |
| [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) | yes | yes | no | Keep the license notice and say what you changed |
| [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | yes | yes | no | Credit the author and link the license |
| [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) | yes | yes | no | Credit the author and link the license |
| [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) | yes | yes | yes | Credit the author, and share what you make from it under the same license |

At the moment the offered voices are CC0, public domain and Apache 2.0, so none of them requires credit or sharing. Check the card of the voice you use whenever it matters: the license applies to the voice, and the question of what it means for the audio the voice produces is the license's to answer, not this page's.

### What is never offered

A voice is left out, with the reason, when its card:

- says non-commercial (CC BY-NC and similar),
- names a custom license (for example the Blizzard 2013 data behind the Lessac voices),
- says only "See URL" or "See LICENSE file", so the license is not clear, or
- says nothing.

Other voices are left out for technical reasons: their language has no pronunciation engine yet, or they have several speakers. The [voices page](voices.md) lists every voice of the collection with its status and reason, and `bx-audio voices --excluded` prints the same.

## FFmpeg

FFmpeg is used only for OGG, Opus, AAC, reading video and putting sound on a video, and it is never part of this module. The module starts it as a separate program: yours, if you have one, or a build it downloads from its provider into `~/.bxaudio/ffmpeg`, after checking its size and SHA-256.

| Platform | Provider |
|---|---|
| Linux and macOS, amd64 and arm64 | [martin-riedl.de](https://ffmpeg.martin-riedl.de/), version 9.0.2 |
| Windows amd64 | [gyan.dev](https://www.gyan.dev/ffmpeg/builds/), version 8.1.2 |

FFmpeg is LGPL 2.1 or later, or GPL 2 or later when built with GPL components, and these builds include such components (the Linux and macOS builds are also marked non-free by their provider, so they cannot be redistributed). The module does not redistribute them. If your use needs a particular FFmpeg license, install your own build and set the `ffmpeg` [setting](reference/configuration.md). See <https://ffmpeg.org/legal.html>.

## Libraries in the module

| Library | Used for | License |
|---|---|---|
| JLayer 1.0.1 | Reading MP3 | LGPL, a separate jar in `libs/` |
| jump3r 1.0.5 | Writing MP3 (a Java port of LAME) | LGPL 2.1 or later, a separate jar in `libs/` |

JLayer and jump3r are separate, replaceable jars and are never merged into the module's own jar. Without them, WAV and FLAC still work and MP3 calls say so clearly. Their license text is in the module's `licenses/` folder.

FLAC is encoded and decoded by code in this module, written from the published format specification, with no code from libFLAC.

## Libraries downloaded for speech

These come with the speech engine, which is downloaded on first use, checked against pinned SHA-1 checksums, and loaded in the module's own class loader:

| Library | Used for | License |
|---|---|---|
| Voices `core`, `chorus`, `g2p`, `piper-runtime`, `model-downloader` 0.0.9 | Running Piper voices from Java, and text to phonemes | Apache 2.0 |
| Voices `en_us` 0.0.9 | The US English pronunciation dictionary | Apache 2.0 |
| ONNX Runtime 1.22.0 | Running the voice models | MIT |
| Apache OpenNLP Tools 2.5.5 | Part of the phonemizer | Apache 2.0 |
| Gson 2.13.2 | Reading the voice configuration | Apache 2.0 |
| SLF4J API and NOP 2.0.17 | Logging, silenced | MIT |

The Voices `en_uk` dictionary (British English pronunciation) is licensed GPL 3. It is not bundled: it is downloaded with the engine the first time a British English voice speaks, and only British English voices use it. If your use cannot accept a GPL 3 download, use the `en_US` voices.
