# ⚡︎ BoxLang Module: BoxLang Audio

```
|:------------------------------------------------------:|
| ⚡︎ B o x L a n g ⚡︎
| Dynamic : Modular : Productive
|:------------------------------------------------------:|
```

<blockquote>
	Copyright Since 2023 by Ortus Solutions, Corp
	<br>
	<a href="https://www.boxlang.io">www.boxlang.io</a> |
	<a href="https://www.ortussolutions.com">www.ortussolutions.com</a>
</blockquote>

<p>&nbsp;</p>

## Introduction

BoxLang Audio makes sound from code. Synthesize instruments and drums, arrange them on a beat grid, mix tracks with ducking, add effects and save the result as WAV or MP3. It runs entirely on the JVM, so there is no ffmpeg, Python or native tool to install, and a track made on your laptop renders the same on a server.

**Key Features:**

- 🎹 Synthesis: band-limited oscillators, noise, envelopes, sweeps and a set of drums
- 🎚️ Timeline mixer with a tempo grid, named tracks, automatic sample rate and channel conversion
- 🔊 Kick-style pump and voice-over ducking for trailers and videos
- 🎛️ Filters, delay (with ping pong), Freeverb reverb and chorus
- ✂️ Edit: gain, pan, fade, normalize, trim, concat, reverse, resample
- 💾 Read and write WAV (16/24 bit PCM, 32 bit float) and MP3
- 🎼 A catalog of complete, original example songs with no samples to license
- 🔁 Repeatable: the same seed gives the same sound on any machine
- 🌐 A [catalog explorer](examples/explorer/) web app on the BoxLang MiniServer that plays every piece in the browser
- 🧩 100% Java, no native dependencies, works from the CLI, web apps and Lambda

## Why BoxLang Audio?

Audio scripts are usually Python and numpy glued to ffmpeg. Here the whole pipeline is BoxLang functions that take a buffer and return a new one, so a song reads like a recipe:

```javascript
t = audioTimeline( bpm=128 );
kick = audioDrum( "kick" );
bass = audioFilter( audioEnvelope( audioOsc( "saw", audioFreq( "A1" ), 0.4 ), 0.003, 0.12 ), "lowpass", 900, 2 );

for( at in audioBeats( t, 0, 16 ) ){
    audioAdd( timeline=t, sound=kick, at=at, track="drums" );
}
audioAdd( timeline=t, sound=bass, at=t.bar( 1 ), track="bass", db=-6 );
audioPump( t, "bass", audioBeats( t, 0, 16 ), -5, 0.18 );

audioSave( audioSaturate( audioRender( t ) ), "groove.mp3" );
```

## Quick Start

### Requirements

- **BoxLang Runtime** 1.0.0 or higher
- **MP3 only:** the optional codec jars that ship in the module's `libs/` folder (see [Licensing](#licensing))

### Installation

```bash
# Install into your BoxLang home
install-bx-module bx-audio

# Or in a CommandBox project
box install bx-audio
```

### Basic Usage

```javascript
// Make a one second A440 tone, fade it and save it
tone = audioOsc( "sine", 440, 1 );
tone = audioFade( audio=tone, fadeIn=0.01, fadeOut=0.2, curve="smooth" );
audioSave( tone, "tone.wav" );

// Read it back and inspect it
println( audioInfo( "tone.wav" ) );
```

## Quick Reference

Every function returns a new buffer and leaves its input untouched. BoxLang does not allow mixing positional and named arguments in one call, so use all of one style.

### Load, save and edit

