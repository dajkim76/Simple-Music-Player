package com.simplemobiletools.musicplayer.models

import com.google.gson.annotations.SerializedName

data class Cue(
    @SerializedName("timestamp") val timestamp: Int,
    @SerializedName("title") val title: String,
    @SerializedName("enabled") val enabled: Boolean = true
)
