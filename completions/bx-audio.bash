#!/usr/bin/env bash
# Tab completion for the bx-audio command
_bx_audio_complete() {
    local current_word="${COMP_WORDS[COMP_CWORD]}"
    local command="${COMP_WORDS[1]}"
    local commands="info convert normalize trim waveform pieces render explorer doctor version help"
    if [ "$COMP_CWORD" -eq 1 ]; then
        COMPREPLY=($(compgen -W "$commands" -- "$current_word"))
        return
    fi
    case "$command" in
        render)
            if [ "$COMP_CWORD" -eq 2 ]; then
                COMPREPLY=($(compgen -W "pop-drive lofi house ambient techno synthwave cinematic" -- "$current_word"))
            else
                COMPREPLY=($(compgen -W "--out= --bars= --bpm= --style= --swing= --seed=" -- "$current_word"))
            fi ;;
        convert) COMPREPLY=($(compgen -f -W "--bitrate= --bitDepth= --sampleRate= --channels=" -- "$current_word")) ;;
        normalize) COMPREPLY=($(compgen -f -W "--db=" -- "$current_word")) ;;
        trim) COMPREPLY=($(compgen -f -W "--start= --end=" -- "$current_word")) ;;
        waveform) COMPREPLY=($(compgen -f -W "--width= --unicode" -- "$current_word")) ;;
        explorer) COMPREPLY=($(compgen -W "--port=" -- "$current_word")) ;;
        help) COMPREPLY=($(compgen -W "$commands" -- "$current_word")) ;;
        *) COMPREPLY=($(compgen -f -- "$current_word")) ;;
    esac
}
complete -o filenames -F _bx_audio_complete bx-audio
