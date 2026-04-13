package com.simplemobiletools.musicplayer.helpers

import android.annotation.SuppressLint
import android.content.Context
import com.simplemobiletools.commons.helpers.BaseConfig
import com.tencent.mmkv.MMKV
import java.util.Arrays

// Config is singleton by getInstance()
class Config private constructor(context: Context) : BaseConfig(context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: Config? = null

        @Synchronized
        fun getInstance(context: Context): Config {
            if (MMKV.getRootDir() == null) MMKV.initialize(context)
            return instance ?: Config(context.applicationContext).also {
                instance = it
                it.migrate(context)
            }
        }
    }

    var isShuffleEnabled: Boolean
        get() = mmkv.decodeBool(SHUFFLE, false)
        set(shuffle) {
            mmkv.encode(SHUFFLE, shuffle)
        }

    var playbackSetting: PlaybackSetting
        get() = PlaybackSetting.values()[mmkv.decodeInt(PLAYBACK_SETTING, PlaybackSetting.REPEAT_OFF.ordinal)]
        set(playbackSetting) {
            mmkv.encode(PLAYBACK_SETTING, playbackSetting.ordinal)
        }

    var autoplay: Boolean
        get() = mmkv.decodeBool(AUTOPLAY, true)
        set(autoplay) {
            mmkv.encode(AUTOPLAY, autoplay)
        }

    var showFilename: Int
        get() = mmkv.decodeInt(SHOW_FILENAME, SHOW_FILENAME_IF_UNAVAILABLE)
        set(showFilename) {
            mmkv.encode(SHOW_FILENAME, showFilename)
        }

    var swapPrevNext: Boolean
        get() = mmkv.decodeBool(SWAP_PREV_NEXT, false)
        set(swapPrevNext) {
            mmkv.encode(SWAP_PREV_NEXT, swapPrevNext)
        }

    var lastSleepTimerSeconds: Int
        get() = mmkv.decodeInt(LAST_SLEEP_TIMER_SECONDS, 30 * 60)
        set(lastSleepTimerSeconds) {
            mmkv.encode(LAST_SLEEP_TIMER_SECONDS, lastSleepTimerSeconds)
        }

    var sleepInTS: Long
        get() = mmkv.decodeLong(SLEEP_IN_TS, 0)
        set(sleepInTS) {
            mmkv.encode(SLEEP_IN_TS, sleepInTS)
        }

    var playlistSorting: Int
        get() = mmkv.decodeInt(PLAYLIST_SORTING, PLAYER_SORT_BY_TITLE)
        set(playlistSorting) {
            mmkv.encode(PLAYLIST_SORTING, playlistSorting)
        }

    var playlistTracksSorting: Int
        get() = mmkv.decodeInt(PLAYLIST_TRACKS_SORTING, PLAYER_SORT_BY_TITLE)
        set(playlistTracksSorting) {
            mmkv.encode(PLAYLIST_TRACKS_SORTING, playlistTracksSorting)
        }

    fun saveCustomPlaylistSorting(playlistId: Int, value: Int) {
        mmkv.encode(SORT_PLAYLIST_PREFIX + playlistId, value)
    }

    fun getCustomPlaylistSorting(playlistId: Int) = mmkv.decodeInt(SORT_PLAYLIST_PREFIX + playlistId, sorting)

    fun removeCustomPlaylistSorting(playlistId: Int) {
        mmkv.remove(SORT_PLAYLIST_PREFIX + playlistId)
    }

    fun hasCustomPlaylistSorting(playlistId: Int) = mmkv.contains(SORT_PLAYLIST_PREFIX + playlistId)

    fun getProperPlaylistSorting(playlistId: Int) = if (hasCustomPlaylistSorting(playlistId)) {
        getCustomPlaylistSorting(playlistId)
    } else {
        playlistTracksSorting
    }

    fun getProperFolderSorting(path: String) = if (hasCustomSorting(path)) {
        getFolderSorting(path)
    } else {
        playlistTracksSorting
    }

    var folderSorting: Int
        get() = mmkv.decodeInt(FOLDER_SORTING, PLAYER_SORT_BY_TITLE)
        set(folderSorting) {
            mmkv.encode(FOLDER_SORTING, folderSorting)
        }

    var artistSorting: Int
        get() = mmkv.decodeInt(ARTIST_SORTING, PLAYER_SORT_BY_TITLE)
        set(artistSorting) {
            mmkv.encode(ARTIST_SORTING, artistSorting)
        }

    var albumSorting: Int
        get() = mmkv.decodeInt(ALBUM_SORTING, PLAYER_SORT_BY_TITLE)
        set(albumSorting) {
            mmkv.encode(ALBUM_SORTING, albumSorting)
        }

    var trackSorting: Int
        get() = mmkv.decodeInt(TRACK_SORTING, PLAYER_SORT_BY_TITLE)
        set(trackSorting) {
            mmkv.encode(TRACK_SORTING, trackSorting)
        }

    var queueSorting: Int
        get() = mmkv.decodeInt(QUEUE_SORTING, PLAYER_SORT_BY_TITLE)
        set(queueSorting) {
            mmkv.encode(QUEUE_SORTING, queueSorting)
        }

    var genreSorting: Int
        get() = mmkv.decodeInt(GENRE_SORTING, PLAYER_SORT_BY_TITLE)
        set(genreSorting) {
            mmkv.encode(GENRE_SORTING, genreSorting)
        }

    var equalizerEnabled: Boolean
        get() = mmkv.decodeBool(EQUALIZER_ENABLED, false)
        set(equalizerEnabled) {
            mmkv.encode(EQUALIZER_ENABLED, equalizerEnabled)
        }

    var equalizerPreset: Int
        get() = mmkv.decodeInt(EQUALIZER_PRESET, 0)
        set(equalizerPreset) {
            mmkv.encode(EQUALIZER_PRESET, equalizerPreset)
        }

    var equalizerBands: String
        get() = mmkv.decodeString(EQUALIZER_BANDS, "")!!
        set(equalizerBands) {
            mmkv.encode(EQUALIZER_BANDS, equalizerBands)
        }

    var playbackSpeed: Float
        get() = mmkv.decodeFloat(PLAYBACK_SPEED, 1f)
        set(playbackSpeed) {
            mmkv.encode(PLAYBACK_SPEED, playbackSpeed)
        }

    var playbackSpeedProgress: Int
        get() = mmkv.decodeInt(PLAYBACK_SPEED_PROGRESS, -1)
        set(playbackSpeedProgress) {
            mmkv.encode(PLAYBACK_SPEED_PROGRESS, playbackSpeedProgress)
        }

    var wasAllTracksPlaylistCreated: Boolean
        get() = mmkv.decodeBool(WAS_ALL_TRACKS_PLAYLIST_CREATED, false)
        set(wasAllTracksPlaylistCreated) {
            mmkv.encode(WAS_ALL_TRACKS_PLAYLIST_CREATED, wasAllTracksPlaylistCreated)
        }

    var tracksRemovedFromAllTracksPlaylist: MutableSet<String>
        get() = mmkv.decodeStringSet(TRACKS_REMOVED_FROM_ALL_TRACKS_PLAYLIST, HashSet())!!
        set(tracksRemovedFromAllTracksPlaylist) {
            mmkv.putStringSet(TRACKS_REMOVED_FROM_ALL_TRACKS_PLAYLIST, tracksRemovedFromAllTracksPlaylist)
        }

    var showTabs: Int
        get() = mmkv.decodeInt(SHOW_TABS, ALL_TABS_MASK)
        set(showTabs) {
            mmkv.encode(SHOW_TABS, showTabs)
        }

    var excludedFolders: MutableSet<String>
        get() = mmkv.decodeStringSet(EXCLUDED_FOLDERS, HashSet())!!
        set(excludedFolders) {
            mmkv.putStringSet(EXCLUDED_FOLDERS, excludedFolders)
        }

    fun addExcludedFolder(path: String) {
        addExcludedFolders(HashSet(Arrays.asList(path)))
    }

    fun addExcludedFolders(paths: Set<String>) {
        val currExcludedFolders = HashSet(excludedFolders)
        currExcludedFolders.addAll(paths.map { it.removeSuffix("/") })
        excludedFolders = currExcludedFolders.filter { it.isNotEmpty() }.toHashSet()
    }

    fun removeExcludedFolder(path: String) {
        val currExcludedFolders = HashSet(excludedFolders)
        currExcludedFolders.remove(path)
        excludedFolders = currExcludedFolders
    }

    var gaplessPlayback: Boolean
        get() = mmkv.decodeBool(GAPLESS_PLAYBACK, false)
        set(gaplessPlayback) {
            mmkv.encode(GAPLESS_PLAYBACK, gaplessPlayback)
        }

    var autoplayOnBluetoothConnect: Boolean
        get() = mmkv.decodeBool(AUTOPLAY_ON_BLUETOOTH_CONNECT, false)
        set(autoplayOnBluetoothConnect) {
            mmkv.encode(AUTOPLAY_ON_BLUETOOTH_CONNECT, autoplayOnBluetoothConnect)
        }

    // When you play a track, it takes you to the playback screen for that track.
    var showPlaybackActivity: Boolean
        get() = mmkv.decodeBool(SHOW_PLAYBACK_ACTIVITY, false)
        set(showPlaybackActivity) {
            mmkv.encode(SHOW_PLAYBACK_ACTIVITY, showPlaybackActivity)
        }

    // When the user plays a track, it starts from the last playback position.
    var keepTrackLastPosition: Boolean
        get() = mmkv.decodeBool(KEEP_TRACK_LAST_POSITION, true)
        set(keepTrackLastPosition) {
            mmkv.encode(KEEP_TRACK_LAST_POSITION, keepTrackLastPosition)
        }

    var lastFullScanTime: Long
        get() = mmkv.decodeLong("last_full_scan_time", 0L)
        set(time) {
            mmkv.encode("last_full_scan_time", time)
        }

    var lastQueueSource by string("last_queue_source", "")

    var queueId by long("queue_id", 0)  // current selected queue id (0 is legacy queue)

    var tabQueueId by long("tab_queue_id", 0)

    var nextQueueId by long("next_queue_id", 1) // crete new queue with generate unique queue id (user defined queue id from 1)

    var queueListJson by string("queueList", "[]") // List<QueueData>
}

