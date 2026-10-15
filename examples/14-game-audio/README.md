# Game audio

What a game asks of a sound engine, in one script: sound effects, varied so repeats do not sound alike, music loops that repeat without a click, a boss theme made from the same loop, and a mixer that plays everything on volume buses. Then the assets are written the way a game loads them.

```bash
boxlang game.bxs                          # renders a scripted play session to out/game-session.wav
boxlang game.bxs --play                   # the same session, live through your speakers
boxlang game.bxs --folder=assets/audio    # where the sprite and the loops go
```

## The session

| Time | Event |
|---|---|
| 0.0 | The music loop starts, fading in on the `music` bus |
| 1.5 | The player jumps (variation 1 of 5) |
| 2.2, 2.45 | Two coins |
| 3.3 | Another jump |
| 4.0 | Three laser shots, panned from left to right |
| 5.5 | The boss arrives: the music crossfades to the boss theme over 1.5 s |
| 7.5 | An explosion, and the music bus ducks to 25 percent for a second |
| 9.5 | The music fades out |

## How it is built

1. **Effects.** Each is a few lines of synthesis, a pitch glide or a noise burst, as in the [sound effects example](../09-sfx/README.md).
2. **Variations.** `audioVariations` makes copies with a random pitch and level, repeatable with a seed. A game picks one at random each time.
3. **The loop.** Four bars of bass and arpeggio, then `audioLoopify` blends the end into the start so the join cannot click.
4. **The boss theme.** `audioStretch` speeds the loop up by 15 percent without changing its pitch, and `audioPitch` lowers it a major third without changing its speed.
5. **The mixer.** `audioMixer` has a `music` bus at 60 percent and an `sfx` bus. `play` takes the volume, pan, pitch, loop and bus of each sound, and `crossfade` swaps the music. In `memory` mode, as here by default, `render` produces the audio; with `--play` the sound card is the output and the script waits in real time.
6. **The assets.** `audioSprite` joins the effects into one file with a [Howler.js](https://howlerjs.com/) map, which is how browser games load effects.

## What it shows

| Feature | Where |
|---|---|
| A mixer with buses, volume, pan, pitch and loops | `audioMixer`, `mixer.play` |
| Crossfading music | `mixer.crossfade` |
| Seamless loops | `audioLoopify` |
| Effects that vary | `audioVariations` |
| Time stretch and pitch shift | `bossLoop` |
| An audio sprite with a map | `audioSprite` |
| Real time playback | `--play` |
