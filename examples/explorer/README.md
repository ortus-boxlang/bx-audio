# Catalog explorer

A small web app, written in BoxLang templates, that lets you browse the examples catalog, change a piece's settings and play it in the browser. It runs on the BoxLang MiniServer and is a showcase for the module: every sound you hear is synthesized on the server when you press play.

![Explorer](explorer.png)

## Run it

Install the module and the MiniServer, then start the server from this folder:

```bash
install-bx-module bx-audio
cd examples/explorer
boxlang-miniserver --webroot . --port 8080
```

Open <http://localhost:8080/index.bxm>. With `miniserver.json` in the folder you can also run `boxlang-miniserver` with no arguments.

## What it does

- Lists every piece in `catalog.json`.
- Shows the settings each piece accepts (bars, tempo, style, swing, seed) as sliders.
- **Render and play** synthesizes the piece, draws its waveform and plays it. Click the waveform to seek, press space to pause, use the arrow keys for 5 second jumps.
- The same settings play instantly the second time, because results are cached.
- **Download WAV** saves what you are hearing.

## Branding

The page follows the BxAudio brand: the teal `#00EFD0` to cyan `#00D4EF` to blue `#0074FF` gradient of the logo on the waveform, the play button and the selected piece, on deep ink `#001019` in dark mode. It follows the browser's light or dark setting and swaps between the light and dark logos.

## How it works

| File | Role |
|---|---|
| `catalog.json` | The list of pieces: id, title, script and the settings with their ranges. The single source of truth for the page and the server |
| `index.bxm` | The page. Reads the catalog on the server and hands it to the browser |
| `render.bxm` | The JSON endpoint behind the button. Renders a piece and answers with the audio URL, its length and the waveform from `audioWaveform()` |
| `Renderer.bx` | Runs one example script in its own variables scope, so the script's variables cannot overwrite the request's |
| `assets/` | The BxAudio logos (full light, full dark and icon) |
| `cache/` | Rendered audio and metadata, created at runtime and not committed. Keeps the newest 40 renders |

`render.bxm` is deliberately strict because it turns a URL into running code. A piece must be listed in the catalog, only the settings listed for it are read, numbers are clamped to the listed range, `select` settings accept only listed options, and the script path comes from the catalog and never from the request.

## Add a piece

1. Add the script to `examples/`, reading its settings the way the others do (`overrides`, then command line).
2. Add an entry to `catalog.json` with the settings you want as controls. The defaults must match the script's own.
3. Run `./gradlew test`. `ExplorerCatalogTest` checks that the script exists, that each setting is a documented option of the script with the same default, and that the piece renders with the smallest value of every setting.
