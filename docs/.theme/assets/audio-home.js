/*
 * BxAudio homepage behaviour. Nothing here plays sound by itself: every sound starts from a button the visitor presses.
 * Pieces: the shared player (theme, clips, voices), the hero visuals, the in-browser playground (Web Audio previews of four building
 * blocks, next to the BoxLang code that makes the real thing), a playable piano, and scroll reveals.
 */
( function () {
	"use strict";
	document.documentElement.classList.add( "ax-js" );
	var reduce = window.matchMedia && window.matchMedia( "(prefers-reduced-motion: reduce)" ).matches;
	var $ = function ( s, r ) { return ( r || document ).querySelector( s ); };
	var $$ = function ( s, r ) { return Array.prototype.slice.call( ( r || document ).querySelectorAll( s ) ); };
	var icon = function ( n ) { return '<i class="ph-duotone ph-' + n + '"></i>'; };
	var PLAY = icon( "play" ), PAUSE = icon( "pause" );

	/* ------------------------------------------------------------------ shared player */
	var audio = $( "#ax-audio" );
	var current = null;
	var ctx = null, analyser = null, freq = null, live = false, timer = null;
	var bars = [];

	function setupAnalyser() {
		if ( ctx || !audio ) { return; }
		try {
			ctx = new ( window.AudioContext || window.webkitAudioContext )();
			var src = ctx.createMediaElementSource( audio );
			analyser = ctx.createAnalyser();
			analyser.fftSize = 128;
			freq = new Uint8Array( analyser.frequencyBinCount );
			src.connect( analyser );
			analyser.connect( ctx.destination );
			live = true;
		} catch ( e ) { live = false; }
	}
	function simulate() {
		bars.forEach( function ( b, i ) { b.style.height = ( 18 + Math.abs( Math.sin( Date.now() / 260 + i * .5 ) ) * Math.random() * 80 ) + "%"; } );
	}
	function draw() {
		if ( live && analyser ) {
			analyser.getByteFrequencyData( freq );
			var sum = 0;
			bars.forEach( function ( b, i ) {
				var v = freq[ Math.min( freq.length - 1, Math.floor( i * freq.length / bars.length * .8 ) ) ] / 255;
				b.style.height = ( 8 + v * 92 ) + "%";
				sum += v;
			} );
			if ( sum < .05 ) { simulate(); }
		} else { simulate(); }
	}
	function animate( on ) {
		var p = $( "#ax-hero-player" );
		if ( p ) { p.classList.toggle( "playing", on ); }
		clearInterval( timer );
		if ( on && !reduce ) { timer = setInterval( draw, 60 ); }
		else { bars.forEach( function ( b, i ) { b.style.height = ( 20 + Math.abs( Math.sin( i * .4 ) ) * 60 ) + "%"; } ); }
	}
	function reset() {
		$$( ".ax-clip.on" ).forEach( function ( e ) { e.classList.remove( "on" ); } );
		$$( ".ax-clip .ic, .ax-vplay, #ax-hero-btn" ).forEach( function ( e ) { e.innerHTML = PLAY; } );
		animate( false );
	}
	function toggle( src, btn ) {
		var same = current === btn && audio && !audio.paused;
		reset();
		if ( same ) { audio.pause(); return; }
		current = btn;
		if ( btn.id === "ax-hero-btn" ) { setupAnalyser(); }
		if ( ctx && ctx.state === "suspended" ) { ctx.resume(); }
		audio.src = src;
		audio.play().catch( function () {} );
		if ( btn.classList.contains( "ax-clip" ) ) { btn.classList.add( "on" ); btn.querySelector( ".ic" ).innerHTML = PAUSE; }
		else { btn.innerHTML = PAUSE; }
		if ( btn.id === "ax-hero-btn" ) { animate( true ); }
	}
	if ( audio ) { audio.addEventListener( "ended", reset ); }

	/* hero waveform bars */
	var wave = $( "#ax-wave" );
	if ( wave ) {
		for ( var i = 0; i < 56; i++ ) { wave.appendChild( document.createElement( "i" ) ); }
		bars = $$( "i", wave );
		animate( false );
	}

	/* the listen strip */
	var clipBox = $( "#ax-clips" );
	if ( clipBox ) {
		var base = clipBox.getAttribute( "data-base" );
		[ [ "The BxAudio theme", "theme", "waveform", "grid anthem" ],
			[ "Pop Drive", "pop-drive", "headphones" ], [ "Lofi", "lofi", "coffee" ], [ "House", "house", "vinyl-record" ], [ "Ambient", "ambient", "cloud" ],
			[ "Techno", "techno", "lightning" ], [ "Synthwave", "synthwave", "sun-horizon" ], [ "Cinematic", "cinematic", "film-slate" ],
			[ "Chiptune", "chiptune", "game-controller" ], [ "Game SFX", "sfx", "sparkle" ], [ "Jazz", "jazz", "music-notes" ], [ "Rock", "rock", "guitar" ],
			[ "Voice-over", "voiceover", "microphone" ], [ "Trailer", "trailer", "film-strip" ], [ "Game audio", "game-audio", "joystick" ] ].forEach( function ( c, n ) {
			var b = document.createElement( "button" );
			b.type = "button";
			b.className = "ax-clip ax-reveal" + ( n === 0 ? " theme" : "" );
			b.style.setProperty( "--d", ( n * .04 ) + "s" );
			b.setAttribute( "data-src", base + c[ 1 ] + ".mp3" );
			b.innerHTML = '<span class="ic">' + PLAY + '</span><span><b>' + c[ 0 ] + '</b><small>' + ( c[ 3 ] || "made from code" ) + '</small></span><span class="bars"><i></i><i></i><i></i><i></i></span>';
			clipBox.appendChild( b );
		} );
	}
	document.addEventListener( "click", function ( e ) {
		var c = e.target.closest( ".ax-clip" ); if ( c ) { toggle( c.getAttribute( "data-src" ), c ); return; }
		var v = e.target.closest( ".ax-vplay" ); if ( v ) { toggle( v.getAttribute( "data-src" ), v ); return; }
		var h = e.target.closest( "#ax-hero-btn" ); if ( h ) { toggle( h.getAttribute( "data-src" ), h ); }
	} );

	/* copy buttons */
	function copy( text, btn, label ) {
		if ( navigator.clipboard ) { navigator.clipboard.writeText( text ); }
		var old = btn.innerHTML;
		btn.textContent = "Copied";
		setTimeout( function () { btn.innerHTML = old; }, 1500 );
	}
	var cp = $( "#ax-copy" );
	if ( cp ) { cp.addEventListener( "click", function () { copy( "install-bx-module bx-audio", cp ); } ); }

	/* ------------------------------------------------------------------ hero visuals */
	var notes = $( "#ax-notes" );
	if ( notes && !reduce ) {
		[ "music-note", "music-notes", "music-notes-simple", "music-note-simple", "waveform", "vinyl-record" ].forEach( function ( n ) {
			for ( var r = 0; r < 3; r++ ) {
				var el = document.createElement( "i" );
				el.className = "ph-duotone ph-" + n;
				el.style.cssText = "--x:" + ( Math.random() * 96 ) + "%;--s:" + ( 18 + Math.random() * 26 ) + "px;--t:" + ( 14 + Math.random() * 10 ) + "s;--w:-" + ( Math.random() * 20 ) + "s;--dx:" + ( ( Math.random() - .5 ) * 160 ) + "px";
				notes.appendChild( el );
			}
		} );
	}
	var swap = $( "#ax-swap" );
	if ( swap && !reduce ) {
		var words = [ "kick", "bass line", "melody", "voice-over", "trailer", "game loop" ], wi = 0;
		setInterval( function () {
			swap.classList.add( "out" );
			setTimeout( function () { wi = ( wi + 1 ) % words.length; swap.textContent = words[ wi ]; swap.classList.remove( "out" ); }, 350 );
		}, 2400 );
	}
	var seq = $( "#ax-seq" );
	if ( seq ) {
		var pat = { kick: [ 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0 ], snare: [ 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1 ], hat: [ 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1 ] };
		var rows = Object.keys( pat ).map( function ( k ) {
			return pat[ k ].map( function ( v ) { var b = document.createElement( "b" ); b.className = v ? "on" : "off"; seq.appendChild( b ); return b; } );
		} );
		if ( !reduce ) {
			var step = 0;
			setInterval( function () {
				rows.forEach( function ( r ) { r.forEach( function ( b ) { b.classList.remove( "hit" ); } ); r[ step ].classList.add( "hit" ); } );
				step = ( step + 1 ) % 16;
			}, 60000 / 132 / 4 );
		}
	}
	var track = $( "#ax-track" );
	if ( track ) {
		var tags = [ [ "waveform", "Oscillators" ], [ "vinyl-record", "Drums" ], [ "sliders-horizontal", "Filters and EQ" ], [ "microphone-stage", "Speech and captions" ], [ "gauge", "EBU R128 loudness" ],
			[ "film-strip", "FFmpeg mux" ], [ "game-controller", "Real time mixer" ], [ "metronome", "Time stretch" ], [ "chart-bar", "Spectrum and tempo" ], [ "file-audio", "WAV, FLAC, MP3, OGG" ], [ "terminal-window", "bx-audio CLI" ] ];
		var h = ""; for ( var r2 = 0; r2 < 2; r2++ ) { tags.forEach( function ( t ) { h += "<span>" + icon( t[ 0 ] ) + t[ 1 ] + "</span>"; } ); }
		track.innerHTML = h;
	}
	var stage = $( "#ax-stage" );
	if ( stage && !reduce ) {
		stage.addEventListener( "pointermove", function ( e ) {
			var r = stage.getBoundingClientRect(), x = ( e.clientX - r.left ) / r.width - .5, y = ( e.clientY - r.top ) / r.height - .5;
			stage.style.transform = "perspective(1100px) rotateY(" + ( x * 5 ) + "deg) rotateX(" + ( -y * 5 ) + "deg)";
		} );
		stage.addEventListener( "pointerleave", function () { stage.style.transform = ""; } );
	}
	document.addEventListener( "pointermove", function ( e ) {
		var t = e.target.closest && e.target.closest( ".ax-card,.ax-eco a,.ax-clip" );
		if ( t ) { var r = t.getBoundingClientRect(); t.style.setProperty( "--mx", ( e.clientX - r.left ) + "px" ); t.style.setProperty( "--my", ( e.clientY - r.top ) + "px" ); }
	} );

	/* scroll reveal (the clips are added above, so observe after) */
	if ( "IntersectionObserver" in window ) {
		var io = new IntersectionObserver( function ( es ) { es.forEach( function ( e ) { if ( e.isIntersecting ) { e.target.classList.add( "in" ); io.unobserve( e.target ); } } ); }, { threshold: .12 } );
		$$( ".ax-reveal" ).forEach( function ( el ) { io.observe( el ); } );
	} else { $$( ".ax-reveal" ).forEach( function ( el ) { el.classList.add( "in" ); } ); }

	/* ------------------------------------------------------------------ Web Audio helpers for the piano and the playground */
	var wa = null;
	function ac() {
		if ( !wa ) { wa = new ( window.AudioContext || window.webkitAudioContext )(); }
		if ( wa.state === "suspended" ) { wa.resume(); }
		return wa;
	}
	var midi = function ( n ) { return 440 * Math.pow( 2, ( n - 69 ) / 12 ); };
	var NOTE = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 };
	function reverbBus( c ) {
		// A short, bright room made of decaying noise, shared by the playground sounds
		var len = c.sampleRate * 1.6, buf = c.createBuffer( 2, len, c.sampleRate );
		for ( var ch = 0; ch < 2; ch++ ) { var d = buf.getChannelData( ch ); for ( var i = 0; i < len; i++ ) { d[ i ] = ( Math.random() * 2 - 1 ) * Math.pow( 1 - i / len, 2.6 ); } }
		var conv = c.createConvolver(); conv.buffer = buf;
		var wet = c.createGain(); wet.gain.value = .35;
		conv.connect( wet ); wet.connect( c.destination );
		return conv;
	}
	function tone( c, dest, o ) {
		var osc = c.createOscillator(), g = c.createGain(), f = c.createBiquadFilter();
		osc.type = o.type || "sawtooth"; osc.frequency.value = o.freq;
		f.type = "lowpass"; f.frequency.value = o.cutoff || 4000; f.Q.value = o.q || 1;
		var t = o.at || c.currentTime;
		g.gain.setValueAtTime( .0001, t );
		g.gain.exponentialRampToValueAtTime( o.gain || .2, t + ( o.attack || .004 ) );
		g.gain.exponentialRampToValueAtTime( .0001, t + ( o.attack || .004 ) + ( o.decay || .3 ) );
		osc.connect( f ); f.connect( g ); g.connect( dest );
		osc.start( t ); osc.stop( t + ( o.attack || .004 ) + ( o.decay || .3 ) + .05 );
	}

	/* the piano: hover lights the keys, press plays a note */
	var piano = $( "#ax-piano" );
	if ( piano ) {
		var whites = [ 0, 2, 4, 5, 7, 9, 11 ], blackAfter = { 0: 1, 1: 3, 3: 6, 4: 8, 5: 10 }, html = "";
		for ( var oct = 0; oct < 4; oct++ ) {
			for ( var k = 0; k < 7; k++ ) {
				var n = 48 + oct * 12 + whites[ k ];
				html += '<div class="w" data-n="' + n + '">' + ( blackAfter[ k ] !== undefined && !( oct === 3 && k === 6 ) ? '<span class="b" data-n="' + ( 48 + oct * 12 + blackAfter[ k ] ) + '"></span>' : "" ) + "</div>";
			}
		}
		piano.innerHTML = html;
		piano.addEventListener( "pointerdown", function ( e ) {
			var key = e.target.closest( ".b,.w" ); if ( !key ) { return; }
			key.classList.add( "lit" ); setTimeout( function () { key.classList.remove( "lit" ); }, 250 );
			var c = ac(); tone( c, c.destination, { type: "triangle", freq: midi( +key.getAttribute( "data-n" ) ), gain: .2, decay: .9, cutoff: 5000 } );
		} );
		var ran = false;
		if ( "IntersectionObserver" in window && !reduce ) {
			new IntersectionObserver( function ( en ) {
				if ( en[ 0 ].isIntersecting && !ran ) { ran = true; $$( ".w", piano ).forEach( function ( k, i ) { setTimeout( function () { k.classList.add( "lit" ); setTimeout( function () { k.classList.remove( "lit" ); }, 180 ); }, i * 45 ); } ); }
			}, { threshold: .6 } ).observe( piano );
		}
	}

	/* ------------------------------------------------------------------ the playground */
	var pg = $( "#ax-pg" );
	if ( pg ) {
		var sliders = $( "#ax-pg-sliders" ), snippet = $( "#ax-pg-snippet" ), playBtn = $( "#ax-pg-play" );
		var chords = { Dm: [ 62, 65, 69, 74 ], Bb: [ 58, 62, 65, 70 ], C: [ 60, 64, 67, 72 ], Am: [ 57, 60, 64, 69 ] };
		var arpTimer = null;
		var recipes = {
			kick: {
				params: [ { id: "pitch", label: "Pitch", min: 0.6, max: 1.4, step: 0.05, value: 0.95 }, { id: "decay", label: "Decay", min: 0.4, max: 1.6, step: 0.05, value: 0.9 } ],
				code: function ( p ) { return 'kick = audioDrum( type="kick", pitch=' + p.pitch + ', decay=' + p.decay + ', level=0.95 );\naudioSave( kick, "kick.wav" );'; },
				play: function ( c, p ) {
					var t = c.currentTime, osc = c.createOscillator(), g = c.createGain();
					osc.frequency.setValueAtTime( 170 * p.pitch, t ); osc.frequency.exponentialRampToValueAtTime( 44 * p.pitch, t + .12 );
					g.gain.setValueAtTime( 1, t ); g.gain.exponentialRampToValueAtTime( .001, t + .45 * p.decay );
					osc.connect( g ); g.connect( c.destination ); osc.start( t ); osc.stop( t + .6 * p.decay );
				}
			},
			bass: {
				params: [ { id: "wave", label: "Wave", options: [ "saw", "square" ], value: "saw" }, { id: "note", label: "Note", options: [ "D2", "A1", "C2", "Bb1" ], value: "D2" },
					{ id: "cutoff", label: "Filter cutoff", min: 200, max: 3000, step: 50, value: 900, unit: " Hz" }, { id: "q", label: "Resonance", min: 0.5, max: 8, step: 0.1, value: 2.4 } ],
				code: function ( p ) { return 'tone = audioOsc( "' + p.wave + '", audioFreq( "' + p.note + '" ), 0.3 );\nbass = audioFilter( tone, "lowpass", ' + p.cutoff + ', ' + p.q + ' );\nbass = audioEnvelope( bass, 0.003, 0.16 );\naudioSave( bass, "bass.wav" );'; },
				play: function ( c, p ) {
					var letter = p.note.charAt( 0 ), flat = p.note.charAt( 1 ) === "b", oct = parseInt( p.note.slice( -1 ), 10 );
					var n = 12 * ( oct + 1 ) + NOTE[ letter ] - ( flat ? 1 : 0 ), t = c.currentTime;
					for ( var i = 0; i < 4; i++ ) { tone( c, c.destination, { type: p.wave === "saw" ? "sawtooth" : "square", freq: midi( n ), cutoff: +p.cutoff, q: +p.q, gain: .22, decay: .14, at: t + i * .19 } ); }
				}
			},
			arp: {
				params: [ { id: "chord", label: "Chord", options: [ "Dm", "Bb", "C", "Am" ], value: "Dm" }, { id: "bpm", label: "Tempo", min: 90, max: 160, step: 1, value: 132, unit: " BPM" },
					{ id: "cutoff", label: "Filter cutoff", min: 400, max: 6000, step: 100, value: 3600, unit: " Hz" } ],
				code: function ( p ) { return 't = audioTimeline( bpm=' + p.bpm + ' );\nnotes = ' + JSON.stringify( chords[ p.chord ] ).replace( /,/g, ", " ).replace( "[", "[ " ).replace( "]", " ]" ) + ';   // ' + p.chord + '\nfor( s = 0; s < 16; s++ ){\n    pluck = audioFilter( audioOsc( "square", audioFreq( notes[ ( s mod 4 ) + 1 ] + 12 ), 0.25 ), "lowpass", ' + p.cutoff + ', 1.4 );\n    audioAdd( timeline=t, sound=audioEnvelope( pluck, 0.002, 0.1 ), at=s * ( 60 / ' + p.bpm + ' / 4 ), track="arp" );\n}\naudioSave( audioRender( t ), "arp.wav" );'; },
				play: function ( c, p ) {
					var notes = chords[ p.chord ], step = 60 / p.bpm / 4, t = c.currentTime, pattern = [ 0, 2, 1, 3, 2, 3, 1, 2, 0, 2, 1, 3, 2, 3, 1, 3 ];
					for ( var s = 0; s < 16; s++ ) {
						tone( c, c.destination, { type: "square", freq: midi( notes[ pattern[ s ] ] + 12 ), cutoff: +p.cutoff, q: 1.4, gain: s % 4 === 0 ? .12 : .07, decay: .1, at: t + s * step } );
					}
				}
			},
			chime: {
				params: [ { id: "spread", label: "Spread", min: 0.04, max: 0.2, step: 0.005, value: 0.085, unit: " s" }, { id: "ring", label: "Ring", min: 0.6, max: 2.2, step: 0.1, value: 1.4, unit: " s" } ],
				code: function ( p ) { return 't = audioTimeline( bpm=132 );\nnotes = [ 86, 90, 93, 98 ];\nfor( i = 1; i <= 4; i++ ){\n    bell = audioOsc( "sine", audioFreq( notes[ i ] ), ' + p.ring + ' );\n    audioAdd( timeline=t, sound=audioEnvelope( bell, 0.002, ' + ( p.ring * 0.75 ).toFixed( 2 ) + ' ), at=( i - 1 ) * ' + p.spread + ', track="chime" );\n}\naudioSave( audioRender( t ), "chime.wav" );'; },
				play: function ( c, p ) {
					var notes = [ 86, 90, 93, 98 ], t = c.currentTime, rv = reverbBus( c );
					notes.forEach( function ( n, i ) {
						tone( c, rv, { type: "sine", freq: midi( n ), gain: .16, attack: .002, decay: +p.ring, at: t + i * +p.spread } );
						tone( c, c.destination, { type: "sine", freq: midi( n ) * 2.01, gain: .03, attack: .002, decay: +p.ring * .6, at: t + i * +p.spread } );
					} );
				}
			}
		};
		var active = "kick", values = {};
		function render() {
			var r = recipes[ active ]; sliders.innerHTML = ""; values = {};
			r.params.forEach( function ( p ) {
				values[ p.id ] = p.value;
				var w = document.createElement( "div" ); w.className = "ax-slider";
				if ( p.options ) {
					w.innerHTML = '<label>' + p.label + '</label><select>' + p.options.map( function ( o ) { return '<option' + ( o === p.value ? " selected" : "" ) + '>' + o + '</option>'; } ).join( "" ) + '</select>';
					w.querySelector( "select" ).addEventListener( "change", function ( e ) { values[ p.id ] = e.target.value; update(); } );
				} else {
					w.innerHTML = '<label>' + p.label + ' <b>' + p.value + ( p.unit || "" ) + '</b></label><input type="range" min="' + p.min + '" max="' + p.max + '" step="' + p.step + '" value="' + p.value + '">';
					w.querySelector( "input" ).addEventListener( "input", function ( e ) { values[ p.id ] = +e.target.value; w.querySelector( "b" ).textContent = e.target.value + ( p.unit || "" ); update(); } );
				}
				sliders.appendChild( w );
			} );
			update();
		}
		function update() { snippet.textContent = recipes[ active ].code( values ); }
		$$( "[data-recipe]", pg ).forEach( function ( b ) {
			b.addEventListener( "click", function () {
				$$( "[data-recipe]", pg ).forEach( function ( x ) { x.classList.toggle( "on", x === b ); } );
				active = b.getAttribute( "data-recipe" ); render();
			} );
		} );
		playBtn.addEventListener( "click", function () { recipes[ active ].play( ac(), values ); } );
		var pgCopy = $( "#ax-pg-copy" );
		if ( pgCopy ) { pgCopy.addEventListener( "click", function () { copy( snippet.textContent, pgCopy ); } ); }
		render();
	}
}() );
