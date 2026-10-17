# Voice-over

A trailer style announcement over music. The words are spoken by a text to speech voice. The music under it, a pad, a pulsing bass, an arpeggio and a light beat, ducks whenever the voice speaks and swells back between the lines. It is the mix you would otherwise do by hand for a video: record the narration, put music under it, lower the music while someone talks.

```bash
boxlang voiceover.bxs                                     # three lines, spoken by kristin, out/voiceover.wav
boxlang voiceover.bxs --voice=kristin --text="Hello there.|This is a voice-over.|Goodbye."
boxlang voiceover.bxs --voiceFile=narration.wav           # your own recording instead of a synthesized voice
bx-audio render voiceover --voice=kristin --out=promo.mp3
```

The first run downloads the speech engine (about 148 MB) and the voice (63 MB or more), once, see [docs/voices.md](../../docs/voices.md) for the voices. Pass `--install=false` to forbid downloads, and it tells you what to install instead. Because of that download, the voice-over is not in the web explorer, which should not start a 150 MB download by itself. It runs from the command line.

| Setting | Default | |
|---|---|---|
| `--out` | `out/voiceover.wav` | Where to write the audio |
| `--voice` | `kristin` | A voice from `bx-audio voices` |
| `--speed` | `0.96` | How fast the voice speaks, 1 is its own pace |
| `--text` | three lines about BoxLang | The lines to speak, separated by `\|` |
| `--voiceFile` | none | A recording to use instead of speech, one clip placed after two bars of music |
| `--duck` | -12 | How far the music drops under the voice, in dB |
| `--bpm` | 100 | Tempo of the music |
| `--install` | `true` | May the engine and voice be downloaded |

## How the mix is built

1. **The voice.** Each line is spoken with `audioSpeak`, cleaned with a high pass at 80 Hz, given a touch of room, and brought to a speaking level of -17 dBFS RMS, but never past -1 dBFS at the peak. Speech has loud peaks and a low average, so levelling by peak would leave it too quiet.
2. **Where it goes.** The first line comes in after two bars of music. Each next line starts on the first bar line after the one before it ended. The music runs for as long as the voice needs, plus two bars to finish.
3. **The music.** Am, F, C, G on a detuned saw pad and an arpeggio from the start, and the bass and a light beat from bar two. The music sits 10 dB under the voice's level even when nobody speaks.
4. **Ducking.** `audioDuck` lowers the music and the beat by `--duck` dB while the voice track is loud, with a fast attack and a gentle release, so the music dips as the voice starts and comes back after it.
5. **The end.** A crash and a last kick, a long fade, and a limiter for the final level.

## What it shows

| Feature | Where |
|---|---|
| Speech from `audioSpeak` mixed with synthesized music on one timeline | the `voice` track and the `music` and `drums` tracks |
| Ducking music under a voice | `audioDuck( t, "music", "voice", ... )` |
| Levelling a voice by loudness, not by its peak | `leveled()` |
| Lengths worked out from the voice: lines placed on bar lines, the music as long as it needs | `starts`, `bars` |
| Your own narration instead of speech | `--voiceFile` |

## Checked by measurement

The tests use a stand-in for the narration, 2.3 seconds of tones that switch on and off like syllables, so they need no speech engine.

| What | Result |
|---|---|
| Placement | the voice starts after two bars, and the mix lasts the bars the voice needs plus two, then the fade |
| Ducking | the bass and kick under the voice are at least 8 dB lower than the same music without ducking |
| Recovery | after the voice the music is back at the same level as before it |
| The voice | the tones of the narration are as loud during the line as the whole music was before it, with the music 12 dB down |
| Same inputs | give the identical file |
| Downloads | with `--install=false` and no engine, it fails with `bx-audio install engine` |

With a real voice, `joe` and the earlier lines, measured by hand: with the 12 dB duck the bass and kick under the voice are about 8.5 dB lower than without it, and the music alone before the voice is about as loud as the voice itself.
