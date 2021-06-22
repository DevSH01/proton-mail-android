/*
 * Copyright (c) 2020 Proton Technologies AG
 *
 * This file is part of ProtonMail.
 *
 * ProtonMail is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonMail is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonMail. If not, see https://www.gnu.org/licenses/.
 */
package ch.protonmail.android.activities.settings

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
import android.provider.Settings.EXTRA_APP_PACKAGE
import android.provider.Settings.EXTRA_CHANNEL_ID
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.protonmail.android.R
import ch.protonmail.android.activities.AccountSettingsActivity
import ch.protonmail.android.activities.BaseConnectivityActivity
import ch.protonmail.android.activities.DefaultAddressActivity
import ch.protonmail.android.activities.EXTRA_SETTINGS_ITEM_TYPE
import ch.protonmail.android.activities.EditSettingsItemActivity
import ch.protonmail.android.activities.SettingsItem
import ch.protonmail.android.activities.labelsManager.EXTRA_MANAGE_FOLDERS
import ch.protonmail.android.activities.labelsManager.LabelsManagerActivity
import ch.protonmail.android.activities.settings.SettingsEnum.*
import ch.protonmail.android.adapters.SettingsAdapter
import ch.protonmail.android.core.Constants
import ch.protonmail.android.core.UserManager
import ch.protonmail.android.data.local.AttachmentMetadataDao
import ch.protonmail.android.data.local.ContactDao
import ch.protonmail.android.data.local.ContactDatabase
import ch.protonmail.android.data.local.CounterDao
import ch.protonmail.android.data.local.CounterDatabase
import ch.protonmail.android.data.local.MessageDao
import ch.protonmail.android.data.local.MessageDatabase
import ch.protonmail.android.data.local.NotificationDao
import ch.protonmail.android.data.local.NotificationDatabase
import ch.protonmail.android.data.local.PendingActionDao
import ch.protonmail.android.data.local.PendingActionDatabase
import ch.protonmail.android.domain.entity.user.Address
import ch.protonmail.android.domain.entity.user.User
import ch.protonmail.android.events.FetchLabelsEvent
import ch.protonmail.android.jobs.FetchByLocationJob
import ch.protonmail.android.mailbox.data.local.ConversationDao
import ch.protonmail.android.servers.notification.CHANNEL_ID_EMAIL
import ch.protonmail.android.settings.pin.PinSettingsActivity
import ch.protonmail.android.settings.presentation.AccountTypeActivity
import ch.protonmail.android.settings.presentation.AttachmentStorageActivity
import ch.protonmail.android.settings.presentation.DisplayNameAndSignatureFragment
import ch.protonmail.android.settings.presentation.EXTRA_SETTINGS_ATTACHMENT_STORAGE_VALUE
import ch.protonmail.android.settings.presentation.SettingsDividerItemDecoration
import ch.protonmail.android.settings.presentation.SnoozeNotificationsActivity
import ch.protonmail.android.settings.presentation.SwipeSettingFragment
import ch.protonmail.android.uiModel.SettingsItemUiModel
import ch.protonmail.android.usecase.fetch.LaunchInitialDataFetch
import ch.protonmail.android.utils.AppUtil
import ch.protonmail.android.utils.CustomLocale
import ch.protonmail.android.utils.PREF_CUSTOM_APP_LANGUAGE
import ch.protonmail.android.utils.UiUtil
import ch.protonmail.android.utils.extensions.app
import ch.protonmail.android.utils.extensions.showToast
import ch.protonmail.android.utils.startMailboxActivity
import ch.protonmail.android.utils.ui.dialogs.DialogUtils.Companion.showInfoDialog
import ch.protonmail.android.viewmodel.ConnectivityBaseViewModel
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.android.synthetic.main.settings_item_layout.view.*
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import ch.protonmail.android.api.models.User as LegacyUser

// region constants
const val EXTRA_CURRENT_MAILBOX_LOCATION = "Extra_Current_Mailbox_Location"
const val EXTRA_CURRENT_MAILBOX_LABEL_ID = "Extra_Current_Mailbox_Label_ID"
private const val EXTRA_CURRENT_ACTION = "extra.current.action"
// endregion

abstract class BaseSettingsActivity : BaseConnectivityActivity() {

    val viewModel: ConnectivityBaseViewModel by viewModels()

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var launchInitialDataFetch: LaunchInitialDataFetch

