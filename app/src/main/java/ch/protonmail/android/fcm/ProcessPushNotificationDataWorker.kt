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

package ch.protonmail.android.fcm

import android.content.Context
import androidx.hilt.Assisted
import androidx.hilt.work.WorkerInject
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ch.protonmail.android.api.models.DatabaseProvider
import ch.protonmail.android.api.models.User
import ch.protonmail.android.api.models.room.notifications.Notification
import ch.protonmail.android.api.segments.event.AlarmReceiver
import ch.protonmail.android.core.QueueNetworkUtil
import ch.protonmail.android.core.UserManager
import ch.protonmail.android.crypto.UserCrypto
import ch.protonmail.android.domain.entity.Name
import ch.protonmail.android.fcm.models.PushNotification
import ch.protonmail.android.fcm.models.PushNotificationData
import ch.protonmail.android.repository.MessageRepository
import ch.protonmail.android.servers.notification.NotificationServer
import ch.protonmail.android.utils.AppUtil
import me.proton.core.util.kotlin.EMPTY_STRING
import me.proton.core.util.kotlin.deserialize
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject

const val KEY_PUSH_NOTIFICATION_UID = "UID"
const val KEY_PUSH_NOTIFICATION_ENCRYPTED_MESSAGE = "encryptedMessage"
const val KEY_PROCESS_PUSH_NOTIFICATION_DATA_ERROR = "ProcessPushNotificationDataError"

/**
 * A worker that is responsible for processing the data payload of the received FCM push notifications.
 */

class ProcessPushNotificationDataWorker @WorkerInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val notificationServer: NotificationServer,
    private val alarmReceiver: AlarmReceiver,
    private val queueNetworkUtil: QueueNetworkUtil,
    private val userManager: UserManager,
    private val databaseProvider: DatabaseProvider,
    private val messageRepository: MessageRepository
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_PUSH_NOTIFICATION_UID)
        val encryptedMessage = inputData.getString(KEY_PUSH_NOTIFICATION_ENCRYPTED_MESSAGE)

        if (sessionId.isNullOrEmpty() || encryptedMessage.isNullOrEmpty()) {
            return Result.failure(
                workDataOf(KEY_PROCESS_PUSH_NOTIFICATION_DATA_ERROR to "Input data is missing")
            )
        }

        if (!AppUtil.isAppInBackground()) {
            alarmReceiver.setAlarm(applicationContext, true)
        }

        queueNetworkUtil.setCurrentlyHasConnectivity()

        val notificationUsername = userManager.getUsernameBySessionId(sessionId)
        if (notificationUsername.isNullOrEmpty()) {
            // we do not show notifications for unknown/inactive users
            return Result.failure(workDataOf(KEY_PROCESS_PUSH_NOTIFICATION_DATA_ERROR to "User is unknown or inactive"))
        }

        val user = userManager.getUser(notificationUsername)
        if (!user.isBackgroundSync) {
            // we do not show notifications for users who have disabled background sync
            return Result.failure(workDataOf(KEY_PROCESS_PUSH_NOTIFICATION_DATA_ERROR to "Background sync is disabled"))
        }

        var pushNotification: PushNotification? = null
        var pushNotificationData: PushNotificationData? = null
        try {
            val userCrypto = UserCrypto(userManager, userManager.openPgp, Name(notificationUsername))
            val textDecryptionResult = userCrypto.decryptMessage(encryptedMessage)
            val decryptedData = textDecryptionResult.decryptedData
            pushNotification = decryptedData.deserialize(PushNotification.serializer())
            pushNotificationData = pushNotification.data
        } catch (e: Exception) {
            Timber.e(e, "Error with decryption or deserialization of the notification data")
        }

        if (pushNotification == null || pushNotificationData == null) {
            return Result.failure(
                workDataOf(KEY_PROCESS_PUSH_NOTIFICATION_DATA_ERROR to "Decryption or deserialization error")
            )
        }

        val messageId = pushNotificationData.messageId
        val notificationBody = pushNotificationData.body
        val notificationSender = pushNotificationData.sender
        val sender = notificationSender?.let {
            it.senderName.ifEmpty { it.senderAddress }
        } ?: EMPTY_STRING

        val primaryUser = userManager.username == notificationUsername
        val isQuickSnoozeEnabled = userManager.isSnoozeQuickEnabled()
        val isScheduledSnoozeEnabled = userManager.isSnoozeScheduledEnabled()

        if (!isQuickSnoozeEnabled && (!isScheduledSnoozeEnabled || !shouldSuppressNotification())) {
            sendNotification(user, messageId, notificationBody, sender, primaryUser)
        }

        return Result.success()
    }

    private suspend fun sendNotification(
        user: User,
        messageId: String,
        notificationBody: String,
        sender: String,
        primaryUser: Boolean
    ) {

        // Insert current Notification in Database
        val notificationsDatabase = databaseProvider.provideNotificationsDao(user.username)
        val notification = Notification(messageId, sender, notificationBody)
        val notifications = notificationsDatabase.insertNewNotificationAndReturnAll(notification)
        val message = messageRepository.getMessage(messageId, user.username)

        if (notifications.size > 1) {
            notificationServer.notifyMultipleUnreadEmail(userManager, user, notifications)
        } else {
            notificationServer.notifySingleNewEmail(
                userManager, user, message, messageId, notificationBody, sender, primaryUser
            )
        }
    }

    private fun shouldSuppressNotification(): Boolean {
        val rightNow = Calendar.getInstance()
        return userManager.snoozeSettings?.shouldSuppressNotification(rightNow) ?: false
    }

    class Enqueuer @Inject constructor(
        private val workManager: WorkManager
    ) {

        operator fun invoke(pushNotificationData: Map<String, String>) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = Data.Builder()
                .putAll(pushNotificationData)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ProcessPushNotificationDataWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            workManager.enqueue(workRequest)
        }
    }
}
