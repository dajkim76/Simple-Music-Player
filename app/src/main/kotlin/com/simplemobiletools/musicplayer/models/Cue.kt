package com.simplemobiletools.musicplayer.models

data class Cue(
    val timestamp: Int,
    val title: String,
    val enabled: Boolean = true
)