    @Inject
    lateinit var attachmentMetadataDao: AttachmentMetadataDao

    // region views
    private val toolbar by lazy { findViewById<Toolbar>(R.id.toolbar) }
    // endregion

    private val settingsAdapter = SettingsAdapter()

    var settingsUiList: List<SettingsItemUiModel> = ArrayList()

    var contactDao: ContactDao? = null
    var messageDao: MessageDao? = null
    var conversationDao: ConversationDao? = null
    private var searchDatabase: MessageDao? = null
    private var notificationDao: NotificationDao? = null
    var counterDao: CounterDao? = null
    var pendingActionDao: PendingActionDao? = null
    var preferences: SharedPreferences? = null

    private var mMailboxLocation: Constants.MessageLocationType = Constants.MessageLocationType.INBOX
    private var mLabelId: String? = null
    var mBackgroundSyncValue: Boolean = false
    var mAttachmentStorageValue: Int = 0
    var mAutoDownloadGcmMessages: Boolean = false
    var mPinValue: Boolean = false
    var mNotificationOptionValue: Int = 0
    lateinit var selectedAddress: Address
    var mDisplayName: String = ""
    var mSignature: String = ""

    @Deprecated("Use new User model", ReplaceWith("user"))
    lateinit var legacyUser: LegacyUser
    lateinit var user: User

    private var canClick = AtomicBoolean(true)

