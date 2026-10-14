# Voices

These are the voices `audioSpeak()` can use. They are [Piper](https://github.com/rhasspy/piper) voices from the [rhasspy/piper-voices](https://huggingface.co/rhasspy/piper-voices) collection, and none of them is bundled with the module: each is downloaded the first time you use it, into the `voices` folder of the module's speech home.

```bash
bx-audio voices                       # this list, from the command line
bx-audio install joe                  # download a voice ahead of time
bx-audio speak "Hello" --voice=joe     # or just use it, it installs itself
```

```js
audio = audioSpeak( text="Hello from BoxLang", voice="joe" );
audioSave( audio, "hello.mp3" );
```

## Licenses

Only voices whose model card states an open license are listed: CC0, public domain, CC BY, CC BY-SA, Apache 2.0 or the Unlicense. The license column is the identifier we map the card's wording to, and the card itself stays the authority, so read it when it matters (the link in the last column leads to the training data). A voice licensed CC BY asks for credit, and CC BY-SA also asks that what you make from it is shared under the same terms. Voices whose card says non-commercial, names a custom license, says only "See URL", or says nothing are not listed.

## Available voices

| Voice | Language | Quality | License | Download | Training data |
|---|---|---|---|---|---|
| `en_US-bryce-medium` | en_US | medium | Public-Domain | 64 MB | not stated |
| `en_US-joe-medium` | en_US | medium | CC0-1.0 | 63 MB | [source](https://github.com/OHF-Voice/voice-datasets) |
| `en_US-john-medium` | en_US | medium | Public-Domain | 64 MB | [source](https://librivox.org) |
| `en_US-kathleen-low` | en_US | low | CC0-1.0 | 63 MB | [source](https://github.com/rhasspy/dataset-voice-kathleen) |
| `en_US-kristin-medium` | en_US | medium | Public-Domain | 64 MB | [source](https://librivox.org) |
| `en_US-ljspeech-high` | en_US | high | Public-Domain | 114 MB | [source](https://keithito.com/LJ-Speech-Dataset/) |
| `en_US-ljspeech-medium` | en_US | medium | Public-Domain | 64 MB | [source](https://keithito.com/LJ-Speech-Dataset/) |
| `en_US-mike-medium` | en_US | medium | CC0-1.0 | 63 MB | [source](https://github.com/OHF-Voice/voice-datasets) |
| `en_US-norman-medium` | en_US | medium | Public-Domain | 64 MB | [source](https://librivox.org) |
| `en_US-reza_ibrahim-medium` | en_US | medium | CC0-1.0 | 64 MB | [source](https://huggingface.co/datasets/mah92/Hedayatfar-Persian-Quran-Audio-Dataset) |
| `en_US-sam-medium` | en_US | medium | Apache-2.0 | 63 MB | [source](https://github.com/Sam-Accenture-Non-Binary-Voice/non-binary-voice-files) |

Use a voice by its full id, or by its short name when only one voice has that name (`joe`, but not `ljspeech`, which comes in two qualities).

## Left out

| Voice | Why |
|---|---|
| `en_US-amy-low` | The model card only says 'See URL', so the license is not clear |
| `en_US-amy-medium` | The model card only says 'See URL', so the license is not clear |
| `en_US-arctic-medium` | The model card only says 'See LICENSE file', so the license is not clear |
| `en_US-danny-low` | The model card only says 'See URL', so the license is not clear |
| `en_US-hfc_female-medium` | Non-commercial license |
| `en_US-hfc_male-medium` | Non-commercial license |
| `en_US-kusal-medium` | The model card only says 'See URL', so the license is not clear |
| `en_US-l2arctic-medium` | Non-commercial license |
| `en_US-lessac-high` | Custom license (Blizzard 2013 data), not an open license |
| `en_US-lessac-low` | Custom license (Blizzard 2013 data), not an open license |
| `en_US-lessac-medium` | Custom license (Blizzard 2013 data), not an open license |
| `en_US-libritts-high` | 904 speakers, not supported yet |
| `en_US-libritts_r-medium` | 904 speakers, not supported yet |
| `en_US-ryan-high` | Non-commercial license |
| `en_US-ryan-low` | Non-commercial license |
| `en_US-ryan-medium` | Non-commercial license |

British English voices (`en_GB`) are not offered because the British dictionary of the Voices engine is licensed GPL 3.

## Updating this list

This page and `voices.json` are generated. Run `boxlang tools/update-voices.bxs` in the module repository to read the current voices and their model cards again, and review the difference.
