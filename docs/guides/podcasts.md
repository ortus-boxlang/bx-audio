---
title: Podcasts
order: 13
summary: Clean up a recording, level it to the podcast standard, add music that ducks under the voice, mark chapters and publish an MP3.
---

A podcast episode is a voice recording, a little music, and a file that sounds as loud as everyone else's. The steps below are the whole job, in the order to do them.

The recording here is made up so the examples run anywhere: bursts of shaped noise that stand in for a voice. Replace `recording` with `audioLoad( "episode.wav" )`.

<!-- run -->
```js
// Stand-in for a recorded voice: three phrases with a long pause between and a room tone under all of it
function phrase( seconds ){
    return audioEnvelope( audio=audioGain( audioNoise( seconds=seconds, color="pink", seed=3 ), -12 ), attack=0.05, decay=seconds );
}
roomTone = audioGain( audioNoise( seconds=14, color="brown", seed=1 ), -48 );
voice = audioConcat( [ audioSilence( seconds=1.5, channels=1 ), phrase( 3 ), audioSilence( seconds=1.2, channels=1 ), phrase( 4 ), audioSilence( seconds=2, channels=1 ), phrase( 2.5 ) ] );
recording = audioMix( [ voice, audioTrim( audio=roomTone, start=0, end=voice.getDurationSeconds() ) ] );
println( "recorded " & numberFormat( recording.getDurationSeconds(), "0.0" ) & " s" );
```

## 1. Clean

Cut the silence at both ends, remove the low rumble below the voice, and even out the level so quiet words are not lost and loud ones do not jump out.

<!-- run -->
```js
recording = audioGain( audioNoise( seconds=6, color="pink", seed=3 ), -20 );
cleaned = audioTrimSilence( audio=recording, thresholdDb=-50, pad=0.25 );          // keep a quarter second of air at each end
cleaned = audioFilter( cleaned, "highpass", 80, 0.7071 );                         // below 80 Hz is rumble, not voice
cleaned = audioCompress( audio=cleaned, thresholdDb=-22, ratio=3, attackMs=8, releaseMs=120 );   // gentle: 3 to 1
cleaned = audioEq( cleaned, [ { type: "peak", freq: 3000, gainDb: 2, q: 1 } ] );   // a little presence
println( "cleaned: " & numberFormat( cleaned.getDurationSeconds(), "0.00" ) & " s" );
```

`audioDetectSilence` lists the quiet stretches if you want to cut the long pauses out as well: it returns `{ start, end, duration }` for each one.

## 2. Level to the standard

Podcast apps and directories expect about **-16 LUFS** for stereo and **-19 LUFS** for a mono show, with no peak above **-1 dBFS**. `audioMaster` does it in one call, and `audioLoudness` proves it.

<!-- run -->
```js
episode = audioGain( audioNoise( seconds=10, color="pink", seed=5 ), -30 );
final = audioMaster( audio=episode, target="podcast", truePeakDb=-1 );
r = audioLoudness( final );
println( numberFormat( r.integrated, "0.0" ) & " LUFS, true peak " & numberFormat( r.truePeakDb, "0.0" ) & " dBFS" );
```

Use `target=-19` for a mono show. Master the finished episode once, at the end, after the music is in.

## 3. Music under the voice

Put the voice and the music on their own tracks, and let `audioDuck` lower the music while the voice speaks. The music comes back up by itself in the pauses.

<!-- run -->
```js
voice  = audioConcat( [ audioSilence( seconds=2, channels=1 ), audioGain( audioNoise( seconds=4, color="pink", seed=3 ), -14 ), audioSilence( seconds=2, channels=1 ) ] );
bed    = audioFilter( audioGain( audioOsc( shape="saw", freq=110, seconds=8 ), -22 ), "lowpass", 1200, 0.7071 );

t = audioTimeline( bpm=100, beatsPerBar=4, sampleRate=44100, channels=2 );
audioAdd( timeline=t, sound=bed, at=0, track="music", gainDb=-4 );
audioAdd( timeline=t, sound=voice, at=0, track="voice" );
audioDuck( timeline=t, track="music", trigger="voice", amountDb=-14, attackMs=30, releaseMs=600 );
mix = audioRender( timeline=t );
println( "mixed " & numberFormat( mix.getDurationSeconds(), "0.0" ) & " s" );
```

## 4. Chapters and show notes

`audioMarkers` turns a list of times into the formats players and editors read: a YouTube description (`youtube`), `ffmetadata` for FFmpeg, `srt` and `vtt`, `edl`, `csv` and `json`.

<!-- run -->
```js
text = audioMarkers(
    markers=[
        { time: 0, label: "Intro" },
        { time: 95, label: "The news" },
        { time: 610, label: "Interview" },
        { time: 2480, label: "Wrap up" }
    ],
    format="youtube",
    total=2700
);
println( text );
```

## 5. Tags, cover art and chapters inside the file

Players show the title and the cover, and podcast apps let listeners jump between chapters, when the file carries them. `audioTags` writes all of it into the MP3. The same list of markers you gave `audioMarkers` works as the chapters.

<!-- run -->
```js
audioSave( audio=audioNoise( seconds=6, color="pink", seed=3 ), path="episode.mp3", bitrate=96 );

audioTags(
    path="episode.mp3",
    tags={
        title: "Episode 42: The pilot",
        artist: "The Show",
        album: "Season 1",
        year: 2026,
        track: "42",
        genre: "Podcast",
        chapters: [
            { start: 0, title: "Intro" },
            { start: 2.5, title: "The news" },
            { start: 4.5, title: "Wrap up" }
        ]
    }
);

episode = audioTags( path="episode.mp3" );
println( episode.title & ", " & episode.chapters.len() & " chapters, the second starts at " & episode.chapters[ 2 ].start & " s" );
```

Add `cover: "cover.png"` for the picture (PNG, JPEG, GIF, WebP or BMP, up to 16 MB, square and at least 1400 pixels wide for the podcast directories). Tags you leave out are kept, an empty string removes a tag, and `chapters: []` removes the chapters. The audio is not touched. See [Return values](../reference/return-values.md#audiotags-path) for the shape of what you read back.

MP3 gets ID3v2.4 tags with chapter frames, FLAC gets Vorbis comments and picture blocks, and WAV gets an INFO list and cue points (a WAV file has no place for a cover, the album artist or the disc, and `audioTags` tells you which tags it ignored).

## 6. Save the MP3

Speech does not need stereo or a high bit rate. Mono at 96 or 128 kbps is a small file that sounds the same.

```js
final = audioChannels( final, 1 );
audioSave( audio=final, path="episode-042.mp3", bitrate=96 );
```

MP3 needs no FFmpeg. If you also publish OGG or Opus, FFmpeg writes them, see [Formats and FFmpeg](formats-and-ffmpeg.md).

## A transcript that matches the audio

A show read by a voice, such as a news summary, can make its own captions. `audioSpeakCues` speaks the text and gives back the time of every sentence, `audioSubtitles` turns them into a file. See [Speech and subtitles](speech-and-subtitles.md).

```js
spoken = audioSpeakCues( text=script, voice="kristin", speed=0.96, pauses={ sentence: 0.5, clause: 0.2 } );
audioSave( audio=spoken.audio, path="summary.mp3", bitrate=96 );
audioSubtitles( cues=spoken.cues, format="vtt", path="summary.vtt" );
```
