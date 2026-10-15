# Examples catalog

Every example is a BoxLang script that synthesizes a complete piece of music from code, with no samples and no licensing issues. All of them are original compositions.

Run an example with the module installed:

```bash
boxlang examples/01-pop-drive/pop-drive.bxs
```

Prefer to listen in a browser? The [explorer](explorer/) is a BoxLang MiniServer app that plays any piece and lets you change its settings.

Audio is written to `out/` by default and is not committed. Each script takes `--out=`, and most take `--bars=` and `--bpm=`.

| # | Example | Style | Shows |
|---|---|---|---|
| 01 | [Pop Drive](01-pop-drive/) | Upbeat pop, 128 BPM | Voices, beat grid, kick pump, mastering. A checked port of a numpy script |
| 02 | [Lofi](02-lofi/) | Lofi hip hop, 78 BPM with swing | Swing, humanising, stems with their own effects, tape wobble, vinyl crackle |
| 03 | [House](03-house/) | House, 124 BPM | A full arrangement, sidechain pumping on four stems, filtered saw bass, stabs, riser and drop |
| 04 | [Ambient](04-ambient/) | Drone and pads, 48 BPM | Slow envelopes, inharmonic bells, noise swells, very large reverb and echo |
| 05 | [Techno](05-techno/) | Techno, 132 BPM | One chord for the whole track, rumble bass, an acid line from resonant filters, a kick-less breakdown |
| 06 | [Synthwave](06-synthwave/) | Synthwave, 100 BPM | A melody as data, a gated snare in a huge room, detuned pads and leads with chorus |
| 07 | [Cinematic](07-cinematic/) | Trailer music, 90 BPM | Dynamics from quiet to loud, a true silent beat before the boom, strings and a theme |
| 08 | [Chiptune](08-chiptune/) | 8 bit game music, five tracks | Pulse, triangle and noise channels, music written as text and read by a parser, a track per setting |
| 09 | [Game sound effects](09-sfx/) | Ten effects, as a sampler or a pack of files | Pitch glides that never click, noise shaped by envelopes, an effects pack exported as one file per sound |
| 10 | [Jazz](10-jazz/) | A twelve bar blues, swing | Swing as one number, a walking bass, rule based improvisation that follows the chords |
| 11 | [Rock](11-rock/) | Rock, 128 BPM | Distortion as saturation plus a cabinet filter, two guitar takes, fills and crashes, a seeded solo |
| 12 | [Voice-over](12-voiceover/) | Narration over music | Text to speech on a timeline with the music ducking under it, a voice levelled by loudness. Command line only, it needs the speech engine |
| 13 | [Trailer](13-trailer/) | A scored video | Music cut to the exact length of a video, a voice-over with timed captions, ducking, mastering to -14 LUFS, chapter markers and the sound muxed onto the picture |
| 14 | [Game audio](14-game-audio/) | A scripted play session | A real time mixer with buses, seamless loops, varied effects, a boss theme from time stretch and pitch shift, an audio sprite. Renders to a file or plays live |

`examples/` is covered by a test that renders each script with short settings. It checks the length, the level, that the same settings give the same file, and what each piece sounds like by measurement: how bright it is, whether it pulses on the beat, how percussive it is, and that it fades out. Pieces with their own character are checked on it too: the jazz bass starts every bar on the chord root, the music in the voice-over ducks under the voice, and the pitch of every sound effect moves the way the sound is meant to. So the catalog cannot silently break or drift.

| Piece | Highs above 5 kHz | Low end pulse at the beat | Transient strength |
|---|---|---|---|
| Pop Drive | −9.5 dB | 0.85 | 3.6 |
| Lofi | −45.8 dB | −0.03 (none) | 1.8 |
| House | −20.5 dB | 0.88 | 2.8 |
| Ambient | −47.1 dB | not meaningful | 0.7 |
| Techno | −21.8 dB | 0.90 | 3.5 |
| Synthwave | −22.3 dB | 0.84 | 2.7 |
| Cinematic | −24.5 dB | 0.43 | 4.2 |
| Chiptune (overworld) | −14.2 dB | 0.80 | 1.3 |
| Jazz (2 choruses) | −25.6 dB | 0.51 | 1.8 |
| Rock | −26.0 dB | 0.72 | 1.4 |

These are measured on the short renders that the tests use.
