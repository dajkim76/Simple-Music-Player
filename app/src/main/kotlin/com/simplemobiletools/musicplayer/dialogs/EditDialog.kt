package com.simplemobiletools.musicplayer.dialogs

import android.content.ContentUris
import android.content.res.ColorStateList
import android.os.Looper
import android.provider.MediaStore
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.DialogRenameSongBinding
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.helpers.CueListCache
import com.simplemobiletools.musicplayer.helpers.TagHelper
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Track
import org.greenrobot.eventbus.EventBus
import java.io.File

class EditDialog(val activity: BaseSimpleActivity, val track: Track, val callback: (track: Track) -> Unit) {
    private val tagHelper = TagHelper(activity)
    private val binding by activity.viewBinding(DialogRenameSongBinding::inflate)
    private val oldFileStableId: Long
        get() {
            val file = File(track.path)
            if (file.exists()) {
                track.fileLength = file.length()
                track.fileLastModified = file.lastModified()
            }
            return track.fileStableId
        }

    init {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.mediaStoreId)
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
        )
        try {
            activity.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getStringValueOrNull(MediaStore.Audio.Media.TITLE)?.let { track.title = it }
                    cursor.getStringValueOrNull(MediaStore.Audio.Media.ARTIST)?.let { track.artist = it }
                    cursor.getStringValueOrNull(MediaStore.Audio.Media.ALBUM)?.let { track.album = it }
                }
            }
        } catch (ignored: Exception) {
        }

        binding.apply {
            title.setText(track.title)
            artist.setText(track.artist)
            album.setText(track.album)
            val filename = track.path.getFilenameFromPath()
            fileName.setText(filename.substring(0, filename.lastIndexOf(".")))
            extension.setText(track.path.getFilenameExtension())
            if (isRPlus()) {
                arrayOf(fileNameHint, extensionHint).forEach {
                    it.beGone()
                }
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.rename_song) { alertDialog ->
                    alertDialog.showKeyboard(binding.title)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = binding.title.value
                        val newArtist = binding.artist.value
                        val newAlbum = binding.album.value
                        val newFilename = binding.fileName.value
                        val newFileExtension = binding.extension.value

                        if (newTitle.isEmpty() || newArtist.isEmpty() || newFilename.isEmpty() || newFileExtension.isEmpty()) {
                            activity.toast(R.string.rename_song_empty)
                            return@setOnClickListener
                        }

                        val initialStableId = oldFileStableId
                        if (track.title != newTitle || track.artist != newArtist || track.album != newAlbum) {
                            updateContentResolver(track, newArtist, newTitle, newAlbum) {
                                track.artist = newArtist
                                track.title = newTitle
                                track.album = newAlbum
                                val oldPath = track.path
                                val newPath = "${oldPath.getParentPath()}/$newFilename.$newFileExtension"
                                if (oldPath == newPath) {
                                    storeEditedSong(track, initialStableId, oldPath, newPath) {
                                        callback(track)
                                        alertDialog.dismiss()
                                    }
                                    return@updateContentResolver
                                }

                                if (!isRPlus()) {
                                    activity.renameFile(oldPath, newPath, false) { success, _ ->
                                        if (success) {
                                            track.path = newPath
                                            storeEditedSong(track, initialStableId, oldPath, newPath) {
                                                callback(track)
                                                alertDialog.dismiss()
                                            }
                                        } else {
                                            activity.toast(R.string.rename_song_error)
                                            alertDialog.dismiss()
                                        }
                                    }
                                }
                            }
                        } else {
                            val oldPath = track.path
                            val newPath = "${oldPath.getParentPath()}/$newFilename.$newFileExtension"
                            if (oldPath != newPath && !isRPlus()) {
                                activity.renameFile(oldPath, newPath, false) { success, _ ->
                                    if (success) {
                                        track.path = newPath
                                        storeEditedSong(track, initialStableId, oldPath, newPath) {
                                            callback(track)
                                            alertDialog.dismiss()
                                        }
                                    } else {
                                        activity.toast(R.string.rename_song_error)
                                        alertDialog.dismiss()
                                    }
                                }
                            } else {
                                alertDialog.dismiss()
                            }
                        }
                    }
                }
            }
    }

    private fun storeEditedSong(track: Track, oldFileStableId: Long, oldPath: String, newPath: String, onDone: () -> Unit) {
        val finalFile = File(newPath)
        if (finalFile.exists()) {
            track.fileLength = finalFile.length()
            track.fileLastModified = finalFile.lastModified()
        }
        val newFileStableId = track.fileStableId

        ensureBackgroundThread {
            try {
                activity.audioHelper.updateTrackInfo(newPath, track.artist, track.title, track.album, oldPath, track.fileLength, track.fileLastModified)
                if (oldFileStableId != newFileStableId) {
                    activity.audioHelper.updateCueFileStableId(oldFileStableId, newFileStableId, newPath, track.fileLength, track.fileLastModified)
                    CueListCache.migrateCache(oldFileStableId, newFileStableId)
                }
                EventBus.getDefault().post(Events.RefreshTracks())
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }
            activity.runOnUiThread {
                onDone()
            }
        }
    }

    private fun updateContentResolver(track: Track, newArtist: String, newTitle: String, newAlbum: String, onUpdateMediaStore: () -> Unit) {
        var loadingDialog: AlertDialog? = null

        fun showLoading() {
            activity.runOnUiThread {
                if (loadingDialog == null && !activity.isFinishing && !activity.isDestroyed) {
                    val loadingView = activity.layoutInflater.inflate(R.layout.dialog_loading, null)
                    loadingView.findViewById<ProgressBar>(R.id.progress_bar)?.indeterminateTintList =
                        ColorStateList.valueOf(activity.getProperPrimaryColor())
                    activity.setupDialogStuff(loadingView, activity.getAlertDialogBuilder(), cancelOnTouchOutside = false) { dialog ->
                        dialog.setCancelable(false)
                        loadingDialog = dialog
                    }
                }
            }
        }

        fun dismissLoading() {
            activity.runOnUiThread {
                loadingDialog?.dismiss()
                loadingDialog = null
            }
        }

        val doWriteTag: () -> Unit = {
            try {
                tagHelper.writeTag(track, newArtist, newTitle, newAlbum)
                activity.runOnUiThread {
                    dismissLoading()
                    onUpdateMediaStore.invoke()
                }
            } catch (e: SecurityException) {
                dismissLoading()
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("EditDialog", "Error updating tags", e)
                dismissLoading()
                activity.showErrorToast(e)
            }
        }

        ensureBackgroundThread {
            try {
                activity.handleRecoverableSecurityException { granted ->
                    if (granted) {
                        showLoading()
                        if (Looper.myLooper() == Looper.getMainLooper()) {
                            ensureBackgroundThread {
                                doWriteTag()
                            }
                        } else {
                            doWriteTag()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("EditDialog", "Error updating tags", e)
                dismissLoading()
                activity.showErrorToast(e)
            }
        }
    }
}
