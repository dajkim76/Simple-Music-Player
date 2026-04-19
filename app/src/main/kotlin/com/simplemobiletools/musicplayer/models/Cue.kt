package com.simplemobiletools.musicplayer.models

import com.google.gson.annotations.SerializedName

data class Cue(
    @SerializedName("timestamp") var timestamp: Int,
    @SerializedName("title") var title: String,
    @SerializedName("enabled") var enabled: Boolean = true,
    @SerializedName("favorite") var favorite: Boolean = false,
    @Transient var duration: Int = 0,
    @Transient var isRepeat: Boolean = false
)
