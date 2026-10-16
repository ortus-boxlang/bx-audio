---
title: Contribute
order: 3
summary: Build the module, run the tests, and regenerate the generated docs.
---

The source is at [github.com/ortus-boxlang/bx-audio](https://github.com/ortus-boxlang/bx-audio). Everything is Java 21 and BoxLang; there is nothing native.

## Build and test

```bash
./gradlew downloadBoxLang     # once: the BoxLang jar used to compile and test
./gradlew spotlessApply test  # format, then run the tests
./gradlew build               # the module in build/module
```

Format all Java with `./gradlew spotlessApply` before every commit; CI checks it. Tests are JUnit 5 with Google Truth. Tests that need FFmpeg skip themselves where there is none, and nothing in the tests downloads anything.

## Generated files

Some files are written by scripts, so they cannot drift from the code. Change the source, run the script, commit the result.

| File | Script | Source |
|---|---|---|
| `docs/reference/functions/*.md` | `boxlang tools/generate-docs.bxs` | The BIF classes and their Javadoc |
| `docs/reference/cli.md` | `boxlang tools/generate-docs.bxs` | The command list in `Cli.bx` |
| `docs/examples/index.md` | `boxlang tools/generate-docs.bxs` | `examples/explorer/catalog.json` |
| `docs/voices.md`, `src/main/resources/voices.json` | `boxlang tools/update-voices.bxs` | The Piper voices and their model cards |
| `docs/assets/audio/*.mp3` | `boxlang tools/generate-doc-audio.bxs` | The examples |
| `docs/assets/images/mod-*`, `docs/assets/audio/mod-*` | `boxlang tools/generate-doc-modulation.bxs` | The modulation guide examples |
| `docs/assets/images/*` | `boxlang tools/generate-doc-images.bxs` | The waveform and spectrogram pictures |

A test fails if a function or a command is missing from the docs.

## Build this site

The docs are built with [BX Sites](https://bxsites.io). With `bx-sites`, `bx-markdown`, `bx-esapi`, `bx-yaml` and `bx-image` installed:

```bash
bxSites serve          # build and serve with live reload
bxSites build          # build to site/
```

Pages marked with `<!-- run -->` before a code block are executed by a test, so their examples stay correct.

## Adding a function

1. Add a class in `src/main/java/ortus/boxlang/modules/audio/bifs/`, annotated `@BoxBIF`, extending `BIF`, with Javadoc that has `@argument.name` for each argument and `@return`.
2. Put it in a category in `tools/generate-docs.bxs` (the script stops if you forget) and run it.
3. Add a test.

## Ortus

BoxLang is a professional open source project funded by the community and [Ortus Solutions](https://www.ortussolutions.com).
