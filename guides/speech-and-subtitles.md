---
title: Speech and subtitles
order: 7
summary: Speak text with open license voices that download on first use, control the pace and pauses, and write captions timed to the words.
---

`audioSpeak` turns text into audio with a [Piper](https://github.com/rhasspy/piper) voice, on the JVM, with no API key and no cloud service. The engine (about 148 MB) and the voice (63 MB or more) are not in the module. The first call downloads them, checks every file against a checksum, and keeps them in `~/.bxaudio`, shared by every BoxLang install. After that it works offline.

```js
voice = audioSpeak( text="Meet BoxLang. Fast, friendly and made for the JVM.", voice="joe" );
audioSave( voice, "hello.mp3" );
```

`voice` is a full id such as `en_US-joe-medium` or a short name such as `joe`, when only one voice has that name. The result is a mono buffer at the voice's own sample rate, normally 22 050 Hz.

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

## Download ahead of time

```bash
bx-audio install engine joe
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
    text = "First, the setup. Then the twist.\n\nThe end.",
    voice = "joe",
    pauses = { sentence: 0.5, clause: 0.2, paragraph: 1.0 }     // seconds; these are the defaults' names
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

## Limits

- US English only for now. British English (`en_GB`) voices need a GPL 3 pronunciation dictionary and other languages need a phonemizer the engine does not have yet. See [Voices](../voices.md).
- Only single speaker voices are offered.
- There are no word level timings, so captions follow sentences or clauses, not each word.