| Function | Purpose |
|---|---|
| `audioSilence( seconds, channels=2, sampleRate=44100 )` | Create a blank audio buffer |
| `audioLoad( path )` | Read a WAV (PCM 8/16/24/32 bit and 32/64 bit float) or MP3 file, detected by content |
| `audioSave( audio, path, bitDepth=16, bitrate=192 )` | Write WAV (16 bit, 24 bit or 32 bit float) or MP3 (by `.mp3` extension, `bitrate` in kbps, mono or stereo) |
| `audioInfo( audioOrPath )` | Channels, sample rate, frames, seconds, peak and RMS |
| `audioWaveform( audio, points=800 )` | The loudest level in each of `points` equal slices, as an array for drawing a waveform |
| `audioGain( audio, db )` | Change the volume in decibels |
| `audioPan( audio, pan )` | Place a sound from -1 (left) to 1 (right) with a constant power law. Mono becomes stereo, stereo acts as a balance |
| `audioFade( audio, fadeIn=0, fadeOut=0, curve="linear" )` | Fade in and out, `curve` is `linear` or `smooth` |
| `audioNormalize( audio, targetDb=-1 )` | Scale the peak to a target level |
| `audioTrim( audio, start=0, end )` | Cut a section, in seconds |
| `audioConcat( audios )` | Join buffers end to end, formats must match |
| `audioReverse( audio )` | Play it backwards |
| `audioMix( audios )` | Add buffers together so they play at the same time, as long as the longest |
| `audioChannels( audio, channels )` | Convert between mono and stereo |
| `audioResample( audio, sampleRate )` | Change the sample rate with a windowed sinc filter, with anti-aliasing when lowering it |

### Synthesis

Sounds are made from code, with no samples to download or license. Generators return mono buffers at a known level, so they mix predictably.

| Function | Purpose |
|---|---|
| `audioFreq( note )` | A note name (`"C4"`, `"F#3"`, `"Bb2"`) or MIDI number to Hz, A4 = 440. `60.1` is ten cents sharp of C4 |
| `audioOsc( shape, freq, seconds, sampleRate=44100, harmonics=16, partials, width=0.5, phase=0 )` | `sine`, `saw`, `square`, `triangle` (band limited, peak about 1), `pulse` (naive digital, `width` 0.125 / 0.25 / 0.5 / 0.75 for NES style) or `additive` from `[ multiple, amplitude ]` pairs |
| `audioSweep( startHz, endHz, seconds, speed=28 )` | A sine that glides between pitches, the core of a kick or a laser |
| `audioNoise( seconds, sampleRate=44100, seed=1, color="white" )` | Repeatable noise: `white`, `pink`, `brown` or `blue`, at RMS 0.25 |
| `audioEnvelope( audio, attack=0.005, decay=0 )` | Linear attack then exponential decay, `decay` is the time constant |
| `audioDrum( type, sampleRate=44100, seed=1, pitch=1, decay=1, level=1, seconds )` | `kick`, `snare`, `hat`, `openhat`, `clap`, `tom`, `crash`, `riser`, peak-normalized to `level` |
| `audioFilter( audio, type, cutoff, q=0.7071 )` | `lowpass`, `highpass`, `bandpass` or `notch` |

The same seed always gives the same sound on any machine. It does not match numpy's random stream, so a port of a numpy script sounds the same but is not bit identical.

```js
// A plucked bass note and a kick, mixed on a beat grid
t = audioTimeline( bpm=128 );
pluck = audioEnvelope( audioOsc( "saw", audioFreq( "A1" ), 0.4 ), 0.003, 0.12 );
pluck = audioFilter( pluck, "lowpass", 900, 2 );
kick = audioDrum( "kick" );

for( at in audioBeats( t, 0, 16 ) ){
    audioAdd( timeline=t, sound=kick, at=at, track="drums" );
}
audioAdd( timeline=t, sound=pluck, at=t.bar( 1 ), track="bass", db=-6 );
audioSave( audioSaturate( audioRender( t ) ), "out/groove.wav" );
```

### Effects

Echo and reverb return a longer buffer so the tail rings out instead of being cut off. `mix` is a dry/wet crossfade from 0 to 1.

