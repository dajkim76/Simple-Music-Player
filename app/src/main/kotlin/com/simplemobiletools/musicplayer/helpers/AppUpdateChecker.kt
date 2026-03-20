package com.simplemobiletools.musicplayer.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.musicplayer.BuildConfig
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.MARKET_URL
import java.util.concurrent.TimeUnit

class AppUpdateChecker private constructor(private val context: Activity) {
    private fun check() {
        val remoteConfig = Firebase.remoteConfig
        val intervalDays = remoteConfig.getLong("update_alert_interval_days")
        if (intervalDays <= 0L) { // default
            return
        }

        val latest = remoteConfig.getLong("latest_version_code").toInt()
        val force = remoteConfig.getLong("force_update_version").toInt()
        val current = BuildConfig.VERSION_CODE

        if (current < force) {
            showUpdateDialog(isCancelable = false)
            return
        }

        if (current < latest) {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val lastAlert = prefs.getLong(PREF_LAST_ALERT, 0)
            val interval = if (BuildConfig.DEBUG) TimeUnit.MINUTES.toMillis(1) else TimeUnit.DAYS.toMillis(intervalDays)
            val now = System.currentTimeMillis()

            if (now - lastAlert > interval) {
                showUpdateDialog(isCancelable = true)
                prefs.edit()
                    .putLong(PREF_LAST_ALERT, now)
                    .apply()
            }
        }
    }

    fun showUpdateDialog(isCancelable: Boolean) {
        val builder = AlertDialog.Builder(context)
            .setMessage(R.string.update_message)
            .setPositiveButton(R.string.update) { _, _ ->
                openReleasePage()
                if (!isCancelable) {
                    context.finishAffinity()
                }
            }
            .setCancelable(isCancelable)

        if (isCancelable) {
            builder.setNegativeButton(context.getString(com.simplemobiletools.commons.R.string.cancel), null)
        }
        builder.show()
    }

    private fun openReleasePage() {
        try {
            val uri = Uri.parse(MARKET_URL)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (ex: Exception) {
            context.toast(ex.message ?: "Unknown error")
        }
    }

    companion object {
        private const val PREF_LAST_ALERT = "last_update_alert"

        fun check(activity: Activity) {
            try {
                AppUpdateChecker(activity).check()
            } catch (_: Exception) {
                // ignore
            }
        }
    }
}
