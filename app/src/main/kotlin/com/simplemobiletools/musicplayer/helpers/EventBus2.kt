package com.simplemobiletools.musicplayer.helpers

import kotlinx.coroutines.flow.MutableStateFlow

object EventBus2 {
    val cueTitleFlow = MutableStateFlow<String?>(null)
}
