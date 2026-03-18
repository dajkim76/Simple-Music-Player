package com.simplemobiletools.musicplayer.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.*
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.simplemobiletools.commons.R
import com.simplemobiletools.commons.activities.ContributorsActivity
import com.simplemobiletools.commons.activities.FAQActivity
import com.simplemobiletools.commons.activities.LicenseActivity
import com.simplemobiletools.commons.compose.alert_dialog.rememberAlertDialogState
import com.simplemobiletools.commons.compose.extensions.MyDevices
import com.simplemobiletools.commons.compose.extensions.enableEdgeToEdgeSimple
import com.simplemobiletools.commons.compose.extensions.rateStarsRedirectAndThankYou
import com.simplemobiletools.commons.compose.lists.SimpleColumnScaffold
import com.simplemobiletools.commons.compose.settings.SettingsGroup
import com.simplemobiletools.commons.compose.settings.SettingsHorizontalDivider
import com.simplemobiletools.commons.compose.settings.SettingsListItem
import com.simplemobiletools.commons.compose.settings.SettingsTitleTextComponent
import com.simplemobiletools.commons.compose.theme.AppThemeSurface
import com.simplemobiletools.commons.compose.theme.SimpleTheme
import com.simplemobiletools.commons.dialogs.ConfirmationAdvancedAlertDialog
import com.simplemobiletools.commons.dialogs.RateStarsAlertDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.FAQItem

private const val MY_EMAIL = "kimdaejeong@gmail.com"
const val DEMO_VIDEO_URL = "https://www.youtube.com/watch?v=X_yMfMboIP4"
const val RELEASE_URL = "https://github.com/dajkim76/Simple-Music-Player/releases"

