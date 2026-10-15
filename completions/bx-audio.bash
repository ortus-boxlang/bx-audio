#!/usr/bin/env bash
# Tab completion for the bx-audio command
_bx_audio_complete() {
    local current_word="${COMP_WORDS[COMP_CWORD]}"
    local command="${COMP_WORDS[1]}"
    local commands="info convert normalize trim loudness master stretch pitch bpm silence loopify sprite play waveform speak voices licenses install remove where clean ffmpeg probe mux pieces render explorer doctor version help"
    if [ "$COMP_CWORD" -eq 1 ]; then
        COMPREPLY=($(compgen -W "$commands" -- "$current_word"))
        return
    fi
    case "$command" in
        render)
            if [ "$COMP_CWORD" -eq 2 ]; then
                COMPREPLY=($(compgen -W "pop-drive lofi house ambient techno synthwave cinematic chiptune sfx jazz rock voiceover" -- "$current_word"))
            else
                COMPREPLY=($(compgen -W "--out= --bars= --bpm= --style= --swing= --seed= --track= --loops= --sound= --folder= --choruses= --voice= --text= --voiceFile= --duck= --install=" -- "$current_word"))
            fi ;;
        convert) COMPREPLY=($(compgen -f -W "--bitrate= --bitDepth= --sampleRate= --channels=" -- "$current_word")) ;;
        normalize) COMPREPLY=($(compgen -f -W "--db=" -- "$current_word")) ;;
        trim) COMPREPLY=($(compgen -f -W "--start= --end=" -- "$current_word")) ;;
        waveform) COMPREPLY=($(compgen -f -W "--width= --unicode" -- "$current_word")) ;;
        speak) COMPREPLY=($(compgen -f -W "--voice= --out= --file= --speed= --captions= --offset= --max-chars= --by-clause --sentence-pause= --clause-pause= --paragraph-pause= --no-install" -- "$current_word")) ;;
        voices) COMPREPLY=($(compgen -W "--lang= --installed --downloadable --excluded --all --license=" -- "$current_word")) ;;
        master) COMPREPLY=($(compgen -f -W "--target= --truePeak= --glue" -- "$current_word")) ;;
        stretch) COMPREPLY=($(compgen -f -W "--rate= --seconds=" -- "$current_word")) ;;
        pitch) COMPREPLY=($(compgen -f -W "--semitones=" -- "$current_word")) ;;
        bpm) COMPREPLY=($(compgen -f -W "--beats" -- "$current_word")) ;;
        silence) COMPREPLY=($(compgen -f -W "--db= --min= --trim=" -- "$current_word")) ;;
        loopify) COMPREPLY=($(compgen -f -W "--crossfade=" -- "$current_word")) ;;
        sprite) COMPREPLY=($(compgen -f -W "--gap=" -- "$current_word")) ;;
        play) COMPREPLY=($(compgen -f -W "--volume= --output=" -- "$current_word")) ;;
        mux) COMPREPLY=($(compgen -f -W "--keep-original --offset=" -- "$current_word")) ;;
        ffmpeg) COMPREPLY=($(compgen -W "status install" -- "$current_word")) ;;
        clean) COMPREPLY=($(compgen -W "engine voices ffmpeg cache all --yes" -- "$current_word")) ;;
        explorer) COMPREPLY=($(compgen -W "--port=" -- "$current_word")) ;;
        help) COMPREPLY=($(compgen -W "$commands" -- "$current_word")) ;;
        *) COMPREPLY=($(compgen -f -- "$current_word")) ;;
    esac
}
complete -o filenames -F _bx_audio_complete bx-audio
