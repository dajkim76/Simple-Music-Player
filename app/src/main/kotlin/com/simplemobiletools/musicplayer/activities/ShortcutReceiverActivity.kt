package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.views.MyEditText
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.dialogs.SelectTracklistDialog.Companion.TRACKLIST_ALBUM
import com.simplemobiletools.musicplayer.dialogs.SelectTracklistDialog.Companion.TRACKLIST_ARTIST
import com.simplemobiletools.musicplayer.dialogs.SelectTracklistDialog.Companion.TRACKLIST_FOLDER
import com.simplemobiletools.musicplayer.dialogs.SelectTracklistDialog.Companion.TRACKLIST_PLAYLIST
import com.simplemobiletools.musicplayer.dialogs.SelectTracklistDialog.Companion.onSelectTracklist
import com.simplemobiletools.musicplayer.objects.executeBackgroundThread

class ShortcutReceiverActivity : SimpleControllerActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val queueSource = intent.getStringExtra("queue_source") ?: ""
        executeBackgroundThread {
            if (queueSource.startsWith("p:")) {
                val id = queueSource.substring(2).toLong()
                onSelectTracklist(TRACKLIST_PLAYLIST, id, "")
            } else if (queueSource.startsWith("a:")) {
                val id = queueSource.substring(2).toLong()
                onSelectTracklist(TRACKLIST_ALBUM, id, "")
            } else if (queueSource.startsWith("f:")) {
                val data = queueSource.substring(2)
                onSelectTracklist(TRACKLIST_FOLDER, 0, data)
            } else if (queueSource.startsWith("t:")) {
                val id = queueSource.substring(2).toLong()
                onSelectTracklist(TRACKLIST_ARTIST, id, "")
            }
        }

        toast(com.simplemobiletools.commons.R.string.ok)
        finish()
    }

    companion object {
        fun SimpleControllerActivity.createTracklistShortcut(shortcutLabel: String, queueSource: String) {
            if (queueSource.isEmpty()) return

            // build layout
            val edit = MyEditText(this).apply {
                isSingleLine = true
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setText(shortcutLabel)
            }
            val p = resources.getDimensionPixelSize(com.simplemobiletools.commons.R.dimen.medium_margin)
            val layout = LinearLayout(this).apply {
                setPadding(p, p, p, p)
                addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }

            val builder = getAlertDialogBuilder()
                .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
                .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ ->
                    val title = edit.text.toString().takeIf { it.isNotEmpty() } ?: shortcutLabel
                    val icon = IconCompat.createWithResource(this, R.drawable.ic_tracklist)
                    val shortcut = ShortcutInfoCompat.Builder(this, "tracklist_$queueSource")
                        .setShortLabel(title)
                        .setIcon(icon)
                        .setIntent(Intent(this, ShortcutReceiverActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            putExtra("queue_source", queueSource)
                        })
                        .build()

                    ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
                }

            setupDialogStuff(layout, builder, titleId = com.simplemobiletools.commons.R.string.create_shortcut)
        }
    }
}