| Function | Purpose |
|---|---|
| `audioDelay( audio, time=0.35, feedback=0.4, mix=0.35, pingPong=false, tail )` | Echo. Repeats run until 60 dB down (at most 10 s) unless `tail` is given. `pingPong` bounces them between stereo sides |
| `audioReverb( audio, room=0.5, damp=0.5, mix=0.3, tail )` | Freeverb style reverb: 8 damped combs into 4 allpass filters, stereo spread. Large rooms build up level on sustained sounds, so lower the mix |
| `audioChorus( audio, rate=0.8, depth=0.003, mix=0.5 )` | A slowly swinging short delay for width and thickness |

```js
// A snare with a dotted eighth ping pong echo and a hall
t = audioTimeline( bpm=100 );
snare = audioChannels( audioDrum( type="snare" ), 2 );
echoed = audioDelay( audio=snare, time=t.beat( 0.75 ), feedback=0.45, mix=0.3, pingPong=true );
hall = audioReverb( audio=echoed, room=0.7, damp=0.4, mix=0.25 );
audioSave( hall, "out/snare-hall.wav" );
```

### Timeline mixing

A timeline holds named tracks at one sample rate and a tempo grid. Sounds are placed on tracks, ducking and track volumes are applied when it is rendered, so the stored tracks are never altered.

| Function | Purpose |
|---|---|
| `audioTimeline( bpm=120, beatsPerBar=4, sampleRate=44100, channels=2 )` | Create a timeline. `t.beat( n )` and `t.bar( n )` turn beats and bars into seconds |
| `audioAdd( timeline, sound, at=0, track="main", db=0, pan=0 )` | Place a sound at a time. Other sample rates are converted automatically, mono sounds are panned |
| `audioBeats( timeline, start=0, end, step=1 )` | List moments on the beat grid in seconds, ready for `audioAdd( at= )` or `audioPump( times= )` |
| `audioTrackGain( timeline, track, db )` | Set the volume of a whole track |
| `audioPump( timeline, track, times, amountDb=-5, recovery=0.18 )` | Dip a track at given moments, like a kick drum sidechain |
| `audioDuck( timeline, track, trigger, amountDb=-12, thresholdDb=-40, attack=0.01, release=0.3 )` | Lower a track while another is loud, like music under a voice-over |
| `audioRender( timeline, tail=0 )` | Mix all tracks down to one buffer. The timeline can be changed and rendered again |
| `audioSaturate( audio, drive=2.2, level=0.9 )` | Peak-normalize and round off peaks with a tanh curve, a warm limiter for the final mix |

> BoxLang does not allow mixing positional and named arguments in one call. Use all positional (`audioPump( t, "music", times, -4, 0.1 )`) or all named (`audioAdd( timeline=t, sound=kick, at=t.beat( 4 ), track="drums" )`).

```js
t = audioTimeline( bpm=128 );
voice = audioLoad( "voice.wav" );   // any sample rate, it is converted for you

audioAdd( timeline=t, sound=music, at=0, track="music", db=-10 );
audioAdd( timeline=t, sound=voice, at=t.bar( 2 ), track="voice" );

audioPump( t, "music", audioBeats( t, 0, 64 ), -4, 0.15 );   // pump on every beat
audioDuck( t, "music", "voice", -9 );                         // and duck under the voice

mix = audioFade( audioRender( timeline=t, tail=1 ), 0, 1, "smooth" );
audioSave( audioSaturate( mix ), "out/trailer-mix.wav" );
```

An audio buffer also exposes `getChannels()`, `getFrames()`, `getSampleRate()`, `getDurationSeconds()`, `getPeak()`, `getRms()` and `copy()`.

```js
// Bring a mono 22.05kHz voice-over up to a 44.1kHz stereo mixdown format
voice = audioLoad( "voice.wav" );
voice = audioResample( voice, 44100 );
voice = audioChannels( voice, 2 );
voice = audioNormalize( voice, -3 );
voice = audioFade( audio=voice, fadeIn=0.05, fadeOut=0.3, curve="smooth" );
audioSave( voice, "out/voice-stereo.wav", 24 );
println( audioInfo( "out/voice-stereo.wav" ) );
```

