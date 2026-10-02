package com.ridevibe.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.SupportMessage
import com.ridevibe.core.domain.repository.SupportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Delivery state of a message the rider sent; agent messages are always SENT. */
enum class MessageStatus { SENDING, SENT, FAILED }

/** A [SupportMessage] plus its local delivery status. */
data class ChatMessage(
    val id: String,
    val text: String,
    val fromUser: Boolean,
    val timestampEpochMillis: Long,
    val status: MessageStatus = MessageStatus.SENT,
)

data class ChatUiState(
    val isLoading: Boolean = true,
    /** Failure loading the thread; the screen offers a retry. */
    val error: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
)

// SupportRepository is injected directly (no use-case layer): send/receive
// passthroughs; the only logic here is local delivery status.
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val supportRepository: SupportRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val topic: String = savedStateHandle.get<String>("topic").orEmpty()
    private val bookingLabel: String = savedStateHandle.get<String>("booking").orEmpty()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val history = supportRepository.getMessages().map { it.toChatMessage() }
                _uiState.update { it.copy(isLoading = false, messages = history) }
                sendIntroOnce()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not open the chat") }
            }
        }
    }

    /**
     * Arriving from triage: open the thread with the chosen topic and booking
     * attached so the agent has context up front. Sent once per (topic,
     * booking): the flag lives in SavedStateHandle so a rotation or process
     * death does not post a second intro.
     */
    private suspend fun sendIntroOnce() {
        if (topic.isBlank()) return
        val key = "$KEY_INTRO_SENT:$topic:$bookingLabel"
        if (savedStateHandle.get<Boolean>(key) == true) return
        val intro = buildString {
            append("Topic: ").append(topic)
            if (bookingLabel.isNotBlank()) append(" • Booking ").append(bookingLabel)
        }
        savedStateHandle[key] = true
        deliver(ChatMessage(newLocalId(), intro, fromUser = true, System.currentTimeMillis(), MessageStatus.SENDING))
    }

    fun onDraftChanged(value: String) = _uiState.update { it.copy(draft = value) }

    fun send() {
        val text = _uiState.value.draft.trim()
        if (text.isEmpty() || _uiState.value.isSending) return
        val pending = ChatMessage(newLocalId(), text, fromUser = true, System.currentTimeMillis(), MessageStatus.SENDING)
        _uiState.update { it.copy(draft = "") }
        viewModelScope.launch { deliver(pending) }
    }

    /** Re-sends a FAILED message in place. */
    fun retry(messageId: String) {
        if (_uiState.value.isSending) return
        val failed = _uiState.value.messages.firstOrNull { it.id == messageId && it.status == MessageStatus.FAILED } ?: return
        _uiState.update { state ->
            state.copy(
                messages = state.messages.filterNot { it.id == messageId },
                // The failed text was restored to the draft; retrying it clears that copy.
                draft = if (state.draft == failed.text) "" else state.draft,
            )
        }
        viewModelScope.launch { deliver(failed.copy(status = MessageStatus.SENDING)) }
    }

    /**
     * Shows [pending] immediately, then replaces the local list with the
     * server's thread on success. On failure the bubble stays with a retry
     * chip and the text goes back into the draft so nothing typed is lost.
     */
    private suspend fun deliver(pending: ChatMessage) {
        _uiState.update { it.copy(isSending = true, messages = it.messages + pending) }
        try {
            val updated = supportRepository.sendMessage(pending.text).map { it.toChatMessage() }
            _uiState.update { it.copy(isSending = false, messages = updated) }
        } catch (e: Exception) {
            _uiState.update { state ->
                state.copy(
                    isSending = false,
                    messages = state.messages.map { if (it.id == pending.id) it.copy(status = MessageStatus.FAILED) else it },
                    draft = state.draft.ifBlank { pending.text },
                )
            }
        }
    }

    private fun SupportMessage.toChatMessage() = ChatMessage(id, text, fromUser, timestampEpochMillis)

    private fun newLocalId() = "local-${UUID.randomUUID()}"

    private companion object {
        const val KEY_INTRO_SENT = "intro_sent"
    }
}
