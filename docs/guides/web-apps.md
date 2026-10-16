---
title: Web apps
order: 15
summary: Serve audio from an endpoint so browsers can play and seek it, take uploads, and keep the work cheap with ETag and caching.
---

A browser plays audio by asking for it in pieces: it sends a `Range` header to seek, and an `If-None-Match` header to ask if its copy is still good. `audioHttpResponse` works out what to send for those requests. It does not send anything itself, so it works on any server that runs BoxLang: you copy its status, headers and body to the response.

## Serve a file

<!-- run -->
```js
audioSave( audio=audioOsc( shape="sine", freq=440, seconds=3 ), path="tone.wav" );

// What the browser asks when it starts playing: the whole file
first = audioHttpResponse( source="tone.wav" );
println( first.status & " " & first.headers[ "Content-Type" ] & ", " & first.headers[ "Content-Length" ] & " bytes" );

// What it asks when you drag the slider: a range
seek = audioHttpResponse( source="tone.wav", request={ headers: { range: "bytes=1000-1999" } } );
println( seek.status & " " & seek.headers[ "Content-Range" ] );

// What it asks the next time, with the ETag it was given
again = audioHttpResponse( source="tone.wav", request={ headers: { "if-none-match": first.headers.ETag } } );
println( again.status & ", " & again.body.len() & " bytes sent" );
```

A file on disk is sent as it is, and only the bytes of the range are read from disk, so seeking in a long recording costs a few kilobytes and not the file.

## Send it from a BoxLang page

Pass the request from the web server in, copy the answer out. `getHTTPRequestData()` gives the method and the headers, `bx:header` sets a header and the status, and `bx:content` sends the bytes.

```js
// audio.bxm, or a handler of your framework
request = getHTTPRequestData();
response = audioHttpResponse(
    source = expandPath( "/media/episode-42.mp3" ),
    request = request
);

bx:header statusCode=response.status statusText=response.statusText;
for( name in response.headers ){
    bx:header name=name value=response.headers[ name ];
}
bx:content type=response.headers[ "Content-Type" ] ?: "audio/mpeg" variable=response.body reset=true;
```

## Send audio you made

A buffer is encoded to the format you pick. Encoding costs time on every request, so for a sound that many people ask for, encode once and keep the bytes. The ETag is a hash of the bytes, so it stays the same while the sound does.

<!-- run -->
```js
sound = audioFade( audio=audioOsc( shape="sine", freq=330, seconds=2 ), fadeIn=0.01, fadeOut=0.3 );

// Once, when the sound is made
bytes = audioEncode( audio=sound, format="mp3", bitrate=96 );

// On every request
response = audioHttpResponse( source=bytes, request={ headers: { range: "bytes=0-" } } );
println( response.status & " " & response.headers[ "Content-Type" ] & " " & response.headers.ETag );
```

Give `format` to convert a file on the way out, for example `audioHttpResponse( source="recording.wav", format="mp3" )`. Without it, a path is served untouched.

## Options

| Argument | What it does |
|---|---|
| `maxAgeSeconds` | How long a browser may keep the audio without asking. `3600` by default, `0` asks every time (cheap, because the ETag answers `304`), a negative number forbids storing it. |
| `filename` | A name offered when the browser saves the file. |
| `download` | `true` makes the browser save the file instead of playing it. |
| `format`, `bitrate`, `bitDepth` | How a buffer is encoded, or a file converted. |

A request that is not GET or HEAD gets `405`, and a range past the end of the file gets `416`. See [Return values](../reference/return-values.md#audiohttpresponse-source-request) for every key of the answer.

## Take an upload

`audioDecode` reads audio from bytes, so an upload never has to touch the disk. The format is found from the content, and a file that is not audio is `bxaudio.unsupportedFormat`.

<!-- run -->
```js
upload = audioEncode( audio=audioOsc( shape="saw", freq=220, seconds=1 ), format="flac" );   // in a page: the uploaded file's bytes

try {
    clip = audioDecode( data=upload );
    println( "got " & numberFormat( clip.getDurationSeconds(), "0.0" ) & " s of audio at " & clip.getSampleRate() & " Hz" );
} catch( "bxaudio.unsupportedFormat" e ) {
    println( "That is not an audio file" );
}
```

Do not trust the file name or the content type the browser sends. Decode first, then check what you need: `getDurationSeconds()` for a length limit, `audioLoudness()` for a level check. Limit the size of the request in your web server as well, since a decoded buffer takes about 350 KB per second of stereo audio in memory.

## Formats for the web

| Format | Plays in | Needs |
|---|---|---|
| MP3 | every browser | nothing |
| WAV | every browser | nothing, large |
| FLAC | current browsers | nothing |
| OGG, Opus | most browsers | FFmpeg |
| M4A, AAC | every browser | FFmpeg |

For a site, MP3 at 96 to 128 kbps is the safe choice. See [Formats and FFmpeg](formats-and-ffmpeg.md).
