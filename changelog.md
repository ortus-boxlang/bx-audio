# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

----

## [Unreleased]

### Added

* Module scaffold from the BoxLang module template
* `AudioBuffer` type and a dependency free WAV codec (read PCM 8/16/24/32 and float 32/64, write 16/24 bit PCM and 32 bit float)
* BIFs `audioSilence`, `audioLoad`, `audioSave` and `audioInfo`
* Synthesis: `audioFreq`, `audioOsc` (sine, saw, square, triangle, pulse, additive), `audioSweep`, `audioNoise` (white, pink, brown, blue), `audioEnvelope`, `audioDrum` (kick, snare, hat, openhat, clap, tom, crash, riser) and `audioFilter` (lowpass, highpass, bandpass, notch)
* Timeline mixer: `audioTimeline`, `audioAdd`, `audioBeats`, `audioTrackGain`, `audioPump`, `audioDuck` and `audioRender`, with a tempo grid, automatic sample rate and channel conversion, kick-style pump ducking and voice-over sidechain ducking
* `audioSaturate` for peak-normalize plus tanh limiting
* DSP BIFs `audioGain`, `audioPan`, `audioFade`, `audioNormalize`, `audioTrim`, `audioConcat`, `audioReverse`, `audioChannels` and `audioResample`
