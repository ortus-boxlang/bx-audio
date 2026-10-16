---
title: Try it
order: 3
summary: Hear it in your browser, run a recipe for every feature, or clone the repository and run all of it.
---

There are three ways to try BxAudio, from nothing to install to the whole repository. Pick the one that fits.

| You want to | Do this |
|---|---|
| Hear the ideas in two minutes | The [playground on the home page](index.md): turn the knobs of a kick, a bass, an arpeggio and a chime in your browser |
| See every feature work on your computer | Install the module and run the [recipes](#recipes) below. Each one is a few lines and writes a file |
| Read the code, run the tests, change things | [Clone the repository](#run-it-from-a-clone) |

Nothing here plays sound by itself. The browser playground and the player only start when you press a button, and every recipe writes a file you open when you want to.

## Recipes

Install the module first (see [Getting started](getting-started.md)), then save a recipe as a `.bxs` file and run it with `boxlang recipe.bxs`. The first six need nothing else. Speech downloads a voice once, and video needs FFmpeg, which `bx-audio ffmpeg install` fetches for you.

### 1. Make a sound

A kick, a filtered bass note and a bell, saved as files. See [Synthesis and effects](guides/synthesis-and-effects.md).

<!-- run -->
```js
kick = audioDrum( type="kick", pitch=0.95, decay=0.9, level=0.95 );
bass = audioFilter( audioOsc( "saw", audioFreq( "D2" ), 0.4 ), "lowpass", 900, 2.4 );
bass = audioEnvelope( bass, 0.003, 0.25 );
bell = audioEnvelope( audioOsc( "sine", audioFreq( "A5" ), 1.5 ), 0.002, 1.1 );

audioSave( kick, "kick.wav" );
audioSave( bass, "bass.wav" );
audioSave( bell, "bell.wav" );
println( audioInfo( "kick.wav" ) );
```

### 2. Make a beat

Four bars on a timeline: a kick on every beat, a bass line, and the bass ducking under the kick. See [Mixing](guides/mixing.md).

<!-- run -->
```js
t = audioTimeline( bpm=128 );
kick = audioDrum( type="kick" );
hat = audioDrum( type="hat", seed=3, level=0.4 );
bass = audioEnvelope( audioFilter( audioOsc( "saw", audioFreq( "A1" ), 0.4 ), "lowpass", 800, 2 ), 0.003, 0.2 );

for( at in audioBeats( t, 0, 16 ) ){
	audioAdd( timeline=t, sound=kick, at=at, track="drums" );
}
for( at in audioBeats( t, 0.5, 16 ) ){
	audioAdd( timeline=t, sound=hat, at=at, track="drums", gainDb=-12 );
}
audioAdd( timeline=t, sound=bass, at=0, track="bass", gainDb=-6 );
audioPump( t, "bass", audioBeats( t, 0, 16 ), -5, 0.18 );

audioSave( audioRender( t ), "beat.wav" );
```

### 3. Add effects

Reverb and a ping-pong echo on a short melody. See [Synthesis and effects](guides/synthesis-and-effects.md).

<!-- run -->
```js
notes = [ "D5", "F5", "A5", "D6" ].map( ( n ) => audioEnvelope( audioOsc( "triangle", audioFreq( n ), 0.6 ), 0.002, 0.4 ) );
melody = audioChannels( audioConcat( notes ), 2 );            // ping pong needs stereo
wet = audioDelay( audio=melody, time=0.3, feedback=0.4, mix=0.35, pingPong=true );
wet = audioReverb( audio=wet, room=0.7, damp=0.4, mix=0.3 );
audioSave( wet, "melody.wav" );
```

### 4. Measure and master

Measure loudness the EBU R128 way, then master to the streaming target. See [Loudness and mastering](guides/loudness-and-mastering.md).

<!-- run -->
```js
mix = audioGain( audioMix( [ audioOsc( "saw", audioFreq( "A2" ), 3 ), audioOsc( "saw", audioFreq( "E3" ), 3 ) ] ), -12 );
println( "before: " & audioLoudness( mix ).integrated & " LUFS" );

mastered = audioMaster( audio=mix, target="streaming" );
println( "after: " & audioLoudness( mastered ).integrated & " LUFS, true peak " & audioLoudness( mastered ).truePeakDb & " dBFS" );
audioSave( mastered, "mastered.wav" );
```

### 5. Change time and pitch

Stretch a clip without changing its pitch, shift its pitch without changing its length, and fit it to an exact length. See [Time and pitch](guides/time-and-pitch.md).

<!-- run -->
```js
clip = audioEnvelope( audioOsc( "saw", audioFreq( "C4" ), 2 ), 0.01, 1.5 );

slower = audioStretch( clip, 0.8 );
higher = audioPitch( clip, 3 );
exact = audioFitTo( clip, 3.0 );

println( "stretched: " & audioInfo( slower ).seconds & " s, pitched: " & audioInfo( higher ).seconds & " s, fitted: " & audioInfo( exact ).seconds & " s" );
audioSave( exact, "fitted.wav" );
```

### 6. Analyze audio

Tempo, spectrum and silence. See [Analysis](guides/analysis.md).

<!-- run -->
```js
t = audioTimeline( bpm=120 );
for( at in audioBeats( t, 0, 16 ) ){
	audioAdd( timeline=t, sound=audioDrum( type="kick" ), at=at, track="drums" );
}
beat = audioRender( t );

println( "tempo: " & audioBpm( beat ).bpm & " BPM" );
println( "spectrum bands: " & audioSpectrum( audio=beat, bands=8 ).len() );
println( "silent stretches: " & audioDetectSilence( beat ).len() );
```

### 7. Speak and subtitle

Text to speech with an open license voice. The first run downloads the speech engine and the voice, and `bx-audio where` shows where they go. See [Speech and subtitles](guides/speech-and-subtitles.md).

```bash
bx-audio voices                                           # the voices you can use
bx-audio speak "Hello from BxAudio." --voice=en_US-joe-medium --out=hello.mp3 --captions=hello.srt
```

### 8. Score a video

Put sound on a picture without re-encoding the video. Needs FFmpeg (`bx-audio ffmpeg install`). See [Trailers and video](guides/trailers-and-video.md).

```bash
bx-audio render theme --out=theme.wav
bx-audio mux my-video.mp4 theme.wav my-video-scored.mp4
```

### 9. Game audio

A real time mixer with buses, loops, varied effects and an audio sprite. See [Games](guides/games.md).

<!-- run -->
```js
mixer = audioMixer( output="memory", buses={ music: 0.7, sfx: 1.0 } );
jump = audioGain( audioSweep( 260, 760, 0.18 ), -8 );
song = audioGain( audioOsc( "triangle", audioFreq( "A2" ), 2 ), -10 );

mixer.play( song, { bus: "music", loop: true } );
mixer.play( jump, { bus: "sfx", pan: -0.4, pitch: 1.1 } );
session = mixer.render( 1.0 );
mixer.close();

audioSave( session, "session.wav" );
```

### 10. The whole piece

Every example renders from the command line, including the BxAudio theme.

```bash
bx-audio pieces                                           # the 15 pieces
bx-audio render theme --out=theme.mp3
bx-audio render techno --bars=16 --out=techno.mp3
bx-audio explorer                                         # play them all in the browser
```

## Run it from a clone

Clone the repository to read the code, run the tests or change the module. You need Java 21.

```bash
git clone https://github.com/ortus-boxlang/bx-audio.git
cd bx-audio

./gradlew downloadBoxLang          # once: the BoxLang jar the build uses
./gradlew build                    # compiles, runs the tests, and assembles the module in build/module
```

Then put your build where BoxLang looks for modules, and check it:

```bash
mkdir -p ~/.boxlang/modules
rm -rf ~/.boxlang/modules/bxaudio
cp -r build/module ~/.boxlang/modules/bxaudio

boxlang module:bxaudio doctor      # checks MP3, FFmpeg, the sound card, the examples and the explorer
boxlang module:bxaudio pieces      # lists the example pieces
```

From the clone you can also run the example scripts directly, and the documentation snippets the tests run:

```bash
boxlang examples/15-theme/theme.bxs --out=theme.wav        # the BxAudio theme
boxlang examples/03-house/house.bxs --out=house.wav
./gradlew test                                              # every function, command and example is covered
./gradlew test --tests '*DocsTest'                          # runs the recipes on this page
```

The `<!-- run -->` snippets above are executed by `DocsTest`, so a recipe that stops working fails the build. See [Contribute](contribute.md) for the rest of the workflow.

## When something does not work

[Troubleshooting](troubleshooting.md) lists the usual causes (no sound card, missing FFmpeg, a download that was blocked), and `bx-audio doctor` checks them for you.
