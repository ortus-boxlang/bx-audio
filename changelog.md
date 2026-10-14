# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

----

## [Unreleased]

### Added

* MP3 read and write through `audioLoad` and `audioSave` (new `bitrate` argument). Uses LGPL JLayer and jump3r as separate optional jars in `libs/`, with license texts in `licenses/`; files are detected by content, not extension
* Module scaffold from the BoxLang module template
* `AudioBuffer` type and a dependency free WAV codec (read PCM 8/16/24/32 and float 32/64, write 16/24 bit PCM and 32 bit float)
* BIFs `audioSilence`, `audioLoad`, `audioSave` and `audioInfo`
* Effects: `audioDelay` (with feedback, ping pong and an automatic tail), `audioReverb` (Freeverb design) and `audioChorus`
* `audioMix` to sum buffers
* Examples: `02-lofi`, `03-house` and `04-ambient`, original compositions checked by measurement (chord tones, brightness, beat pulse, transient strength, a silent ending) and covered by tests
* Examples catalog started with `01-pop-drive`, a BoxLang port of a numpy script checked against the original (same length and peak, octave bands within 0.5 dB, kick and bass envelope correlation 1.000), covered by a test
* Build: `shadowJar` now depends on `serviceLoaderBuild`, and `build/module` is cleared before it is assembled, so a jar built on its own still registers its BIFs and no removed file lingers
* Synthesis: `audioFreq`, `audioOsc` (sine, saw, square, triangle, pulse, additive), `audioSweep`, `audioNoise` (white, pink, brown, blue), `audioEnvelope`, `audioDrum` (kick, snare, hat, openhat, clap, tom, crash, riser) and `audioFilter` (lowpass, highpass, bandpass, notch)
* Timeline mixer: `audioTimeline`, `audioAdd`, `audioBeats`, `audioTrackGain`, `audioPump`, `audioDuck` and `audioRender`, with a tempo grid, automatic sample rate and channel conversion, kick-style pump ducking and voice-over sidechain ducking
* `audioSaturate` for peak-normalize plus tanh limiting
* DSP BIFs `audioGain`, `audioPan`, `audioFade`, `audioNormalize`, `audioTrim`, `audioConcat`, `audioReverse`, `audioChannels` and `audioResample`
