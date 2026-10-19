<p align="center">
	<picture>
		<source media="(prefers-color-scheme: dark)" srcset="docs/assets/bxaudio-logo-full-dark.svg">
		<img alt="BxAudio" src="docs/assets/bxaudio-logo-full-light.svg" width="420">
	</picture>
</p>

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

BoxLang Audio makes sound from code. Synthesize instruments and drums, arrange them on a beat grid, mix tracks with ducking, add effects and save the result as WAV, MP3 or FLAC. It runs entirely on the JVM, so there is no Python or native tool to install, and a track made on your laptop renders the same on a server. WAV, MP3 and FLAC need nothing else; OGG, Opus, AAC and video files use FFmpeg, which the module finds on your computer or downloads for you.

**Key Features:**

- 🎹 Synthesis: band-limited oscillators, noise, envelopes, sweeps and a set of drums
- 🎚️ Timeline mixer with a tempo grid, named tracks, automatic sample rate and channel conversion
- 🔊 Kick-style pump and voice-over ducking for trailers and videos
- 🎛️ Filters, delay (with ping pong), Freeverb reverb, convolution reverb from a real room, and chorus
- 🌊 Curves and LFOs that move volume, position, filter or pitch while a sound plays: tremolo, auto pan, filter sweeps, vibrato, and track automation on the timeline
- 🎹 Sampler: an instrument from any recording, with notes, chords and a looped sustain
- ✂️ Edit: gain, pan, fade, normalize, trim, concat, reverse, resample
- 💾 Read and write WAV (16/24 bit PCM, 32 bit float), MP3 and FLAC, and with FFmpeg OGG, Opus, AAC and the audio of any video
- 📏 Loudness measured the EBU R128 way and mastering to a target (-14 LUFS for streaming), with a true-peak limiter
- ⏱️ Time stretch and pitch shift, to cut music to the exact length of a video
- 📝 Subtitles (SRT, WebVTT) timed to the text to speech voice, and chapter markers
- 🎮 A real time mixer for games: buses, volume, pan, pitch, loops, crossfades, audio sprites
- 🔬 Analysis: tempo and beats, spectrum, silence, and waveform and spectrogram pictures as SVG or PNG
- 🏷️ Tags, cover art and chapters for MP3, FLAC and WAV, and audio as bytes with an HTTP response that handles Range, ETag and 304
- 🎼 A catalog of complete, original example songs with no samples to license
- 🗣️ Text to speech with open license voices that download on first use, no API key or cloud service
- 🔁 Repeatable: the same seed gives the same sound on any machine
- 🌐 A [catalog explorer](examples/explorer/) web app on the BoxLang MiniServer that plays every piece that does not need the speech engine (14 of 16) in the browser
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
audioAdd( timeline=t, sound=bass, at=t.bar( 1 ), track="bass", gainDb=-6 );
audioPump( t, "bass", audioBeats( t, 0, 16 ), -5, 0.18 );

