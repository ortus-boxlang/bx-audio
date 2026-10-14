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

## Note on MP3

MP3 decoding and encoding need the `java.desktop` module of the JDK (the encoder describes its input with `javax.sound.sampled`).
Standard JDKs include it.