    init {
        settingsAdapter.onItemClick = { settingItem ->

            if (settingItem.isSection.not() &&
                (
                    settingItem.settingType == SettingsItemUiModel.SettingsItemTypeEnum.DRILL_DOWN ||
                        settingItem.settingType == SettingsItemUiModel.SettingsItemTypeEnum.BUTTON
                    )
            ) {
                selectItem(settingItem.settingId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        legacyUser = userManager.requireCurrentLegacyUser()
        user = legacyUser.toNewUser()
        val userId = user.id

        contactDao = ContactDatabase.getInstance(applicationContext, userId).getDao()
        messageDao = MessageDatabase.getInstance(applicationContext, userId).getDao()
        conversationDao = MessageDatabase.getInstance(applicationContext, userId).getConversationDao()
        notificationDao = NotificationDatabase.getInstance(applicationContext, userId).getDao()
        counterDao = CounterDatabase.getInstance(applicationContext, userId).getDao()
        pendingActionDao = PendingActionDatabase.getInstance(applicationContext, userId).getDao()
        preferences = userManager.preferencesFor(userId)

        mMailboxLocation = Constants.MessageLocationType
            .fromInt(intent.getIntExtra(EXTRA_CURRENT_MAILBOX_LOCATION, 0))
        mLabelId = intent.getStringExtra(EXTRA_CURRENT_MAILBOX_LABEL_ID)

        fetchOrganizationData()

        val primaryAddress = checkNotNull(user.addresses.primary)
        mDisplayName = primaryAddress.displayName?.s
            ?: primaryAddress.email.s

        viewModel.hasConnectivity.observe(this, ::onConnectivityEvent)
    }

    override fun onStart() {
        super.onStart()
        app.bus.register(this)
    }

    override fun onResume() {
        super.onResume()
        legacyUser = userManager.requireCurrentLegacyUser()
        user = legacyUser.toNewUser()
        settingsAdapter.notifyDataSetChanged()
        viewModel.checkConnectivity()
    }

    override fun onStop() {
        super.onStop()
        app.bus.unregister(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.save_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        menuInflater.inflate(R.menu.save_menu, menu)
        menu.findItem(R.id.save).isVisible = false
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (supportFragmentManager.fragments.filterIsInstance<DisplayNameAndSignatureFragment>().isNotEmpty()) {
                    false
                } else {
                    onBackPressed()
                    true
                }
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            toolbar?.title = title
            setSupportActionBar(toolbar)
            UiUtil.hideKeyboard(this)
        } else {
            saveLastInteraction()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        setSupportActionBar(toolbar)
    }

    abstract fun renderViews()

    private fun showCustomLocaleDialog() {
        val selectedLanguage = preferences!!.getString(PREF_CUSTOM_APP_LANGUAGE, "")
        val languageValues = resources.getStringArray(R.array.custom_language_values)
        val selectedLanguageIndex = languageValues.indexOfFirst { it == selectedLanguage }

        AlertDialog.Builder(this)
            .setTitle(R.string.custom_language_dialog_title)
            .setSingleChoiceItems(
                resources.getStringArray(R.array.custom_language_labels),
                selectedLanguageIndex
            ) { dialog, which ->

                val language = resources.getStringArray(R.array.custom_language_values)[which]
                CustomLocale.setLanguage(this@BaseSettingsActivity, language)

                dialog.dismiss()
                startMailboxActivity()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .show()
    }

    private fun showSortAliasDialog() {
        val defaultAddressIntent = AppUtil.decorInAppIntent(Intent(this, DefaultAddressActivity::class.java))
        startActivity(defaultAddressIntent)
    }

    private fun selectItem(settingsId: String) {
        legacyUser = userManager.requireCurrentLegacyUser()
        user = legacyUser.toNewUser()
        when (valueOf(settingsId.toUpperCase())) {
            ACCOUNT -> {
                val accountSettingsIntent =
                    AppUtil.decorInAppIntent(Intent(this, AccountSettingsActivity::class.java))
                startActivity(accountSettingsIntent)
            }
            SUBSCRIPTION -> {
                val accountTypeIntent =
                    AppUtil.decorInAppIntent(Intent(this, AccountTypeActivity::class.java))
                startActivity(accountTypeIntent)
            }
            PASSWORD_MANAGEMENT,
            RECOVERY_EMAIL -> {
                showInfoDialog(
                    this,
                    "",
                    getString(R.string.info_for_missing_functionality)
                ) { unit: Unit -> unit }
                // TODO("startChangePasswordWorkflow")
                // TODO("startrecoverysetupworkflow")
            }
            DEFAULT_EMAIL -> {
                showSortAliasDialog()
            }
            DISPLAY_NAME_N_SIGNATURE -> {
                val displayAndSignatureFragment = DisplayNameAndSignatureFragment.newInstance(user, mJobManager)
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.zoom_in, 0, 0, R.anim.zoom_out)
                    .add(R.id.settings_fragment_container, displayAndSignatureFragment)
                    .addToBackStack(displayAndSignatureFragment.tag)
                    .commitAllowingStateLoss()
            }
            NOTIFICATION_SNOOZE -> {
                val notificationSnoozeIntent =
                    AppUtil.decorInAppIntent(Intent(this, SnoozeNotificationsActivity::class.java))
                startActivity(notificationSnoozeIntent)
            }
            PRIVACY -> {
                val privacyIntent =
                    AppUtil.decorInAppIntent(Intent(this, EditSettingsItemActivity::class.java))
                privacyIntent.putExtra(EXTRA_SETTINGS_ITEM_TYPE, SettingsItem.PRIVACY)
                startActivity(privacyIntent)
            }
            AUTO_DOWNLOAD_MESSAGES -> {
                val gcmAutoDownloadIntent = Intent(this, EditSettingsItemActivity::class.java)
                gcmAutoDownloadIntent.putExtra(EXTRA_SETTINGS_ITEM_TYPE, SettingsItem.AUTO_DOWNLOAD_MESSAGES)
                startActivity(AppUtil.decorInAppIntent(gcmAutoDownloadIntent))
            }
            BACKGROUND_REFRESH -> {
                val backgroundSyncIntent = Intent(this, EditSettingsItemActivity::class.java)
                backgroundSyncIntent.putExtra(EXTRA_SETTINGS_ITEM_TYPE, SettingsItem.BACKGROUND_SYNC)
                startActivity(AppUtil.decorInAppIntent(backgroundSyncIntent))
            }
            LABELS_N_FOLDERS -> {
                val labelsNFoldersIntent =
                    AppUtil.decorInAppIntent(Intent(this, EditSettingsItemActivity::class.java))
                labelsNFoldersIntent.putExtra(EXTRA_SETTINGS_ITEM_TYPE, SettingsItem.LABELS_AND_FOLDERS)
                startActivity(labelsNFoldersIntent)
            }
            LABELS_MANAGER -> {
                val labelsManagerIntent =
                    AppUtil.decorInAppIntent(Intent(this, LabelsManagerActivity::class.java))
                startActivity(labelsManagerIntent)
            }
            FOLDERS_MANAGER -> {
                val foldersManagerIntent = AppUtil.decorInAppIntent(
                    Intent(
                        this,
                        LabelsManagerActivity::class.java
                    )
                )
                foldersManagerIntent.putExtra(EXTRA_MANAGE_FOLDERS, true)
                startActivity(foldersManagerIntent)
            }
            SWIPING_GESTURE -> {
                val mailSettings = checkNotNull(userManager.getCurrentUserMailSettingsBlocking())
                val swipeFragment = SwipeSettingFragment.newInstance(mailSettings)
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.zoom_in, 0, 0, R.anim.zoom_out)
                    .add(R.id.settings_fragment_container, swipeFragment)
                    .addToBackStack(swipeFragment.tag)
                    .commitAllowingStateLoss()
            }
            LOCAL_STORAGE_LIMIT -> {
                val attachmentStorageIntent = Intent(this, AttachmentStorageActivity::class.java)
                attachmentStorageIntent.putExtra(
                    EXTRA_SETTINGS_ATTACHMENT_STORAGE_VALUE,
                    mAttachmentStorageValue
                )
                startActivity(AppUtil.decorInAppIntent(attachmentStorageIntent))
            }
            PUSH_NOTIFICATION -> {
                val privateNotificationsIntent =
                    AppUtil.decorInAppIntent(Intent(this, EditSettingsItemActivity::class.java))
                privateNotificationsIntent.putExtra(EXTRA_SETTINGS_ITEM_TYPE, SettingsItem.PUSH_NOTIFICATIONS)
                startActivity(privateNotificationsIntent)
            }
            NOTIFICATION_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent(ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    intent.putExtra(EXTRA_CHANNEL_ID, CHANNEL_ID_EMAIL)
                    intent.putExtra(EXTRA_APP_PACKAGE, packageName)
                    startActivity(intent)
                } else {
                    mNotificationOptionValue = legacyUser.notificationSetting
                    val notificationSettingsIntent = Intent(this, NotificationSettingsActivity::class.java)
                    notificationSettingsIntent.putExtra(EXTRA_CURRENT_ACTION, mNotificationOptionValue)
                    startActivity(AppUtil.decorInAppIntent(notificationSettingsIntent))
                }
            }
            AUTO_LOCK -> {
                val pinManagerIntent = AppUtil.decorInAppIntent(Intent(this, PinSettingsActivity::class.java))
                startActivity(pinManagerIntent)
            }
            CONNECTIONS_VIA_THIRD_PARTIES -> {
                val allowThirdPartiesSecureConnectionsIntent =
                    AppUtil.decorInAppIntent(Intent(this, EditSettingsItemActivity::class.java))
                allowThirdPartiesSecureConnectionsIntent
                    .putExtra(EXTRA_SETTINGS_ITEM_TYPE, SettingsItem.CONNECTIONS_VIA_THIRD_PARTIES)
                startActivity(allowThirdPartiesSecureConnectionsIntent)
            }
            APP_LANGUAGE -> {
                showCustomLocaleDialog()
            }
            COMBINED_CONTACTS -> {
                val combinedContactsIntent = Intent(this, EditSettingsItemActivity::class.java)
                combinedContactsIntent.putExtra(EXTRA_SETTINGS_ITEM_TYPE, SettingsItem.COMBINED_CONTACTS)
                startActivity(AppUtil.decorInAppIntent(combinedContactsIntent))
            }
            APP_LOCAL_CACHE -> {
                showToast(R.string.processing_request, gravity = Gravity.CENTER)
                if (canClick.getAndSet(false)) {
                    AppUtil.clearStorage(
                        applicationContext,
                        userManager.requireCurrentUserId(),
                        contactDao,
                        messageDao,
                        searchDatabase,
                        conversationDao,
                        notificationDao,
                        counterDao,
                        attachmentMetadataDao,
                        pendingActionDao,
                        true
                    )
                    launchInitialDataFetch(userManager.requireCurrentUserId())
                    mJobManager.addJobInBackground(
                        FetchByLocationJob(
                            mMailboxLocation,
                            mLabelId,
                            true,
                            null,
                            false
                        )
                    )
                }
            }
            else -> { /* ignored */
            }
        }
    }

    protected fun setUpSettingsItems(jsonId: Int) {
        val jsonSettingsListResponse = resources.openRawResource(jsonId).bufferedReader().use { it.readText() }

        val gson = Gson()
        settingsUiList = gson.fromJson(jsonSettingsListResponse, Array<SettingsItemUiModel>::class.java).asList()
        settingsAdapter.items = settingsUiList
        settingsRecyclerView.layoutManager = LinearLayoutManager(this@BaseSettingsActivity)
        settingsRecyclerView.adapter = settingsAdapter

        settingsRecyclerView.setUpItemDecorations(SettingsDividerItemDecoration(this, DividerItemDecoration.VERTICAL))
    }

    protected fun setUpSettingsItems(settingsList: List<SettingsItemUiModel>) {
        settingsUiList = settingsList
        settingsAdapter.items = settingsUiList
        settingsRecyclerView.layoutManager = LinearLayoutManager(this@BaseSettingsActivity)
        settingsRecyclerView.adapter = settingsAdapter

        settingsRecyclerView.setUpItemDecorations(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
    }

    protected fun refreshSettings(settingsList: List<SettingsItemUiModel>) {
        settingsAdapter.items = settingsList
    }

    protected fun notifySettingsChanged() {
        settingsAdapter.notifyDataSetChanged()
    }

    protected fun setToggleListener(settingType: SettingsEnum, listener: ((View, Boolean) -> Unit)?) {
        settingsAdapter.items
            .find { it.settingId == settingType.name.toLowerCase(Locale.ENGLISH) }
            ?.apply { toggleListener = listener }
    }

    protected fun setValue(settingType: SettingsEnum, settingValueNew: String) {
        settingsAdapter.items
            .find { it.settingId == settingType.name.toLowerCase(Locale.ENGLISH) }
            ?.apply { settingValue = settingValueNew }
    }

    protected fun setIconVisibility(settingType: SettingsEnum, visibility: Int) {
        settingsAdapter.items
            .find { it.settingId == settingType.name.toLowerCase(Locale.ENGLISH) }
            ?.apply { iconVisibility = visibility }
    }

    /**
     * Turns the value of setting with [settingType] ON or OFF.
     */
    protected fun setEnabled(settingType: SettingsEnum, settingValueEnabled: Boolean) {
        settingsAdapter.items
            .find { it.settingId == settingType.name.toLowerCase(Locale.ENGLISH) }
            ?.apply { enabled = settingValueEnabled }
    }

    /**
     * Sets the setting with [settingType] to locked, so the user can't change.
     * Usually if the account is on a free plan.
     */
    protected fun setSettingDisabled(settingType: SettingsEnum, settingDisabledNew: Boolean, description: String) {
        settingsAdapter.items.find { it.settingId == settingType.name.toLowerCase(Locale.ENGLISH) }?.apply {
            settingDisabled = settingDisabledNew
            settingsDescription = description
        }
    }

    protected fun setHeader(settingType: SettingsEnum, settingHeaderNew: String = "") {
        settingsAdapter.items.find { it.settingId == settingType.name.toLowerCase(Locale.ENGLISH) }?.apply {
            settingHeader = if (settingHeaderNew.isNotEmpty()) {
                settingHeaderNew
            } else {
                valueOf(settingType.name).getHeader(this@BaseSettingsActivity)
            }
            settingsHint = valueOf(settingType.name).getHint(this@BaseSettingsActivity)
        }
    }

    private fun onConnectivityEvent(connectivity: Constants.ConnectionState) {
        Timber.v("onConnectivityEvent hasConnection:${connectivity.name}")
        if (connectivity != Constants.ConnectionState.CONNECTED) {
            networkSnackBarUtil.getNoConnectionSnackBar(
                mSnackLayout,
                mUserManager.requireCurrentLegacyUser(),
                this,
                { onConnectivityCheckRetry() },
                isOffline = connectivity == Constants.ConnectionState.NO_INTERNET
            ).show()
        } else {
            networkSnackBarUtil.hideAllSnackBars()
        }
    }

    private fun onConnectivityCheckRetry() {
        networkSnackBarUtil.getCheckingConnectionSnackBar(
            mSnackLayout
        ).show()

        viewModel.checkConnectivityDelayed()
    }

    open fun onLabelsLoadedEvent(event: FetchLabelsEvent) {
        if (!canClick.get()) {
            showToast(R.string.cache_cleared, gravity = Gravity.CENTER)
        }
        canClick.set(true)
    }

    private fun RecyclerView.setUpItemDecorations(itemDecoration: DividerItemDecoration) {
        itemDecoration.setDrawable(getDrawable(R.drawable.list_divider)!!)
        addItemDecoration(itemDecoration)
    }
}