audioSave( audioSaturate( audioRender( t ) ), "groove.mp3" );
```

## Quick Start

### Requirements

- **BoxLang Runtime** 1.18.0 or higher
- **Java 21 or newer**, a full JDK (MP3 writing needs the `java.desktop` module)
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
| `audioLoad( path )` | Read a WAV, MP3 or FLAC file, or any other format through FFmpeg, detected by content. An MP3 gives back the length it was saved with |
| `audioSave( audio, path, bitDepth=16, bitrate=0 )` | Write by the extension: WAV (16, 24 or 32 bit float), MP3 (`bitrate` in kbps, default 192 or 128 below 32 kHz, mono or stereo), FLAC, or OGG, Opus, M4A and AAC through FFmpeg |
| `audioInfo( source, levels=false )` | Channels, sample rate, frames and seconds of a buffer or a file, and with `levels=true` the peak and RMS |
| `audioWaveform( audio, points=800 )` | The loudest level in each of `points` equal slices, as an array for drawing a waveform |
| `audioGain( audio, gainDb )` | Change the volume in decibels |
| `audioPan( audio, pan )` | Place a sound from -1 (left) to 1 (right) with a constant power law. Mono becomes stereo, stereo acts as a balance |
| `audioFade( audio, fadeIn=0, fadeOut=0, curve="linear" )` | Fade in and out, `curve` is `linear` or `smooth` |
| `audioNormalize( audio, targetDb=-1 )` | Scale the peak to a target level |
| `audioTrim( audio, start=0, end )` | Cut a section, in seconds |
| `audioConcat( audios )` | Join buffers end to end, formats must match |
| `audioReverse( audio )` | Play it backwards |
| `audioMix( audios )` | Add buffers together so they play at the same time, as long as the longest |
| `audioChannels( audio, channels )` | Convert between mono and stereo |
| `audioResample( audio, sampleRate )` | Change the sample rate with a windowed sinc filter, with anti-aliasing when lowering it |
| `audioTags( path, tags )` | Read the tags of a WAV, MP3 or FLAC file, with the cover and chapters, or write them. Changes are atomic |
| `audioEncode( audio, format="wav", bitDepth=16, bitrate=0 )` | Audio as the bytes of a file, without touching the disk |
| `audioDecode( data, format="" )` | Audio from bytes in memory, such as an upload, the format found from the content |

### Synthesis

Sounds are made from code, with no samples to download or license. Generators return mono buffers at a known level, so they mix predictably.

| Function | Purpose |
|---|---|
| `audioFreq( note )` | A note name (`"C4"`, `"F#3"`, `"Bb2"`) or MIDI number to Hz, A4 = 440. `60.1` is ten cents sharp of C4 |
| `audioOsc( shape, freq, seconds, sampleRate=44100, harmonics=16, partials, width=0.5, phase=0 )` | `sine`, `saw`, `square`, `triangle` (band limited, peak about 1), `pulse` (naive digital, `width` 0.125 / 0.25 / 0.5 / 0.75 for NES style) or `additive` from `[ multiple, amplitude ]` pairs |
| `audioSweep( startHz, endHz, seconds, speed=28, sampleRate=44100 )` | A sine that glides between pitches, the core of a kick or a laser |
| `audioNoise( seconds, sampleRate=44100, seed=1, color="white" )` | Repeatable noise: `white`, `pink`, `brown` or `blue`, at RMS 0.25 |
| `audioEnvelope( audio, attack=0.005, decay=0 )` | Linear attack then exponential decay, `decay` is the time constant |
| `audioDrum( type, sampleRate=44100, seed=1, pitch=1, decay=1, level=1, seconds )` | `kick`, `snare`, `hat`, `openhat`, `clap`, `tom`, `crash`, `riser`, peak-normalized to `level` |
| `audioFilter( audio, type, cutoff, q=0.7071 )` | `lowpass`, `highpass`, `bandpass` or `notch` |
| `audioSampler( audio, rootNote="C4", loop=false, loopStart=0, loopEnd=0, crossfade=0.02 )` | An instrument made from a recording, optionally with a looped sustain |
| `audioNote( sampler, note, seconds=0, velocity=1, attack=0.003, release=0.05 )` | One note, or a chord from an array of notes, played from a sampler |

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
audioAdd( timeline=t, sound=pluck, at=t.bar( 1 ), track="bass", gainDb=-6 );
audioSave( audioSaturate( audioRender( t ) ), "out/groove.wav" );
```

### Effects

Echo and reverb return a longer buffer so the tail rings out instead of being cut off. `mix` is a dry/wet crossfade from 0 to 1.

| Function | Purpose |
|---|---|
| `audioDelay( audio, time=0.35, feedback=0.4, mix=0.35, pingPong=false, tail )` | Echo. Repeats run until 60 dB down (at most 10 s) unless `tail` is given. `pingPong` bounces them between stereo sides |
| `audioReverb( audio, room=0.5, damp=0.5, mix=0.3, tail )` | Freeverb style reverb: 8 damped combs into 4 allpass filters, stereo spread. Large rooms build up level on sustained sounds, so lower the mix |
| `audioChorus( audio, rate=0.8, depth=0.003, mix=0.5 )` | A slowly swinging short delay for width and thickness |
| `audioConvolve( audio, impulse, mix=0.3 )` | Convolution reverb: the sound played through the recorded echo of a real space (an impulse response up to 20 seconds) |

### Modulation and automation

A curve is a value that changes over time. Point it at the volume, position, filter or pitch of a sound, or of a track on a timeline.

