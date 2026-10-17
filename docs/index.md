---
title: BxAudio
order: 1
description: BoxLang Audio makes, mixes, masters and speaks audio from BoxLang, on the JVM, with no Python or native tools.
icon: phosphor-duotone:waveform
toc: false
layout: home
ogImage: assets/og-image.png
---

Make sound from code. Synthesize instruments and drums, arrange them on a beat grid, mix with ducking, add effects, master to a loudness target, speak text with open license voices, score a video, and play sound in a game. It all runs on the JVM, in BoxLang, with no Python and nothing native to install.

```js
t = audioTimeline( bpm=128 );
kick = audioDrum( "kick" );
bass = audioFilter( audioEnvelope( audioOsc( "saw", audioFreq( "A1" ), 0.4 ), 0.003, 0.12 ), "lowpass", 900, 2 );

for( at in audioBeats( t, 0, 16 ) ){
    audioAdd( timeline=t, sound=kick, at=at, track="drums" );
}
audioAdd( timeline=t, sound=bass, at=t.bar( 1 ), track="bass", gainDb=-6 );
audioPump( t, "bass", audioBeats( t, 0, 16 ), -5, 0.18 );

mix = audioMaster( audio=audioRender( t ), target="streaming" );   // -14 LUFS, true peak under -1 dBFS
audioSave( mix, "groove.mp3" );
```

::: cards
::: card title="Get started" icon="phosphor-duotone:rocket-launch" href="getting-started.md"
Install the module, make a sound, and meet the `bx-audio` command line.
:::
::: card title="Listen to the examples" icon="phosphor-duotone:speaker-high" href="examples/index.md"
Sixteen complete pieces made from code, with a player for each. Pop, lofi, house, techno, jazz, rock, game music and more.
:::
::: card title="Score a video" icon="phosphor-duotone:film-strip" href="guides/trailers-and-video.md"
Cut music to the exact length of a picture, add a voice-over with captions, master to -14 LUFS and put the sound on the video.
:::
::: card title="Loudness and mastering" icon="phosphor-duotone:gauge" href="guides/loudness-and-mastering.md"
Measure loudness the EBU R128 way and deliver to streaming, podcast or broadcast targets.
:::
::: card title="Speak and subtitle" icon="phosphor-duotone:microphone" href="guides/speech-and-subtitles.md"
Open license voices that download on first use, with speed, pauses and captions timed to the words.
:::
::: card title="Game audio" icon="phosphor-duotone:game-controller" href="guides/games.md"
A real time mixer with buses, loops and crossfades, varied effects and audio sprites.
:::
::: card title="Voices and licenses" icon="phosphor-duotone:scales" href="voices.md"
Every voice of the collection: which can be used, which cannot, and why.
:::
::: card title="Reference" icon="phosphor-duotone:books" href="reference/functions/index.md"
All 83 functions with their arguments, and every command of the `bx-audio` command line.
:::
:::

## What is in it

| Area | What you get |
|---|---|
| Synthesis | Band-limited oscillators, noise, envelopes, sweeps, drums, filters |
| Mixing | A tempo grid, named tracks, kick pumping, voice-over ducking |
| Effects | Delay with ping pong, Freeverb reverb, chorus, saturation |
| Mastering | EBU R128 loudness, a mastering chain, compressor, limiter, EQ |
| Time and pitch | Time stretch, fit to an exact length, pitch shift |
| Speech | Text to speech with open license voices, speed, pauses, captions |
| Video | Read any video, subtitles, chapter markers, put sound on a picture |
| Games | Real time mixer, seamless loops, effect variations, audio sprites |
| Analysis | Tempo and beats, spectrum, silence |
| Formats | WAV, MP3 and FLAC built in; OGG, Opus and AAC through FFmpeg |

## Requirements

BoxLang 1.18 or newer on Java 21. WAV, FLAC, analysis and everything that makes sound need nothing else. MP3 uses two small LGPL jars shipped with the module and the JDK's `java.desktop` module. Text to speech downloads its engine and a voice the first time, and FFmpeg is found on your computer or downloaded when something needs it. Downloads go to `~/.bxaudio` and you can see and clear them with `bx-audio where` and `bx-audio clean`.