fun isInExcludeFolders(path: String, excludeFolder: Set<String>): Boolean {
    return excludeFolder.any { path.startsWith(it) }
}

fun isInExcludeFolders(path: String, excludeFolder: List<String>): Boolean {
    return excludeFolder.any { path.startsWith(it) }
}

class FolderConfig private constructor() {
    private val mmkv: MMKV = MMKV.mmkvWithID("folder_config")

    fun getFolderFavoriteTime(folderName: String) = mmkv.decodeLong("f:$folderName", 0) // f means `Favorite

    fun setFolderFavoriteTime(favoriteData: List<Pair<String, Long>>) {
        favoriteData.forEach { (folderName, time) ->
            val key = "f:$folderName"
            if (time > 0) {
                mmkv.encode(key, time)
            } else {
                mmkv.remove(key)
            }
        }
    }

    fun getFolderFavoriteList(): List<String> {
        val result = mmkv.allKeys()?.mapNotNull { key ->
            if (key.startsWith("f:"))
                key.substring(2) to mmkv.decodeLong(key, 0) // FolderName to FavoriteTime
            else
                null
        }?.sortedByDescending { it.second }?.map { it.first }
        return result ?: emptyList()
    }

    fun getLastMediaId(folderName: String): Long = mmkv.decodeLong("m:$folderName", 0)  // m means 'Media'

    fun updateLastMediaId(folderName: String, lastMediaId: Long) = mmkv.encode("m:$folderName", lastMediaId)

    private fun migrate(context: Context) {
        val folderConfig = context.getSharedPreferences("folder_config", Context.MODE_PRIVATE)
        if (folderConfig.all.isNotEmpty()) {
            folderConfig.all.map { (key, _) ->
                val favoriteTime = folderConfig.getLong(key, 0)
                if (favoriteTime > 0) {
                    val folderName = key.substringAfterLast('/')
                    mmkv.encode("f:$folderName", favoriteTime)
                }
            }
            folderConfig.edit().clear().apply()
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: FolderConfig? = null

        @Synchronized
        fun getInstance(context: Context): FolderConfig {
            if (MMKV.getRootDir() == null) MMKV.initialize(context)
            return instance ?: FolderConfig().also {
                instance = it
                it.migrate(context)
            }
        }
    }
}