| Function | Purpose |
|---|---|
| `audioLfo( shape="sine", rate=1, depth=1, center=0, phase=0, fadeIn=0, seed=0 )` | A curve that swings around `center` (`sine`, `triangle`, `saw`, `square` or `random`) |
| `audioCurve( points, interpolation="linear" )` | A curve through points (`{ at, value }`), `linear`, `smooth` or `step` |
| `audioCurveAt( curve, seconds )` | What a curve says at a time |
| `audioModulate( audio, param, curve, filter="lowpass", q=0.7071 )` | Drive `gainDb`, `pan`, `cutoff` or `pitch` of a sound with a curve: tremolo, auto pan, filter sweeps, vibrato |
| `audioAutomate( timeline, track, param, curve )` | Move a track's `gainDb` or `pan` during the render |

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
| `audioAdd( timeline, sound, at=0, track="main", gainDb=0, pan=0 )` | Place a sound at a time. Other sample rates are converted automatically, mono sounds are panned |
| `audioBeats( timeline, start=0, end, step=1 )` | List moments on the beat grid in seconds, ready for `audioAdd( at= )` or `audioPump( times= )` |
| `audioTrackGain( timeline, track, gainDb )` | Set the volume of a whole track |
| `audioPump( timeline, track, times, amountDb=-5, recovery=0.18 )` | Dip a track at given moments, like a kick drum sidechain |
| `audioDuck( timeline, track, trigger, amountDb=-12, thresholdDb=-40, attackMs=10, releaseMs=300 )` | Lower a track while another is loud, like music under a voice-over |
| `audioRender( timeline, tail=0 )` | Mix all tracks down to one buffer. The timeline can be changed and rendered again |
| `audioSaturate( audio, drive=2.2, level=0.9 )` | Peak-normalize and round off peaks with a tanh curve, a warm limiter for the final mix |

> BoxLang does not allow mixing positional and named arguments in one call. Use all positional (`audioPump( t, "music", times, -4, 0.1 )`) or all named (`audioAdd( timeline=t, sound=kick, at=t.beat( 4 ), track="drums" )`).

