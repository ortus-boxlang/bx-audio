---
title: Roadmap
order: 2
summary: What is done and what is next.
---

## Done

1. DSP: gain, pan, fade, normalize, trim, concat, reverse, channels, resample
2. Timeline mixing with a beat grid, ducking, pumping and offline rendering
3. Synthesis (oscillators, envelopes, noise, drums, filters) and effects (delay, reverb, chorus, saturation)
4. A catalog of sixteen example scripts, original music, measured and tested
5. Text to speech with open license voices, speed, pauses, timed captions and SRT and WebVTT subtitles
6. Analysis: waveform, loudness (EBU R128), tempo and beats, spectrum, silence
7. Mastering: loudness targets with a true-peak limiter, compressor, limiter, EQ
8. Time stretch, fit to length and pitch shift
9. WAV, MP3 and FLAC built in; OGG, Opus, AAC and video through FFmpeg, found or downloaded
10. A real time mixer for games, seamless loops, effect variations, audio sprites
11. Downloads in `~/.bxaudio`, with commands to show and clear them
12. The `bx-audio` command line, the web explorer and this documentation
13. Audio as bytes and an HTTP response with range requests, ETag and 304, for web apps
14. Tags, cover art and chapters for MP3, FLAC and WAV
15. Waveform and spectrogram pictures as SVG and PNG
16. A sampler: an instrument from any recording, with notes, chords and a looped sustain
17. Curves and LFOs for tremolo, auto pan, filter sweeps and vibrato, and automation of the tracks of a timeline
18. Convolution reverb from a recorded room
19. MP3 that keeps its length and timing through a save and a load

## Next

- More languages for speech, and multi-speaker voices
- MIDI file rendering (the JDK's software synthesizer cannot be driven offline from outside the JDK, so this needs a synthesizer of our own or a library)
- A pitch shift that keeps the character of a voice
- A cache of what was spoken, so the same line is not synthesized twice
- Word level caption timings

## Release notes

The changelog is kept in the repository: [changelog.md](https://github.com/ortus-boxlang/bx-audio/blob/development/changelog.md).