class MyAboutActivity : ComponentActivity() {
    private val appName get() = intent.getStringExtra(APP_NAME) ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeSimple()
        setContent {
            val context = LocalContext.current
            val resources = context.resources
            AppThemeSurface {
                val showExternalLinks = remember { !resources.getBoolean(R.bool.hide_all_external_links) }
                val showGoogleRelations = remember { !resources.getBoolean(R.bool.hide_google_relations) }
                val onEmailClickAlertDialogState = getOnEmailClickAlertDialogState()
                val youtubeTimestampAlertDialogState = getYoutubeTimestampAlertDialogState()
                val rateStarsAlertDialogState = getRateStarsAlertDialogState()
                val onRateUsClickAlertDialogState = getOnRateUsClickAlertDialogState(rateStarsAlertDialogState::show)
                AboutScreen(
                    goBack = ::finish,
                    helpUsSection = {
                        val showHelpUsSection =
                            remember { showGoogleRelations || !showExternalLinks }
                        HelpUsSection(
                            onRateUsClick = {
                                onRateUsClick(
                                    showConfirmationAdvancedDialog = onRateUsClickAlertDialogState::show,
                                    showRateStarsDialog = rateStarsAlertDialogState::show
                                )
                            },
                            onInviteClick = ::onInviteClick,
                            onContributorsClick = ::onContributorsClick,
                            showDonate = resources.getBoolean(R.bool.show_donate_in_about) && showExternalLinks,
                            onDonateClick = ::onDonateClick,
                            showInvite = showHelpUsSection,
                            showRateUs = false /* Disable App rating */
                        )
                    },
                    aboutSection = {
                        val setupFAQ = rememberFAQ()
                        if (!showExternalLinks || setupFAQ) {
                            AboutSection(setupFAQ = setupFAQ, onFAQClick = ::launchFAQActivity, onEmailClick = {
                                onEmailClick(onEmailClickAlertDialogState::show)
                            }, onYoutubeTimestampClick = youtubeTimestampAlertDialogState::show)
                        }
                    },
                    socialSection = {
                        if (showExternalLinks) {
                            SocialSection(
                                onFacebookClick = ::onFacebookClick,
                                onGithubClick = ::onGithubClick,
                                onRedditClick = ::onRedditClick,
                                onTelegramClick = ::onTelegramClick
                            )
                        }
                    }
                ) {
                    val (showWebsite, fullVersion) = showWebsiteAndFullVersion(resources, showExternalLinks)
                    OtherSection(
                        showMoreApps = showGoogleRelations,
                        onMoreAppsClick = ::launchMoreAppsFromUsIntent,
                        showWebsite = showWebsite,
                        onWebsiteClick = ::onWebsiteClick,
                        showPrivacyPolicy = showExternalLinks,
                        onPrivacyPolicyClick = ::onPrivacyPolicyClick,
                        onLicenseClick = ::onLicenseClick,
                        version = fullVersion,
                        onVersionClick = ::onVersionClick,
                        onAppIconClick = ::onAppIconClick,
                    )
                }
            }
        }
    }

    @Composable
    private fun rememberFAQ() = remember { !(intent.getSerializableExtra(APP_FAQ) as? ArrayList<FAQItem>).isNullOrEmpty() }

    @Composable
    private fun showWebsiteAndFullVersion(
        resources: Resources,
        showExternalLinks: Boolean
    ): Pair<Boolean, String> {
        val showWebsite = remember { resources.getBoolean(R.bool.show_donate_in_about) && !showExternalLinks }
        var version = intent.getStringExtra(APP_VERSION_NAME) ?: ""
        if (baseConfig.appId.removeSuffix(".debug").endsWith(".pro")) {
            version += " ${getString(R.string.pro)}"
        }
        val fullVersion = remember { String.format(getString(R.string.version_placeholder, version)) }
        return Pair(showWebsite, fullVersion)
    }

    @Composable
    private fun getRateStarsAlertDialogState() =
        rememberAlertDialogState().apply {
            DialogMember {
                RateStarsAlertDialog(alertDialogState = this, onRating = ::rateStarsRedirectAndThankYou)
            }
        }

    @Composable
    private fun getOnEmailClickAlertDialogState() =
        rememberAlertDialogState().apply {
            DialogMember {
                ConfirmationAdvancedAlertDialog(
                    alertDialogState = this,
                    message = "${getString(R.string.before_asking_question_read_faq)}\n\n${getString(R.string.make_sure_latest)}",
                    messageId = null,
                    positive = R.string.read_faq,
                    negative = R.string.skip
                ) { success ->
                    if (success) {
                        launchFAQActivity()
                    } else {
                        launchEmailIntent()
                    }
                }
            }
        }

    @Composable
    private fun getOnRateUsClickAlertDialogState(showRateStarsDialog: () -> Unit) =
        rememberAlertDialogState().apply {
            DialogMember {
                ConfirmationAdvancedAlertDialog(
                    alertDialogState = this,
                    message = "${getString(R.string.before_asking_question_read_faq)}\n\n${getString(R.string.make_sure_latest)}",
                    messageId = null,
                    positive = R.string.read_faq,
                    negative = R.string.skip
                ) { success ->
                    if (success) {
                        launchFAQActivity()
                    } else {
                        launchRateUsPrompt(showRateStarsDialog)
                    }
                }
            }
        }

    @Composable
    private fun getYoutubeTimestampAlertDialogState() =
        rememberAlertDialogState().apply {
            DialogMember {
                ConfirmationAdvancedAlertDialog(
                    alertDialogState = this,
                    message = stringResource(id = com.simplemobiletools.musicplayer.R.string.youtube_timestamp_info),
                    messageId = null,
                    positive = com.simplemobiletools.musicplayer.R.string.demo_video,
                    negative = com.simplemobiletools.commons.R.string.cancel
                ) { success ->
                    if (success) {
                        launchViewIntent(DEMO_VIDEO_URL)
                    }
                }
            }
        }

    private fun onEmailClick(
        showConfirmationAdvancedDialog: () -> Unit
    ) {
        if (intent.getBooleanExtra(SHOW_FAQ_BEFORE_MAIL, false) && !baseConfig.wasBeforeAskingShown) {
            baseConfig.wasBeforeAskingShown = true
            showConfirmationAdvancedDialog()
        } else {
            launchEmailIntent()
        }
    }

    private fun launchFAQActivity() {
        val faqItems = intent.getSerializableExtra(APP_FAQ) as ArrayList<FAQItem>
        Intent(applicationContext, FAQActivity::class.java).apply {
            putExtra(APP_ICON_IDS, intent.getIntegerArrayListExtra(APP_ICON_IDS) ?: ArrayList<String>())
            putExtra(APP_LAUNCHER_NAME, intent.getStringExtra(APP_LAUNCHER_NAME) ?: "")
            putExtra(APP_FAQ, faqItems)
            startActivity(this)
        }
    }

    private fun launchEmailIntent() {
        val appVersion = String.format(getString(R.string.app_version, intent.getStringExtra(APP_VERSION_NAME)))
        val deviceOS = String.format(getString(R.string.device_os), Build.VERSION.RELEASE)
        val newline = "\n"
        val separator = "------------------------------"
        val body = "$appVersion$newline$deviceOS$newline$separator$newline$newline"

        val address = if (packageName.startsWith("com.simplemobiletools")) {
            MY_EMAIL
        } else {
            getString(R.string.my_fake_email)
        }

        val selectorIntent = Intent(ACTION_SENDTO)
            .setData("mailto:$address".toUri())
        val emailIntent = Intent(ACTION_SEND).apply {
            putExtra(EXTRA_EMAIL, arrayOf(address))
            putExtra(EXTRA_SUBJECT, appName)
            putExtra(EXTRA_TEXT, body)
            selector = selectorIntent
        }

        try {
            startActivity(emailIntent)
        } catch (e: ActivityNotFoundException) {
            val chooser = createChooser(emailIntent, getString(R.string.send_email))
            try {
                startActivity(chooser)
            } catch (e: Exception) {
                toast(R.string.no_email_client_found)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun onRateUsClick(
        showConfirmationAdvancedDialog: () -> Unit,
        showRateStarsDialog: () -> Unit
    ) {
        if (baseConfig.wasBeforeRateShown) {
            launchRateUsPrompt(showRateStarsDialog)
        } else {
            baseConfig.wasBeforeRateShown = true
            showConfirmationAdvancedDialog()
        }
    }

    private fun launchRateUsPrompt(
        showRateStarsDialog: () -> Unit
    ) {
        if (baseConfig.wasAppRated) {
            redirectToRateUs()
        } else {
            showRateStarsDialog()
        }
    }

    private fun onInviteClick() {
        val text = String.format(getString(R.string.share_text), appName, RELEASE_URL)
        Intent().apply {
            action = ACTION_SEND
            putExtra(EXTRA_SUBJECT, appName)
            putExtra(EXTRA_TEXT, text)
            type = "text/plain"
            startActivity(createChooser(this, getString(R.string.invite_via)))
        }
    }

    private fun onContributorsClick() {
        val intent = Intent(applicationContext, ContributorsActivity::class.java)
        startActivity(intent)
    }


    private fun onDonateClick() {
        launchViewIntent(getString(R.string.donate_url))
    }

    private fun onFacebookClick() {
        var link = "https://www.facebook.com/simplemobiletools"
        try {
            packageManager.getPackageInfo("com.facebook.katana", 0)
            link = "fb://page/150270895341774"
        } catch (ignored: Exception) {
        }

        launchViewIntent(link)
    }

    private fun onGithubClick() {
        launchViewIntent("https://github.com/SimpleMobileTools")
    }

    private fun onRedditClick() {
        launchViewIntent("https://www.reddit.com/r/SimpleMobileTools")
    }


    private fun onTelegramClick() {
        launchViewIntent("https://t.me/SimpleMobileTools")
    }


    private fun onWebsiteClick() {
        launchViewIntent("https://simplemobiletools.com/")
    }

    private fun onPrivacyPolicyClick() {
        val appId = baseConfig.appId.removeSuffix(".debug").removeSuffix(".pro").removePrefix("com.simplemobiletools.")
        val url = "https://simplemobiletools.com/privacy/$appId.txt"
        launchViewIntent(url)
    }

    private fun onLicenseClick() {
        Intent(applicationContext, LicenseActivity::class.java).apply {
            putExtra(APP_ICON_IDS, intent.getIntegerArrayListExtra(APP_ICON_IDS) ?: ArrayList<String>())
            putExtra(APP_LAUNCHER_NAME, intent.getStringExtra(APP_LAUNCHER_NAME) ?: "")
            putExtra(APP_LICENSES, intent.getLongExtra(APP_LICENSES, 0))
            startActivity(this)
        }
    }

    private fun onVersionClick() {
        launchViewIntent(RELEASE_URL)
    }

    private fun onAppIconClick() {
        launchViewIntent("https://www.flaticon.com/authors/freepik")
    }
}

private val startingTitlePadding = Modifier.padding(start = 60.dp)

@Composable
internal fun AboutScreen(
    goBack: () -> Unit,
    helpUsSection: @Composable () -> Unit,
    aboutSection: @Composable () -> Unit,
    socialSection: @Composable () -> Unit,
    otherSection: @Composable () -> Unit,
) {
    SimpleColumnScaffold(title = stringResource(id = R.string.about), goBack = goBack) {
        aboutSection()
        helpUsSection()
        socialSection()
        otherSection()
        SettingsListItem(text = stringResource(id = R.string.about_footer))
    }
}

@Composable
internal fun HelpUsSection(
    onRateUsClick: () -> Unit,
    onInviteClick: () -> Unit,
    onContributorsClick: () -> Unit,
    showRateUs: Boolean,
    showInvite: Boolean,
    showDonate: Boolean,
    onDonateClick: () -> Unit,
) {
    SettingsGroup(title = {
        SettingsTitleTextComponent(text = stringResource(id = R.string.help_us), modifier = startingTitlePadding)
    }) {
        if (showRateUs) {
            TwoLinerTextItem(text = stringResource(id = R.string.rate_us), icon = R.drawable.ic_star_vector, click = onRateUsClick)
        }
        if (showInvite) {
            TwoLinerTextItem(text = stringResource(id = R.string.invite_friends), icon = R.drawable.ic_add_person_vector, click = onInviteClick)
        }
        TwoLinerTextItem(
            click = onContributorsClick,
            text = stringResource(id = R.string.contributors),
            icon = R.drawable.ic_face_vector
        )
        if (showDonate) {
            TwoLinerTextItem(
                click = onDonateClick,
                text = stringResource(id = R.string.donate),
                icon = R.drawable.ic_dollar_vector
            )
        }
        SettingsHorizontalDivider()
    }
}

@Composable
internal fun OtherSection(
    showMoreApps: Boolean,
    onMoreAppsClick: () -> Unit,
    onWebsiteClick: () -> Unit,
    showWebsite: Boolean,
    showPrivacyPolicy: Boolean,
    onPrivacyPolicyClick: () -> Unit,
    onLicenseClick: () -> Unit,
    version: String,
    onVersionClick: () -> Unit,
    onAppIconClick: () -> Unit,
) {
    SettingsGroup(title = {
        SettingsTitleTextComponent(text = stringResource(id = R.string.other), modifier = startingTitlePadding)
    }) {
        if (showMoreApps) {
            TwoLinerTextItem(
                click = onMoreAppsClick,
                text = stringResource(id = R.string.more_apps_from_us),
                icon = R.drawable.ic_heart_vector
            )
        }
        if (showWebsite) {
            TwoLinerTextItem(
                click = onWebsiteClick,
                text = stringResource(id = R.string.website),
                icon = R.drawable.ic_link_vector
            )
        }
        if (showPrivacyPolicy) {
            TwoLinerTextItem(
                click = onPrivacyPolicyClick,
                text = stringResource(id = R.string.privacy_policy),
                icon = R.drawable.ic_unhide_vector
            )
        }
        TwoLinerTextItem(
            click = onLicenseClick,
            text = stringResource(id = R.string.third_party_licences),
            icon = R.drawable.ic_article_vector
        )
        TwoLinerTextItem(
            click = onAppIconClick,
            text = "Trendy icons created by Freepik - Flaticon",
            icon = R.drawable.ic_info_vector
        )
        TwoLinerTextItem(
            click = onVersionClick,
            text = version,
            icon = R.drawable.ic_info_vector
        )
        SettingsHorizontalDivider()
    }
}


@Composable
internal fun AboutSection(
    setupFAQ: Boolean,
    onFAQClick: () -> Unit,
    onEmailClick: () -> Unit,
    onYoutubeTimestampClick: () -> Unit
) {
    SettingsGroup(title = {
        SettingsTitleTextComponent(text = stringResource(id = R.string.support), modifier = startingTitlePadding)
    }) {
        if (setupFAQ) {
            TwoLinerTextItem(
                click = onFAQClick,
                text = stringResource(id = R.string.frequently_asked_questions),
                icon = R.drawable.ic_question_mark_vector
            )
        }
        // Youtube timestamp text
        TwoLinerTextItem(
            click = onYoutubeTimestampClick,
            text = stringResource(id = com.simplemobiletools.musicplayer.R.string.youtube_timestamp_player),
            icon = R.drawable.ic_info_vector
        )
        TwoLinerTextItem(
            click = onEmailClick,
            text = MY_EMAIL,
            icon = R.drawable.ic_mail_vector
        )
        SettingsHorizontalDivider()
    }
}

@Composable
internal fun SocialSection(
    onFacebookClick: () -> Unit,
    onGithubClick: () -> Unit,
    onRedditClick: () -> Unit,
    onTelegramClick: () -> Unit
) {
    SettingsGroup(title = {
        SettingsTitleTextComponent(text = stringResource(id = R.string.social), modifier = startingTitlePadding)
    }) {
        SocialText(
            click = onFacebookClick,
            text = stringResource(id = R.string.facebook),
            icon = R.drawable.ic_facebook_vector,
        )
        SocialText(
            click = onGithubClick,
            text = stringResource(id = R.string.github),
            icon = R.drawable.ic_github_vector,
            tint = SimpleTheme.colorScheme.onSurface
        )
        SocialText(
            click = onRedditClick,
            text = stringResource(id = R.string.reddit),
            icon = R.drawable.ic_reddit_vector,
        )
        SocialText(
            click = onTelegramClick,
            text = stringResource(id = R.string.telegram),
            icon = R.drawable.ic_telegram_vector,
        )
        SettingsHorizontalDivider()
    }
}

@Composable
internal fun SocialText(
    text: String,
    icon: Int,
    tint: Color? = null,
    click: () -> Unit
) {
    SettingsListItem(
        click = click,
        text = text,
        icon = icon,
        isImage = true,
        tint = tint,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun TwoLinerTextItem(text: String, icon: Int, click: () -> Unit) {
    SettingsListItem(
        tint = SimpleTheme.colorScheme.onSurface,
        click = click,
        text = text,
        icon = icon,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@MyDevices
@Composable
private fun AboutScreenPreview() {
    AppThemeSurface {
        AboutScreen(
            goBack = {},
            helpUsSection = {
                HelpUsSection(
                    onRateUsClick = {},
                    onInviteClick = {},
                    onContributorsClick = {},
                    showRateUs = true,
                    showInvite = true,
                    showDonate = true,
                    onDonateClick = {}
                )
            },
            aboutSection = {
                AboutSection(setupFAQ = true, onFAQClick = {}, onEmailClick = {}, onYoutubeTimestampClick = {})
            },
            socialSection = {
                SocialSection(
                    onFacebookClick = {},
                    onGithubClick = {},
                    onRedditClick = {},
                    onTelegramClick = {}
                )
            }
        ) {
            OtherSection(
                showMoreApps = true,
                onMoreAppsClick = {},
                onWebsiteClick = {},
                showWebsite = true,
                showPrivacyPolicy = true,
                onPrivacyPolicyClick = {},
                onLicenseClick = {},
                version = "5.0.4",
                onVersionClick = {},
                onAppIconClick = {},
            )
        }
    }
}


fun SimpleActivity.startMyAboutActivity(appNameId: Int, licenseMask: Long, versionName: String, faqItems: ArrayList<FAQItem>, showFAQBeforeMail: Boolean) {
    hideKeyboard()
    Intent(applicationContext, MyAboutActivity::class.java).apply {
        putExtra(APP_ICON_IDS, getAppIconIDs())
        putExtra(APP_LAUNCHER_NAME, getAppLauncherName())
        putExtra(APP_NAME, getString(appNameId))
        putExtra(APP_LICENSES, licenseMask)
        putExtra(APP_VERSION_NAME, versionName)
        putExtra(APP_FAQ, faqItems)
        putExtra(SHOW_FAQ_BEFORE_MAIL, showFAQBeforeMail)
        startActivity(this)
    }
}
