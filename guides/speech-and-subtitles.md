---
title: Speech and subtitles
order: 7
summary: Speak text with open license voices that download on first use, control the pace and pauses, and write captions timed to the words.
---

`audioSpeak` turns text into audio with a [Piper](https://github.com/rhasspy/piper) or a [Kokoro](https://huggingface.co/hexgrad/Kokoro-82M) voice, on the JVM, with no API key and no cloud service. The engine (about 148 MB) and the voice (63 MB or more) are not in the module. The first call downloads them, checks every file against a checksum, and keeps them in `~/.bxaudio`, shared by every BoxLang install. After that it works offline.

```js
voice = audioSpeak( text="Meet BoxLang. Fast, friendly and made for the JVM.", voice="joe" );
audioSave( voice, "hello.mp3" );
```

`voice` is a full id such as `en_US-joe-medium` or a short name such as `joe`, when only one voice has that name. Put the engine and a colon before a name to look only at that engine's voices: `kokoro:heart`, `piper:joe`. A name that fits several voices is refused, and the error lists the `engine:id` forms to use. The result is a mono buffer at the voice's own sample rate, normally 22 050 Hz.

## Choose a voice

```bash
bx-audio voices                       # the voices you can use, and whether they are installed
bx-audio voices joe                   # one voice in detail: license, what it asks of you, size, training data
bx-audio voices --excluded            # voices that are NOT offered, with the reason
bx-audio licenses                     # what each license asks of you
```

Only voices whose model card states an open license are offered, and the license is the one the card gives. The [voices page](../voices.md) lists them all, including the ones left out and why, and [Licenses](../licenses.md) explains what each license asks. In code:

```js
audioVoices();                                     // the voices you can use
audioVoices( status="downloadable" );              // offered, not installed yet
audioVoices( status="excluded" );                  // not offered, each with a reason
audioVoices( license="CC0-1.0" );
audioLicenses();                                   // the licenses and what they ask
```

Each voice has a `status` of `installed`, `downloadable` or `excluded`.

## Kokoro voices

Kokoro is the second kind of voice, next to Piper: one model of 82 million parameters and 28 English voices, at 24 000 Hz. It sounds more natural than the Piper voices, and costs more: the model is 325 MB and the voices pack 28 MB, shared by all 28 voices, and a sentence takes longer on a CPU.

```js
voice = audioSpeak( text="Meet BoxLang. Fast, friendly and made for the JVM.", voice="af_heart" );   // or the short name, heart
audioSave( voice, "hello.mp3" );
```

| Prefix | Voices |
|---|---|
| `af_` American, female | `alloy`, `aoede`, `bella`, `heart`, `jessica`, `kore`, `nicole`, `nova`, `river`, `sarah`, `sky` |
| `am_` American, male | `adam`, `echo`, `eric`, `fenrir`, `liam`, `michael`, `onyx`, `puck`, `santa` |
| `bf_` British, female | `alice`, `emma`, `isabella`, `lily` |
| `bm_` British, male | `daniel`, `fable`, `george`, `lewis` |

- **Installing.** The first call downloads the engine if it is missing, then the model and the voices pack, and checks both against a SHA-256. `bx-audio install af_heart` does it ahead of time. The voices share the files, so `audioVoiceRemove( "af_heart" )` removes the model and the pack for every Kokoro voice.
- **Only Kokoro v1.0.** The files come from the [kokoro-onnx release](https://github.com/thewh1teagle/kokoro-onnx/releases/tag/model-files-v1.0). The model is Apache 2.0, so `audioCredits()` gives the notice to keep.
- **Other languages.** Kokoro also has Spanish, French, Hindi, Italian, Japanese, Portuguese and Mandarin voices. They need a phonemizer the engine does not have, so they are listed as not offered, with the reason, like the other languages.
- **What `audioVoices()` says.** Each voice has an `engine`, `piper` or `kokoro`.
- **How it speaks.** The text becomes phonemes with the same dictionary as the Piper voices. Kokoro was trained on [espeak-ng](https://github.com/espeak-ng/espeak-ng) phonemes, which the engine does not have, so they are rewritten first: a table of function words learned from espeak-ng, and for American English a few vowel rules. Then the model speaks each sentence, silence is trimmed from both ends and sentences are joined with a quarter of a second of pause. `speed`, `pauses` and `pronunciations` work as for Piper.
- **How close it is to Kokoro with espeak-ng.** On 120 sentences of these docs the audio is the same length (0.997 times, 89 of 100 sentences within 5 percent). A speech recognizer (Whisper `base.en`) gets 7.3 percent of the words wrong on the American voice `af_heart`, against 5.7 percent for Kokoro with espeak-ng, and 8.0 against 5.7 percent on the British voice `bf_emma`. A rare or technical word may be said differently: respell it with `pronunciations`.
- **Level.** A few voices peak at or just above full scale on some lines. Run the speech through `audioNormalize` or `audioMaster` before you mix it.

## Download ahead of time

```bash
bx-audio install engine joe
bx-audio install af_heart             # a Kokoro voice: the model and the voices pack
```

```js
audioVoiceInstall( "engine", ( name, done, total ) => println( name & " " & int( done * 100 / total ) & "%" ) );
audioVoiceInstall( "joe" );
```

Set the module setting `autoInstall` to `false` (or pass `autoInstall=false`) to forbid downloads: the call then fails with a message that says exactly what to install.

## Pace and pauses

```js
audioSpeak( text="Take your time.", voice="joe", speed=0.85 );     // slower
audioSpeak( text="Quick, quick.", voice="joe", speed=1.3 );        // faster
```

`speed` is between 0.25 and 4; 1 is the voice's own pace. Without `pauses` the voice phrases the whole text itself. Give `pauses` and the text is spoken piece by piece, with exactly the silence you ask for between the pieces:

```js
audioSpeak(
    text = "First, the setup. Then the twist." & char( 10 ) & char( 10 ) & "The end.",
    voice = "joe",
    pauses = { sentence: 0.5, paragraph: 1.0 }     // seconds; `clause` only applies to audioSpeakCues( split="clause" )
);
```

A blank line starts a new paragraph. The defaults are 0.35 s after a sentence, 0.15 s after a clause and 0.8 s after a paragraph. The padding a voice leaves around each sentence is cut, so the pauses are the ones you set.

## Captions timed to the voice

`audioSpeakCues` speaks the same way and also tells you when each line of text is spoken:

```js
spoken = audioSpeakCues(
    text = "In a world of silence, one module dared to speak! It runs on the JVM.",
    voice = "joe",
    maxChars = 42,
    split = "clause"
);
audioSave( spoken.audio, "narration.wav" );
audioSubtitles( cues=spoken.cues, format="srt", path="narration.srt" );
```

```text
1
00:00:00,000 --> 00:00:01,277
In a world of silence,

2
00:00:01,627 --> 00:00:03,174
one module dared to speak!
```

- `split="sentence"` (the default) speaks and times whole sentences. `split="clause"` works clause by clause, at commas, semicolons, colons and dashes: more natural pauses and shorter captions.
- `maxChars` is the longest caption line (default 80). A longer piece is wrapped into several lines that share its time in proportion to their characters.
- The sentence splitter knows abbreviations (`Dr.`, `Mr.`, `p.m.`), initials and decimals, so it does not break at them.

The times come from the length of each spoken piece, and with the voice's padding trimmed, a caption starts and ends within about 10 ms of the words (measured against the silences in the audio). Times inside a piece that wraps to several lines are estimated by characters.

`audioSubtitles` writes `srt` or `vtt`. Its `offset` adds seconds to every time, for narration that starts later in the video.

```bash
bx-audio speak "Meet BoxLang. Build more, with less." --voice=joe --out=vo.wav --captions=vo.srt --by-clause
```

## Licenses and credits

Every voice has a license, and it decides what you may do with the speech in a product you sell. `audioVoices()` gives each voice a `usage` (`permissive`, `attribution` or `sharealike` for the voices on offer, all of which allow a product you sell) and the `credit` it asks for. `audioCredits()` writes the credits for the voices you use.

<!-- run -->
```js
usable = audioVoices( usage="permissive,attribution" );
println( usable.len() & " voices you can use in a product you sell" );
println( audioCredits( [ "joe", "sam" ] ) );
```

Voices with non-commercial, copyleft, custom or unclear licenses are not offered, and [Voices](../voices.md) says which and why. This is a guide, not legal advice.

## Limits

- US and British English only for now. The British English (`en_GB`) voices use a GPL 3 pronunciation dictionary, downloaded the first time one speaks, and other languages need a phonemizer the engine does not have yet. See [Voices](../voices.md).
- Only single speaker Piper voices are offered. Kokoro speaks English only here, and its pronunciation is close to, not the same as, Kokoro with espeak-ng (see above).
- There are no word level timings, so captions follow sentences or clauses, not each word.
