---
title: Timeline mixing
order: 3
summary: Arrange sounds on a tempo grid and tracks, pump a bass to the kick, and duck music under a voice.
---

A **timeline** holds named tracks at one sample rate and a tempo grid. You place sounds on tracks at times, and ducking and track volumes are applied when it is rendered, so the stored tracks are never altered and the timeline can be changed and rendered again.

<!-- run -->
```js
t = audioTimeline( bpm=120, beatsPerBar=4, sampleRate=44100, channels=2 );
kick = audioDrum( "kick" );
hat  = audioDrum( type="hat", seed=2, level=0.5 );
pad  = audioGain( audioFilter( audioOsc( "saw", audioFreq( "A2" ), 4 ), "lowpass", 900, 0.8 ), -12 );

audioAdd( timeline=t, sound=pad, at=0, track="music" );
for( at in audioBeats( t, 0, 16 ) ){
    audioAdd( timeline=t, sound=kick, at=at, track="drums", gainDb=-4 );
}
for( at in audioBeats( t, 0.5, 16, 1 ) ){
    audioAdd( timeline=t, sound=hat, at=at, track="drums", gainDb=-12, pan=0.3 );
}

audioSave( audioRender( timeline=t, tail=1 ), "beat.wav" );
```

`t.beat( n )` and `t.bar( n )` turn beats and bars into seconds. `audioBeats( t, start, end, step )` lists moments on the grid, such as every beat, or every half beat with `step=0.5`. Sounds with another sample rate are converted for you, and mono sounds are panned into the stereo field with `pan`.

## Make the bass pump

`audioPump` dips a track at given moments with a quick recovery, like a sidechain compressor keyed by the kick.

<!-- run -->
```js
t = audioTimeline( bpm=124 );
kick = audioDrum( "kick" );
bass = audioGain( audioFilter( audioOsc( "saw", audioFreq( "A1" ), 8 ), "lowpass", 500, 1.2 ), -8 );
audioAdd( timeline=t, sound=bass, at=0, track="bass" );
for( at in audioBeats( t, 0, 16 ) ){
    audioAdd( timeline=t, sound=kick, at=at, track="drums" );
}
audioPump( t, "bass", audioBeats( t, 0, 16 ), -6, 0.18 );    // down 6 dB on each kick, back in 0.18 s
mix = audioRender( t );
```

## Duck music under a voice

`audioDuck` lowers a track while another is loud, which is the mix you would otherwise do by hand when a voice-over comes in.

```js
music = audioOsc( "saw", audioFreq( "A2" ), 10 );
voice = audioSpeak( text="Welcome.", voice="joe" );
t = audioTimeline( bpm=100 );
audioAdd( timeline=t, sound=music, at=0, track="music", gainDb=-10 );
audioAdd( timeline=t, sound=voice, at=t.bar( 1 ), track="voice" );
audioDuck( t, "music", "voice", -9, -40, 10, 300 );   // down 9 dB while the voice is above -40 dB; 10 ms to duck, 300 ms to come back
mix = audioRender( timeline=t, tail=1 );
```

The [voice-over example](../examples/index.md#voice-over) and the [trailer guide](trailers-and-video.md) use exactly this.

## Track volume

`audioTrackGain( t, "music", -6 )` sets the volume of a whole track. Use it to keep the music below the voice even when nobody speaks, and let `audioDuck` take it further while someone does.

## Finish the mix

Render, fade, and finish with either `audioSaturate` (a warm tanh limiter that also normalizes the peak) or [`audioMaster`](loudness-and-mastering.md), which delivers a loudness target.

```js
mix = audioFade( audioRender( timeline=t, tail=2 ), 0.05, 1.5, "smooth" );
audioSave( audioMaster( audio=mix, target="streaming" ), "final.wav" );
```
