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
package ch.protonmail.android.mailbox.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import ch.protonmail.android.activities.messageDetails.repository.MessageDetailsRepository
import ch.protonmail.android.api.NetworkConfigurator
import ch.protonmail.android.api.services.MessagesService
import ch.protonmail.android.api.utils.ApplyRemoveLabels
import ch.protonmail.android.core.Constants
import ch.protonmail.android.core.Constants.MessageLocationType.*
import ch.protonmail.android.core.UserManager
import ch.protonmail.android.data.ContactsRepository
import ch.protonmail.android.data.local.model.ContactEmail
import ch.protonmail.android.data.local.model.Label
import ch.protonmail.android.data.local.model.Message
import ch.protonmail.android.domain.entity.Id
import ch.protonmail.android.jobs.ApplyLabelJob
import ch.protonmail.android.jobs.FetchByLocationJob
import ch.protonmail.android.jobs.RemoveLabelJob
import ch.protonmail.android.usecase.VerifyConnection
import ch.protonmail.android.usecase.delete.DeleteMessage
import ch.protonmail.android.utils.Event
import ch.protonmail.android.utils.UserUtils
import ch.protonmail.android.viewmodel.ConnectivityBaseViewModel
import com.birbit.android.jobqueue.JobManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.core.util.kotlin.DispatcherProvider
import java.util.ArrayList
import java.util.HashMap
import javax.inject.Inject
import kotlin.collections.set

const val FLOW_START_ACTIVITY = 1
const val FLOW_USED_SPACE_CHANGED = 2
const val FLOW_TRY_COMPOSE = 3