```js
t = audioTimeline( bpm=128 );
voice = audioLoad( "voice.wav" );   // any sample rate, it is converted for you

audioAdd( timeline=t, sound=music, at=0, track="music", gainDb=-10 );
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

### Loudness and mastering

Loudness is measured as ITU-R BS.1770 and EBU R128 define it (K-weighting, gated blocks, true peak), the numbers streaming services and broadcasters use. Checked against FFmpeg's `ebur128` on rendered music: within 0.1 LU.

| Function | Purpose |
|---|---|
| `audioLoudness( audio )` | `integrated` LUFS, `range` LU, `truePeakDb` and `samplePeakDb` dBFS, `momentaryMax` and `shortTermMax` LUFS. Silence reads -120 |
| `audioNormalizeLoudness( audio, targetLufs=-14, truePeakDb=-1 )` | Set the loudness, with a limiter keeping peaks under the ceiling |
| `audioMaster( audio, target="streaming", truePeakDb=-1, glue=false )` | The same with presets: `streaming` -14, `podcast` -16, `game` -16, `broadcast` -23 (EBU R128), `cinema` -24, or a number. `glue` compresses lightly first |
| `audioCompress( audio, thresholdDb=-18, ratio=4, attackMs=10, releaseMs=100, kneeDb=6, makeupDb="auto" )` | A compressor. `auto` make-up matches the loudness of the input |
| `audioLimit( audio, ceilingDb=-1, releaseMs=100, lookAheadMs=5 )` | A look-ahead limiter: no sample ends above the ceiling |
| `audioEq( audio, bands )` | Parametric EQ. Each band is `{ type, freq, gainDb, q }` with `type` `peak`, `lowshelf`, `highshelf`, `lowpass`, `highpass` or `notch` |

```js
mix = audioRender( timeline=t );
println( audioLoudness( mix ).integrated );          // -19.5
final = audioMaster( audio=mix, target="streaming" ); // -14 LUFS, true peak under -1 dBFS
```

### Time and pitch

| Function | Purpose |
|---|---|
| `audioStretch( audio, rate )` | Change the speed without changing the pitch. `1.25` is faster and shorter, between 0.25 and 4 |
| `audioFitTo( audio, seconds )` | Change the length to exactly this many seconds, keeping the pitch. Keep the change within about 25 percent for natural results |
| `audioPitch( audio, semitones )` | Change the pitch without changing the length, within 24 semitones |

This is WSOLA (waveform similarity overlap-add), written for this module: good on music and speech, with the usual limits of the method on dense chords at large changes. Pitch shifting does not keep the character of a voice (formants move with the pitch).

### Analysis

| Function | Purpose |
|---|---|
| `audioBpm( audio, minBpm=60, maxBpm=200 )` | `bpm` (0 when there is no clear pulse), `confidence` and the `beats` in seconds |
| `audioSpectrum( audio, bands=16, minHz=40, maxHz=16000, start=0, seconds=0 )` | Level in dB of log spaced bands |
| `audioSpectrogram( audio, bands=16, frames=64, minHz=40, maxHz=16000 )` | Band levels over time, for drawing |
| `audioFft( audio, size=2048, start=0 )` | The raw `frequencies` and `magnitudes` of one window |
| `audioDetectSilence( audio, thresholdDb=-50, minSeconds=0.3 )` | The silent stretches as `{ start, end, duration }` |
| `audioTrimSilence( audio, thresholdDb=-50, pad=0 )` | Cut the silence off both ends |
| `audioWaveformImage( audio, width=800, height=160, format="", path="", color="#3b82f6", background="", style="filled", split=false )` | The waveform as an SVG or PNG picture, written to a path or returned |
| `audioSpectrogramImage( audio, width=800, height=256, format="", path="", minHz=40, maxHz=16000, rangeDb=80, scale="magma" )` | The spectrogram as a PNG, with `magma`, `viridis` or `gray` colors |

### Formats and FFmpeg

WAV, MP3 and FLAC are read and written by the module itself (FLAC lossless, in plain Java, verified against FFmpeg). OGG Vorbis, Opus, AAC and M4A, and reading the audio of any video or other format, use FFmpeg as a separate program. The module looks for it in this order: the `ffmpeg` module setting, the `BX_AUDIO_FFMPEG` variable, `~/.bxaudio/ffmpeg`, then the PATH. If there is none it downloads one for your computer (about 66 MB on Linux and macOS, 110 MB on Windows) into `~/.bxaudio`, checked against a pinned checksum, unless `autoInstall` is false.

| Function | Purpose |
|---|---|
| `audioFfmpeg()` | Is FFmpeg available, where from, which version and encoders, and what a download would be |
| `audioFfmpegInstall( onProgress )` | Download it now |
| `videoInfo( path )` | Length, size, video and audio streams of any media file |
| `audioMux( video, audio, output, keepOriginal=false, originalDb=-12, offset=0, bitrate=0 )` | Put audio on a video, copying the picture untouched; never longer than the video |
| `audioMarkers( markers, format="json", path="", fps=30, total=0 )` | Markers as `json`, `csv`, a Resolve `edl`, `srt`, `vtt`, FFmpeg chapters (`ffmetadata`) or a `youtube` chapter list |
| `audioHttpResponse( source, request, format="", bitDepth=16, bitrate=0, maxAgeSeconds=3600, filename="", download=false )` | What to send for a request for audio: status, headers and body for Range, ETag and 304 |

`audioSave( audio, "out.ogg", 16, 96 )` and `audioLoad( "clip.mp4" )` just work once FFmpeg is there. FFmpeg builds are third party programs with their own licenses (see [docs](docs/)); the module never bundles or links them.

### Game audio

| Function | Purpose |
|---|---|
| `audioMixer( sampleRate=44100, channels=2, output="speakers", buses )` | A real time mixer. `output` is `speakers`, `null` (keeps time, no sound, for servers) or `memory` (call `render( seconds )` to get the audio) |
| `mixer.play( sound, { bus, volume, pan, pitch, loop, loopStart, loopEnd, start, fadeIn } )` | Play a sound, get a playback to `stop( fade )`, `pause()`, `resume()`, `setVolume()`, `setPan()`, `setPitch()`, `fadeTo( level, seconds )` |
| `mixer.crossfade( playback, sound, seconds, options )` | Swap music smoothly |
| `mixer.setVolume( bus, volume )` / `mixer.setVolume( volume )` | Bus and master volume |
| `audioPlay( audio, wait=true, volume=1, output="speakers" )` | Play one sound through the speakers, or with `output="null"` where there is no sound card. To get the audio itself use `audioMixer( output="memory" )` and `render()` |
| `audioLoopify( audio, crossfade=0.25 )` | Blend the end into the start so a loop cannot click |
| `audioVariations( audio, count=5, pitchRange=1, gainRangeDb=2, seed=1 )` | Slightly different copies, so repeated effects do not sound alike |
| `audioSprite( sounds, gap=0.1, path )` | Join named sounds into one file with a Howler.js map |

Playing to the speakers needs a sound card and the JVM's `java.desktop` module. The [game audio example](examples/14-game-audio/) shows a scripted session, rendered to a file or played live. MIDI files are not rendered: the JDK's software synthesizer cannot be driven offline from outside the JDK.

### Text to speech

`audioSpeak` turns text into a mono audio buffer with a [Piper](https://github.com/rhasspy/piper) voice, on the JVM and offline once the voices are downloaded. The engine and the voices are not in the module, they are far too big. The first call downloads them, with every file checked against a checksum, into `~/.bxaudio`, shared by every BoxLang install. `bx-audio where` shows what is stored and `bx-audio clean` frees it.

| Function | Purpose |
|---|---|
| `audioSpeak( text, voice, autoInstall, onProgress, speed=1, pauses, pronunciations )` | Speak a text, `speed` 1 is the voice's pace, `pauses` (`{ sentence, clause, paragraph }` in seconds) speaks it piece by piece. `voice` is an id such as `en_US-joe-medium` or a short name such as `joe`. Downloads the engine (about 148 MB, once) and the voice (63 to 114 MB, once) if they are missing, unless `autoInstall` is false |
| `audioSpeakCues( text, voice, autoInstall, onProgress, speed=1, pauses, pronunciations, maxChars=80, split="sentence" )` | Speak piece by piece and get `audio`, `cues` (`start`, `end`, `text`) and `seconds`. Captions land on the words within about 10 ms |
| `audioSubtitles( cues, format="srt", path="", offset=0 )` | SRT or WebVTT text from cues, written to a file if you give a path |
| `audioVoices( language="", installed=false, status="offered", license="", usage="" )` | The voices: id, name, language, quality, license, `usage` (what the license means for a product you sell), `commercial`, `credit`, size, `status` (`installed`, `downloadable`, or `excluded` with `reason`). `status="excluded"` or `"all"` shows the voices that are not offered and why |
| `audioLicenses()` | The licenses of the offered voices and what each asks of you |
| `audioCredits( voices )` | The credits the licenses of some voices ask for, as text for an about page |
| `audioVoiceInstall( voice, onProgress )` | Download a voice ahead of time, or `"engine"` for the speech engine every voice needs |
| `audioVoiceRemove( voice )` | Delete a voice, or `"engine"`, from disk |
| `audioSpeechStatus()` | Where it all lives, whether the engine is installed, and which voices are |
| `audioStorage()` | Where `~/.bxaudio` keeps the engine, voices, FFmpeg and caches and how big each is |
| `audioStorageClear( kind="all" )` | Delete one kind of download (`engine`, `voices`, `ffmpeg`, `cache`) or `all` |

```js
// A voice-over, ducking music underneath it
voice = audioSpeak( text="Meet BoxLang. Fast, friendly and made for the JVM.", voice="joe" );

