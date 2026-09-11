package com.simplemobiletools.musicplayer.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.IS_CUSTOMIZING_COLORS
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isTiramisuPlus
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.ActivitySettingsBinding
import com.simplemobiletools.musicplayer.dialogs.ManageVisibleTabsDialog
import com.simplemobiletools.musicplayer.dialogs.RestoreDataDialog
import com.simplemobiletools.musicplayer.dialogs.RestoreOptions
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.sendCommand
import com.simplemobiletools.musicplayer.helpers.BackupHelper
import com.simplemobiletools.musicplayer.helpers.SHOW_FILENAME_ALWAYS
import com.simplemobiletools.musicplayer.helpers.SHOW_FILENAME_IF_UNAVAILABLE
import com.simplemobiletools.musicplayer.helpers.SHOW_FILENAME_NEVER
import com.simplemobiletools.musicplayer.playback.CustomCommands
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleControllerActivity() {

    private val BACKUP_DATA_INTENT = 10001
    private val RESTORE_DATA_INTENT = 10002

    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(binding.settingsCoordinator, binding.settingsHolder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsToolbar)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.settingsToolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupCustomizeWidgetColors()
        setupUseEnglish()
        setupLanguage()
        setupManageExcludedFolders()
        setupManageShownTabs()
        setupSwapPrevNext()
        setupReplaceTitle()
        setupBackupData()
        setupRestoreData()
        setupGaplessPlayback()
        setupAutoplayOnBluetoothConnect()
        setupShowPlaybackActivity()
        setupKeepTrackLastPosition()
        updateTextColors(binding.settingsNestedScrollview)

        arrayOf(binding.settingsColorCustomizationSectionLabel, binding.settingsGeneralSettingsLabel, binding.settingsPlaybackSectionLabel).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun setupPurchaseThankYou() = binding.apply {
        settingsPurchaseThankYouHolder.beGoneIf(isOrWasThankYouInstalled())
        settingsPurchaseThankYouHolder.setOnClickListener {
            launchPurchaseThankYouIntent()
        }
    }

    private fun setupCustomizeColors() = binding.apply {
        settingsColorCustomizationLabel.text = getCustomizeColorsString()
        settingsColorCustomizationHolder.setOnClickListener {
            handleCustomizeColorsClick()
        }
    }

    private fun setupCustomizeWidgetColors() {
        binding.settingsWidgetColorCustomizationHolder.setOnClickListener {
            Intent(this, WidgetConfigureActivity::class.java).apply {
                putExtra(IS_CUSTOMIZING_COLORS, true)
                startActivity(this)
            }
        }
    }

    private fun setupUseEnglish() = binding.apply {
        settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settingsUseEnglish.isChecked = config.useEnglish
        settingsUseEnglishHolder.setOnClickListener {
            settingsUseEnglish.toggle()
            config.useEnglish = settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() = binding.apply {
        settingsLanguage.text = Locale.getDefault().displayLanguage
        settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
        settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupSwapPrevNext() = binding.apply {
        settingsSwapPrevNext.isChecked = config.swapPrevNext
        settingsSwapPrevNextHolder.setOnClickListener {
            settingsSwapPrevNext.toggle()
            config.swapPrevNext = settingsSwapPrevNext.isChecked
        }
    }

    private fun setupReplaceTitle() = binding.apply {
        settingsShowFilename.text = getReplaceTitleText()
        settingsShowFilenameHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SHOW_FILENAME_NEVER, getString(com.simplemobiletools.commons.R.string.never)),
                RadioItem(SHOW_FILENAME_IF_UNAVAILABLE, getString(R.string.title_is_not_available)),
                RadioItem(SHOW_FILENAME_ALWAYS, getString(com.simplemobiletools.commons.R.string.always))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.showFilename) {
                config.showFilename = it as Int
                settingsShowFilename.text = getReplaceTitleText()
                refreshQueueAndTracks()
            }
        }
    }

    private fun getReplaceTitleText() = getString(
        when (config.showFilename) {
            SHOW_FILENAME_NEVER -> com.simplemobiletools.commons.R.string.never
            SHOW_FILENAME_IF_UNAVAILABLE -> R.string.title_is_not_available
            else -> com.simplemobiletools.commons.R.string.always
        }
    )

    private fun setupManageShownTabs() = binding.apply {
        settingsManageShownTabsHolder.setOnClickListener {
            ManageVisibleTabsDialog(this@SettingsActivity) { result ->
                val tabsMask = config.showTabs
                if (tabsMask != result) {
                    config.showTabs = result
                    withPlayer {
                        sendCommand(CustomCommands.RELOAD_CONTENT)
                    }
                }
            }
        }
    }

    private fun setupManageExcludedFolders() {
        binding.settingsManageExcludedFoldersHolder.setOnClickListener {
            startActivity(Intent(this, ExcludedFoldersActivity::class.java))
        }
    }

    private fun setupGaplessPlayback() = binding.apply {
        settingsGaplessPlayback.isChecked = config.gaplessPlayback
        settingsGaplessPlaybackHolder.setOnClickListener {
            settingsGaplessPlayback.toggle()
            config.gaplessPlayback = settingsGaplessPlayback.isChecked
            withPlayer {
                sendCommand(CustomCommands.TOGGLE_SKIP_SILENCE)
            }
        }
    }

    private fun setupAutoplayOnBluetoothConnect() = binding.apply {
        settingsAutoplayOnBluetoothConnect.isChecked = config.autoplayOnBluetoothConnect
        settingsAutoplayOnBluetoothConnectHolder.setOnClickListener {
            settingsAutoplayOnBluetoothConnect.toggle()
            config.autoplayOnBluetoothConnect = settingsAutoplayOnBluetoothConnect.isChecked
        }
    }

    private fun setupShowPlaybackActivity() = binding.apply {
        settingsShowPlaybackActivity.isChecked = config.showPlaybackActivity
        settingsShowPlaybackActivityHolder.setOnClickListener {
            settingsShowPlaybackActivity.toggle()
            config.showPlaybackActivity = settingsShowPlaybackActivity.isChecked
        }
    }

    private fun setupBackupData() = binding.apply {
        settingsBackupDataHolder.setOnClickListener {
            ConfirmationDialog(
                activity = this@SettingsActivity,
                message = "",
                messageId = R.string.backup_data_description,
                positive = com.simplemobiletools.commons.R.string.ok,
                negative = com.simplemobiletools.commons.R.string.cancel
            ) {
                val fileName = "music_player_backup_${getCurrentFormattedDateTime()}.json"
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, fileName)
                    addCategory(Intent.CATEGORY_OPENABLE)

                    try {
                        startActivityForResult(this, BACKUP_DATA_INTENT)
                    } catch (e: ActivityNotFoundException) {
                        toast(com.simplemobiletools.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                    } catch (e: Exception) {
                        showErrorToast(e)
                    }
                }
            }
        }
    }

    private fun setupRestoreData() = binding.apply {
        settingsRestoreDataHolder.setOnClickListener {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/json"
                addCategory(Intent.CATEGORY_OPENABLE)

                try {
                    startActivityForResult(this, RESTORE_DATA_INTENT)
                } catch (e: ActivityNotFoundException) {
                    toast(com.simplemobiletools.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            when (requestCode) {
                BACKUP_DATA_INTENT -> {
                    val uri = resultData.data!!
                    val outputStream = contentResolver.openOutputStream(uri)
                    if (outputStream == null) {
                        toast(com.simplemobiletools.commons.R.string.unknown_error_occurred)
                        return
                    }
                    BackupHelper(this).exportData(outputStream) { success ->
                        runOnUiThread {
                            if (success) {
                                toast(R.string.backup_data_exported_successfully)
                            } else {
                                toast(com.simplemobiletools.commons.R.string.exporting_failed)
                            }
                        }
                    }
                }
                RESTORE_DATA_INTENT -> {
                    val uri = resultData.data!!
                    RestoreDataDialog(this) { options ->
                        if (!options.hasAnySelected()) {
                            toast(R.string.no_data_selected_to_restore)
                            return@RestoreDataDialog
                        }

                        val inputStream = contentResolver.openInputStream(uri)
                        if (inputStream == null) {
                            toast(com.simplemobiletools.commons.R.string.unknown_error_occurred)
                            return@RestoreDataDialog
                        }

                        BackupHelper(this).restoreData(inputStream, options) { success ->
                            runOnUiThread {
                                if (success) {
                                    toast(R.string.backup_data_restored_successfully)
                                    withPlayer {
                                        sendCommand(CustomCommands.RELOAD_CONTENT)
                                    }
                                    Intent(this, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        startActivity(this)
                                    }
                                    finish()
                                } else {
                                    toast(R.string.invalid_backup_file)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupKeepTrackLastPosition() = binding.apply {
        settingsKeeTrackLastPosition.isChecked = config.keepTrackLastPosition
        settingsKeeTrackLastPositionHolder.setOnClickListener {
            settingsKeeTrackLastPosition.toggle()
            config.keepTrackLastPosition = settingsKeeTrackLastPosition.isChecked
        }
    }
}