@HiltViewModel
class MailboxViewModel @Inject constructor(
    private val messageDetailsRepository: MessageDetailsRepository,
    private val userManager: UserManager,
    private val jobManager: JobManager,
    private val deleteMessage: DeleteMessage,
    private val dispatchers: DispatcherProvider,
    private val contactsRepository: ContactsRepository,
    verifyConnection: VerifyConnection,
    networkConfigurator: NetworkConfigurator,
    private val messageServiceScheduler: MessagesService.Scheduler
) : ConnectivityBaseViewModel(verifyConnection, networkConfigurator) {

    var pendingSendsLiveData = messageDetailsRepository.findAllPendingSendsAsync()
    var pendingUploadsLiveData = messageDetailsRepository.findAllPendingUploadsAsync()

    private val _manageLimitReachedWarning = MutableLiveData<Event<Boolean>>()
    private val _manageLimitApproachingWarning = MutableLiveData<Event<Boolean>>()
    private val _manageLimitBelowCritical = MutableLiveData<Event<Boolean>>()
    private val _manageLimitReachedWarningOnTryCompose = MutableLiveData<Event<Boolean>>()
    private val _toastMessageMaxLabelsReached = MutableLiveData<Event<MaxLabelsReached>>()
    private val _hasSuccessfullyDeletedMessages = MutableLiveData<Boolean>()

    lateinit var userId: Id

    val manageLimitReachedWarning: LiveData<Event<Boolean>>
        get() = _manageLimitReachedWarning
    val manageLimitApproachingWarning: LiveData<Event<Boolean>>
        get() = _manageLimitApproachingWarning
    val manageLimitBelowCritical: LiveData<Event<Boolean>>
        get() = _manageLimitBelowCritical
    val manageLimitReachedWarningOnTryCompose: LiveData<Event<Boolean>>
        get() = _manageLimitReachedWarningOnTryCompose
    val toastMessageMaxLabelsReached: LiveData<Event<MaxLabelsReached>>
        get() = _toastMessageMaxLabelsReached

    val hasSuccessfullyDeletedMessages: LiveData<Boolean>
        get() = _hasSuccessfullyDeletedMessages

    fun reloadDependenciesForUser() {
        pendingSendsLiveData = messageDetailsRepository.findAllPendingSendsAsync()
        pendingUploadsLiveData = messageDetailsRepository.findAllPendingUploadsAsync()
    }

    fun usedSpaceActionEvent(limitReachedFlow: Int) {
        viewModelScope.launch {
            userManager.setShowStorageLimitReached(true)
            val user = userManager.currentUser
                ?: return@launch
            val (usedSpace, totalSpace) = with(user.dedicatedSpace) { used.l.toLong() to total.l.toLong() }
            val userMaxSpace = if (totalSpace == 0L) Long.MAX_VALUE else totalSpace
            val percentageUsed = usedSpace * 100L / userMaxSpace
            val limitReached = percentageUsed >= 100
            val limitApproaching = percentageUsed >= Constants.STORAGE_LIMIT_WARNING_PERCENTAGE

            when (limitReachedFlow) {
                FLOW_START_ACTIVITY -> {
                    if (limitReached) {
                        _manageLimitReachedWarning.postValue(Event(limitReached))
                    } else if (limitApproaching) {
                        _manageLimitApproachingWarning.postValue(Event(limitApproaching))
                    }
                }
                FLOW_USED_SPACE_CHANGED -> {
                    if (limitReached) {
                        _manageLimitReachedWarning.postValue(Event(limitReached))
                    } else if (limitApproaching) {
                        _manageLimitApproachingWarning.postValue(Event(limitApproaching))
                    } else {
                        _manageLimitBelowCritical.postValue(Event(true))
                    }
                }
                FLOW_TRY_COMPOSE -> {
                    _manageLimitReachedWarningOnTryCompose.postValue(Event(limitReached))
                }
            }
        }
    }

    fun processLabels(messageIds: List<String>, checkedLabelIds: List<String>, unchangedLabels: List<String>) {
        val iterator = messageIds.iterator()

        val labelsToApplyMap = HashMap<String, MutableList<String>>()
        val labelsToRemoveMap = HashMap<String, MutableList<String>>()
        var result: Pair<Map<String, List<String>>, Map<String, List<String>>>? = null

        viewModelScope.launch {
            withContext(dispatchers.Comp) {
                while (iterator.hasNext()) {
                    val messageId = iterator.next()
                    val message = messageDetailsRepository.findMessageById(messageId).first()

                    if (message != null) {
                        val currentLabelsIds = message.labelIDsNotIncludingLocations
                        val labels = getAllLabelsByIds(currentLabelsIds)
                        val applyRemoveLabels = resolveMessageLabels(
                            message, ArrayList(checkedLabelIds),
                            ArrayList<String>(unchangedLabels),
                            labels
                        )
                        val apply = applyRemoveLabels?.labelsToApply
                        val remove = applyRemoveLabels?.labelsToRemove
                        apply?.forEach {
                            var labelsToApply: MutableList<String>? = labelsToApplyMap[it]
                            if (labelsToApply == null) {
                                labelsToApply = ArrayList()
                            }
                            labelsToApply.add(messageId)
                            labelsToApplyMap[it] = labelsToApply
                        }
                        remove?.forEach {
                            var labelsToRemove: MutableList<String>? = labelsToRemoveMap[it]
                            if (labelsToRemove == null) {
                                labelsToRemove = ArrayList()
                            }
                            labelsToRemove.add(messageId)
                            labelsToRemoveMap[it] = labelsToRemove
                        }
                    }
                }

                result = Pair(labelsToApplyMap, labelsToRemoveMap)
            }
            val applyKeySet = result?.first?.keys
            val removeKeySet = result?.second?.keys
            applyKeySet?.forEach {
                jobManager.addJobInBackground(ApplyLabelJob(labelsToApplyMap[it], it))
            }

            removeKeySet?.forEach {
                jobManager.addJobInBackground(RemoveLabelJob(labelsToRemoveMap[it], it))
            }
        }
    }

    fun getMailboxItems(
        location: Constants.MessageLocationType,
        labelId: String?,
        includeLabels: Boolean,
        uuid: String,
        refreshMessages: Boolean,
        earliestTime: Long? = null
    ): LiveData<List<MailboxUiItem>> {
        // When earliest time is valid the request is about paginated messages (page > 1)
        if (earliestTime != null) {
            val isCustomLocation = location == LABEL || location == LABEL_FOLDER

            if (isCustomLocation) {
                messageServiceScheduler.fetchMessagesOlderThanTimeByLabel(
                    location,
                    userManager.requireCurrentUserId(),
                    earliestTime,
                    labelId ?: ""
                )
            } else {
                messageServiceScheduler.fetchMessagesOlderThanTime(
                    location,
                    userManager.requireCurrentUserId(),
                    earliestTime
                )
            }
        } else {
            jobManager.addJobInBackground(
                FetchByLocationJob(
                    location,
                    labelId,
                    includeLabels,
                    uuid,
                    refreshMessages
                )
            )
        }

        return getLiveDataByLocation(location, labelId).switchMap {
            messagesToMailboxItems(it)
        }
    }

    fun messagesToMailboxItems(messages: List<Message>): LiveData<List<MailboxUiItem>> {
        val mailboxItems = MutableLiveData<List<MailboxUiItem>>()
        viewModelScope.launch(dispatchers.Io) {
            val contacts = contactsRepository.findAllContactEmails().first()

            mailboxItems.postValue(
                messages.map { message ->
                    val senderName = getSenderNameToDisplay(contacts, message)

                    val messageData = MessageData(
                        message.location,
                        message.isReplied ?: false,
                        message.isRepliedAll ?: false,
                        message.isForwarded ?: false,
                        message.isInline,
                    )

                    MailboxUiItem(
                        message.messageId!!,
                        senderName,
                        message.subject!!,
                        message.timeMs,
                        message.numAttachments > 0,
                        message.isStarred ?: false,
                        message.isRead,
                        message.expirationTime,
                        0,
                        messageData,
                        message.deleted,
                        message.allLabelIDs,
                        message.toListStringGroupsAware
                    )
                }
            )
        }
        return mailboxItems
    }

    private fun getSenderNameToDisplay(contacts: List<ContactEmail>, message: Message): String {
        val senderNameFromContacts = contacts.find { message.senderEmail == it.email }?.name

        senderNameFromContacts?.let {
            return it
        }

        message.senderName?.let {
            if (it.isNotEmpty()) return it
        }

        return message.senderEmail
    }

    private fun getAllLabelsByIds(labelIds: List<String>) =
        messageDetailsRepository.findAllLabelsWithIds(labelIds)

    private fun resolveMessageLabels(
        message: Message,
        checkedLabelIds: MutableList<String>,
        unchangedLabels: List<String>,
        currentContactLabels: List<Label>?
    ): ApplyRemoveLabels? {
        val labelsToRemove = ArrayList<String>()

        currentContactLabels?.forEach {
            val labelId = it.id
            if (!checkedLabelIds.contains(labelId) && !unchangedLabels.contains(labelId) && !it.exclusive) {
                labelsToRemove.add(labelId)
            } else if (checkedLabelIds.contains(labelId)) {
                checkedLabelIds.remove(labelId)
            }
        }

        val labelList = ArrayList(message.labelIDsNotIncludingLocations)
        labelList.addAll(checkedLabelIds)
        labelList.removeAll(labelsToRemove)
        val labelSet = labelList.toSet()
        val maxLabelsAllowed = UserUtils.getMaxAllowedLabels(userManager)

        if (labelSet.size > maxLabelsAllowed) {
            _toastMessageMaxLabelsReached.value = Event(MaxLabelsReached(message.subject, maxLabelsAllowed))
            return null
        }

        message.addLabels(checkedLabelIds)
        message.removeLabels(labelsToRemove)
        viewModelScope.launch {
            messageDetailsRepository.saveMessageInDB(message)
        }

        return ApplyRemoveLabels(checkedLabelIds, labelsToRemove)
    }

    private fun getLiveDataByLocation(
        mailboxLocation: Constants.MessageLocationType,
        labelId: String?
    ): LiveData<List<Message>> {
        return when (mailboxLocation) {
            STARRED -> messageDetailsRepository.getStarredMessagesAsync()
            LABEL,
            LABEL_OFFLINE,
            LABEL_FOLDER -> messageDetailsRepository.getMessagesByLabelIdAsync(labelId!!)
            DRAFT,
            SENT,
            ARCHIVE,
            INBOX,
            SEARCH,
            SPAM,
            TRASH ->
                messageDetailsRepository.getMessagesByLocationAsync(mailboxLocation.messageLocationTypeValue)
            ALL_MAIL -> messageDetailsRepository.getAllMessages()
            INVALID -> throw IllegalArgumentException("Invalid location.")
            else -> throw IllegalArgumentException("Unknown location: $mailboxLocation")
        }
    }

    fun deleteMessages(messageIds: List<String>, currentLabelId: String?) =
        viewModelScope.launch {
            val deleteMessagesResult = deleteMessage(messageIds, currentLabelId)
            _hasSuccessfullyDeletedMessages.postValue(deleteMessagesResult.isSuccessfullyDeleted)
        }

    data class MaxLabelsReached(val subject: String?, val maxAllowedLabels: Int)
}
