# Third party software

The BoxLang Audio module itself is licensed under the Apache License 2.0.

## Optional MP3 codecs

MP3 reading and writing use two libraries that are licensed under the **GNU Lesser General Public License (LGPL)**. They are not part
of this module's jar. They are separate files in the module's `libs` folder, so you can replace them with your own build of the same
library, or delete them to remove MP3 support entirely. Everything else in the module keeps working without them.

| Library | Used for | License as declared by the project | Source |
|---|---|---|---|
| JLayer 1.0.1 (`javazoom:jlayer`) | Decoding MP3 | LGPL | https://central.sonatype.com/artifact/javazoom/jlayer |
| jump3r 1.0.5 (`de.sciss:jump3r`), a Java port of LAME | Encoding MP3 | LGPL 2.1 or later | https://codeberg.org/sciss/jump3r |

The full text of the LGPL version 2.1 is in [LGPL-2.1.txt](LGPL-2.1.txt). The source code of both libraries is available from the locations
above and, as `-sources` jars, from Maven Central.

This file records what the projects state about themselves. It is not legal advice, so please confirm the terms suit your own use,
particularly if you redistribute the module.

## Speech engine and voices (downloaded on first use)

Text to speech uses the [Voices](https://github.com/pitest/voices) library, which runs [Piper](https://github.com/rhasspy/piper) models on ONNX
Runtime. None of it is in this module. It is downloaded the first time it is needed, with each file checked against a checksum that is pinned in the
module (`engine.json`), and loaded in a class loader of its own. The licenses below are the ones the projects declare in their own metadata.

| Library | Used for | License as declared by the project |
|---|---|---|
| Voices `core`, `chorus`, `g2p`, `piper-runtime`, `model-downloader` 0.0.9 (`org.pitest.voices`) | Running Piper voices from Java, and turning text into phonemes | Apache 2 |
| Voices `en_us` 0.0.9 | The US English pronunciation dictionary | Apache 2 |
| ONNX Runtime 1.22.0 (`com.microsoft.onnxruntime`) | Running the voice models | MIT |
| Apache OpenNLP Tools 2.5.5 | Part of the phonemizer | Apache 2 (`META-INF/LICENSE` in the jar) |
| Gson 2.13.2 | Reading the voice configuration | Apache 2 |
| SLF4J API and NOP 2.0.17 | Logging, silenced | MIT |

The Voices `en_uk` dictionary is **not** used: it is licensed GPL 3. That is why British English voices are not offered.

Each voice has its own license, taken from its model card. [voices.md](../docs/voices.md) lists the voices on offer, their licenses, and the ones that
were left out because their license is restrictive or unclear. Voices are downloaded from the
[rhasspy/piper-voices](https://huggingface.co/rhasspy/piper-voices) collection on Hugging Face.

## Note on MP3

MP3 decoding and encoding need the `java.desktop` module of the JDK (the encoder describes its input with `javax.sound.sampled`).
Standard JDKs include it.
