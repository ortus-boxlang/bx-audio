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
* DSP BIFs `audioGain`, `audioPan`, `audioFade`, `audioNormalize`, `audioTrim`, `audioConcat`, `audioReverse`, `audioChannels` and `audioResample`