t = audioTimeline( bpm=100 );
audioAdd( timeline=t, sound=music, at=0, track="music", gainDb=-10 );
audioAdd( timeline=t, sound=voice, at=t.bar( 1 ), track="voice" );
audioDuck( t, "music", "voice", -9 );
audioSave( audioSaturate( audioRender( timeline=t, tail=1 ) ), "promo.mp3" );
```

Only voices whose model card states an open license are on offer, each with its license shown, and you choose which to use. The full list, and the voices that were left out and why, is in [docs/voices.md](docs/voices.md). Only US English voices are available for now.

Three module settings (in `boxlang.json` under `modules.bxaudio.settings`, or in `ModuleConfig`): `home` is where the engine, the voices and FFmpeg are downloaded to, empty for `~/.bxaudio`; `autoInstall` is `true` unless you want the module to refuse to download anything; `ffmpeg` is the path to an FFmpeg you want it to use. The environment variables `BX_AUDIO_HOME` and `BX_AUDIO_FFMPEG` set the same.

### AI agent tools

With the [BoxLang AI module](https://ai.ortusbooks.com) (`bx-ai`) installed, BxAudio registers 26 audio tools in its global tool registry as `audioInfo@bxaudio`, `audioTrim@bxaudio`, `audioMaster@bxaudio` and so on, so an agent can inspect, edit, master and create audio files. Registering only makes them available, an agent uses the ones its `tools` list names:

```js
agent = aiAgent( name : "sound-editor", tools: [ "audioInfo@bxaudio", "audioTrim@bxaudio", "audioNormalize@bxaudio" ] )
agent.run( "Cut the first 5 seconds off intro.wav and normalize it" )
```

The tools work on files, write new files and never overwrite one, and stay inside the folders of the `ai.allowedPaths` setting (the temp folder and the working directory by default). See [AI agent tools](docs/guides/ai-tools.md).

## Examples

The [examples catalog](examples/) holds complete pieces of music made from code, with no samples or licensing concerns. Start with [Pop Drive](examples/01-pop-drive/), a 55 second pop track that was checked against an original numpy script, then [Lofi](examples/02-lofi/), [House](examples/03-house/), [Ambient](examples/04-ambient/), [Techno](examples/05-techno/), [Synthwave](examples/06-synthwave/), [Cinematic](examples/07-cinematic/), [Chiptune](examples/08-chiptune/), five original 8 bit tracks, [Game sound effects](examples/09-sfx/), ten sounds you can export as a pack, and [Jazz](examples/10-jazz/), [Rock](examples/11-rock/), a [Voice-over](examples/12-voiceover/) with music that ducks under a spoken voice, a complete [Trailer](examples/13-trailer/) soundtrack (music cut to the video, voice-over, captions, mastering, mux) [Game audio](examples/14-game-audio/) (a mixer session, loops, variations, a sprite), [Sampler](examples/16-sampler/) and [The BxAudio theme](examples/15-theme/).

```bash
boxlang examples/01-pop-drive/pop-drive.bxs --out=out/pop-drive.wav
```

To listen in a browser, start the [explorer](examples/explorer/) with the MiniServer: `cd examples/explorer && boxlang-miniserver --webroot .`.

## Command Line

Installing the module with `install-bx-module` also installs a `bx-audio` command and its bash tab completion (`bx-audio completions zsh`, `fish` and `powershell` print the scripts for those shells), so you can work with audio from a shell without writing a script. Without the installer, the same commands run as `boxlang module:bxaudio <command>`.

```bash
bx-audio render techno --bars=16 --out=techno.mp3   # synthesize an example piece
bx-audio info techno.mp3                            # format, length, peak and RMS
bx-audio convert song.wav song.mp3 --bitrate=160    # WAV to MP3, with --sampleRate and --channels
bx-audio normalize voice.wav loud.wav --targetDb=-3
bx-audio trim song.wav intro.wav --start=0 --end=12
bx-audio waveform song.wav                          # one line of loudness, add --unicode for blocks
bx-audio voices                                     # the open license voices you can use
bx-audio speak "Hello there" --voice=joe --out=hi.mp3   # installs the engine and voice the first time
bx-audio explorer                                   # the web explorer, on the BoxLang MiniServer
bx-audio doctor                                     # checks MP3, the examples and the MiniServer
```

| Command | Purpose |
|---|---|
| `info <file>` | Format, channels, sample rate, length, peak and RMS of an audio file |
| `convert <in> <out>` | Convert between WAV, MP3, FLAC and (with FFmpeg) OGG, Opus and M4A. Options: `--bitrate`, `--bitDepth`, `--sampleRate`, `--channels` |
| `loudness <file>` | LUFS, loudness range and true peak |
| `master <in> <out>` | Reach a loudness target: `--target=streaming` (or `podcast`, `broadcast`, a number), `--truePeakDb`, `--glue` |
| `stretch <in> <out>` / `pitch <in> <out>` | `--rate` or `--seconds` for the speed, `--semitones` for the pitch |
| `bpm <file>` / `silence <file>` | Tempo (with `--beats`), and the silent stretches or `--trim=OUT` |
| `loopify <in> <out>` / `sprite <out> <files...>` / `play <file>` | Game audio: seamless loops, a sprite with a Howler map, playing to the speakers |
| `ffmpeg [install]` / `probe <file>` / `mux <video> <audio> <out>` | FFmpeg status and download, media information, audio onto a video |
| `where` / `clean [kind] --yes` | Where `~/.bxaudio` keeps its downloads and how big they are, and deleting them |
| `licenses` | The voice licenses and what each asks of you |
| `credits <voice>...` | The credits the licenses of some voices ask for, to put on an about page |
| `completions <shell>` | Print the tab completion script for `bash`, `zsh`, `fish` or `powershell` |
| `version` | The module version |
| `normalize <in> <out>` | Scale the peak to `--targetDb` (default -1) |
| `trim <in> <out>` | Keep the section from `--start` to `--end`, in seconds |
| `waveform <file>` | Draw the loudness on one line, `--width` characters wide |
| `speak <text>` | Say a text with `--voice`, into `--out` (default `speech.wav`). Installs what is missing, unless `--no-install`. `--file` reads the text from a file, `--speed`, `--captions=FILE.srt`, `--by-clause` and the pause options time it for video |
| `voices [voice]` | List the voices you can use, with `--installed`, `--downloadable`, `--excluded` (not offered, and why), `--all`, `--lang` and `--license`. A voice name shows its details |
| `install <voice>` / `remove <voice>` | Download or delete a voice, or `engine` for the speech engine |
| `pieces` | List the example pieces |
| `render <piece>` | Synthesize an example piece. Takes `--out` and the settings of that piece, such as `--bars` and `--bpm` |
| `explorer` | Start the web explorer on `--port` (default 8080) and `--host` (default `127.0.0.1`, this machine only). Needs the BoxLang MiniServer on your PATH |
| `doctor` | Check MP3, FFmpeg, audio output, the examples and the explorer on this machine |

Paths are relative to the folder you run the command in. Run `bx-audio help <command>` for the options of one command.

## Roadmap

1. ~~DSP: gain, pan, fade, normalize, trim, concat, reverse, channels, resample~~ (done)
2. ~~Timeline mixing with a beat grid, ducking, and offline rendering~~ (done)
3. ~~Synthesis: oscillators, envelopes, noise, drums, filters~~ (done), ~~effects: delay, reverb, chorus~~ (done)
4. ~~A catalog of example music scripts~~ done
5. ~~Text-to-speech~~ done (US English, with speed, pauses, captions and subtitles). More languages, multi-speaker voices and a cache of what was spoken to come
6. ~~Analysis: waveform, loudness, tempo and beats, spectrum, silence~~ done
7. ~~Loudness, mastering, compression, EQ, time stretch and pitch shift~~ done
8. ~~Real time playback and mixing for games~~ done (needs a sound card to hear, headless modes for servers)
9. ~~MP3 and FLAC read and write; OGG, Opus, AAC and video through FFmpeg~~ done
10. ~~Encode and decode in memory, an HTTP response with range requests, tags with cover art and chapters, waveform and spectrogram pictures~~ done
11. ~~A sampler, LFOs and curves with modulation and track automation, convolution reverb, gapless MP3~~ done
12. MIDI file rendering, more languages for speech, formant-preserving pitch shift

## Licensing

The module is Apache 2.0. Text to speech downloads the Voices engine (Apache 2 and MIT) and the voices you choose, each with its own license, see [docs/voices.md](docs/voices.md). FLAC is encoded and decoded by code in this module. FFmpeg, when used, is a separate program found on your computer or downloaded from its provider (macOS and Linux builds by martin-riedl.de, Windows builds by gyan.dev), started as a process and never bundled; its builds carry their own licenses, see https://ffmpeg.org/legal.html. MP3 uses JLayer and jump3r (both LGPL), shipped as separate, replaceable jars in the module's `libs/` folder and never bundled into the module jar. Without them WAV still works and MP3 calls raise a clear error. MP3 encoding needs the `java.desktop` module in the JVM. Details and license texts: `licenses/THIRD-PARTY.md`.

## Documentation

The documentation site is at **[bxaudio.boxlang.io](https://bxaudio.boxlang.io)**, built from [`docs/`](docs/) with [BX Sites](https://bxsites.io): guides for video, speech, mastering and games, the reference of every function and command, every voice with its license, and a player for each example. The function and command reference, the voices page and the examples page are generated from the code, see [docs/contribute.md](docs/contribute.md).

## Development

```bash
./gradlew downloadBoxLang   # once, fetches the BoxLang jar used for compiling and tests
./gradlew spotlessApply test
```

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com). Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more. If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)

### THE DAILY BREAD

 > "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
