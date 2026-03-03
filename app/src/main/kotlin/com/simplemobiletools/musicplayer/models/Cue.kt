package com.simplemobiletools.musicplayer.models

import com.google.gson.annotations.SerializedName

data class Cue(
    @SerializedName("timestamp") val timestamp: Int,
    @SerializedName("title") var title: String,
    @SerializedName("enabled") var enabled: Boolean = true
)