## Examples

The [examples catalog](examples/) holds complete pieces of music made from code, with no samples or licensing concerns. Start with [Pop Drive](examples/01-pop-drive/), a 55 second pop track that was checked against an original numpy script, then [Lofi](examples/02-lofi/), [House](examples/03-house/), [Ambient](examples/04-ambient/), [Techno](examples/05-techno/), [Synthwave](examples/06-synthwave/) and [Cinematic](examples/07-cinematic/).

```bash
boxlang examples/01-pop-drive/pop-drive.bxs --out=out/pop-drive.wav
```

To listen in a browser, start the [explorer](examples/explorer/) with the MiniServer: `cd examples/explorer && boxlang-miniserver --webroot .`.

## Command Line

Installing the module with `install-bx-module` also installs a `bx-audio` command and its bash tab completion, so you can work with audio from a shell without writing a script. Without the installer, the same commands run as `boxlang module:bxaudio <command>`.

```bash
bx-audio render techno --bars=16 --out=techno.mp3   # synthesize an example piece
bx-audio info techno.mp3                            # format, length, peak and RMS
bx-audio convert song.wav song.mp3 --bitrate=192    # WAV to MP3, with --sampleRate and --channels
bx-audio normalize voice.wav loud.wav --db=-3
bx-audio trim song.wav intro.wav --start=0 --end=12
bx-audio waveform song.wav                          # one line of loudness, add --unicode for blocks
bx-audio explorer                                   # the web explorer, on the BoxLang MiniServer
bx-audio doctor                                     # checks MP3, the examples and the MiniServer
```

| Command | Purpose |
|---|---|
| `info <file>` | Format, channels, sample rate, length, peak and RMS of a WAV or MP3 file |
| `convert <in> <out>` | Convert between WAV and MP3. Options: `--bitrate`, `--bitDepth`, `--sampleRate`, `--channels` |
| `normalize <in> <out>` | Scale the peak to `--db` (default -1) |
| `trim <in> <out>` | Keep the section from `--start` to `--end`, in seconds |
| `waveform <file>` | Draw the loudness on one line, `--width` characters wide |
| `pieces` | List the example pieces |
| `render <piece>` | Synthesize an example piece. Takes `--out` and the settings of that piece, such as `--bars` and `--bpm` |
| `explorer` | Start the web explorer on `--port` (default 8080). Needs the BoxLang MiniServer on your PATH |
| `doctor` | Check that MP3, the examples and the explorer will work on this machine |

Paths are relative to the folder you run the command in. Run `bx-audio help <command>` for the options of one command.

## Roadmap

1. ~~DSP: gain, pan, fade, normalize, trim, concat, reverse, channels, resample~~ (done)
2. ~~Timeline mixing with a beat grid, ducking, and offline rendering~~ (done)
3. ~~Synthesis: oscillators, envelopes, noise, drums, filters~~ (done), ~~effects: delay, reverb, chorus~~ (done)
4. A catalog of example music scripts (pop, lofi, house, ambient, techno, synthwave and cinematic done; chiptune, jazz, rock, game sound effects and more to follow, all original compositions)
5. Text-to-speech through a pluggable provider, with a content-hash cache
6. Analysis: ~~waveform data~~ done, loudness, silence detection, FFT
7. Realtime playback
8. ~~MP3 read and write as optional codec jars~~ done (see below)

## Licensing

The module is Apache 2.0. MP3 uses JLayer and jump3r (both LGPL), shipped as separate, replaceable jars in the module's `libs/` folder and never bundled into the module jar. Without them WAV still works and MP3 calls raise a clear error. MP3 encoding needs the `java.desktop` module in the JVM. Details and license texts: `licenses/THIRD-PARTY.md`.

## Development

```bash
./gradlew downloadBoxLang   # once, fetches the BoxLang jar used for compiling and tests
./gradlew spotlessApply test
```

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com). Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more. If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)

### THE DAILY BREAD

 > "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
