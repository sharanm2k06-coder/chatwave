package com.chatwave.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatwave.app.core.SocketEvent
import com.chatwave.app.core.SocketManager
import com.chatwave.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class SendState { SENDING, SENT, DELIVERED, READ, FAILED }

data class UiMessage(val clientId: String, val serverId: String?, val seq: Long?, val body: String, val mine: Boolean,
                     val state: SendState, val createdAt: String?)

data class ChatState(val messages: List<UiMessage> = emptyList(), val loading: Boolean = true, val error: String? = null,
                     val peerTyping: Boolean = false, val connected: Boolean = false, val nextBefore: Long? = null)

/** Merges server messages into the list keyed by clientMessageId so retries/echoes never duplicate. */
fun List<UiMessage>.merge(incoming: UiMessage): List<UiMessage> {
    val i = indexOfFirst { it.clientId == incoming.clientId }
    val out = if (i >= 0) toMutableList().also { it[i] = incoming } else this + incoming
    return out.sortedWith(compareBy({ it.seq == null }, { it.seq ?: 0L }))
}

fun Message.toUi(me: String?) = UiMessage(clientMessageId, id, seq, body, senderId == me, when (status) {
    "read" -> SendState.READ; "delivered" -> SendState.DELIVERED; else -> SendState.SENT }, createdAt)

class ChatViewModel(private val convId: String, private val repo: Repository, private val socket: SocketManager, private val me: String?) : ViewModel() {
    private val _s = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _s

    init {
        socket.connect()
        viewModelScope.launch { socket.connected.collect { c -> _s.update { it.copy(connected = c) }; if (c) { load(); resendFailed() } } }
        viewModelScope.launch {
            socket.events.collect { e ->
                when (e) {
                    is SocketEvent.NewMessage -> if (e.m.conversationId == convId) {
                        _s.update { it.copy(messages = it.messages.merge(e.m.toUi(me))) }
                        socket.markDelivered(listOf(e.m.id)); socket.markRead(convId, e.m.seq)
                    }
                    is SocketEvent.Delivered -> setState(e.messageId, SendState.DELIVERED, onlyIfBelow = SendState.DELIVERED)
                    is SocketEvent.Read -> setState(e.messageId, SendState.READ, onlyIfBelow = SendState.READ)
                    is SocketEvent.Typing -> if (e.conversationId == convId && e.userId != me) _s.update { it.copy(peerTyping = e.on) }
                    else -> Unit
                }
            }
        }
        load()
    }

    private fun setState(serverId: String, st: SendState, onlyIfBelow: SendState) = _s.update { s ->
        s.copy(messages = s.messages.map { if (it.serverId == serverId && it.state.ordinal < onlyIfBelow.ordinal) it.copy(state = st) else it })
    }

    fun load() = viewModelScope.launch {
        when (val r = repo.history(convId)) {
            is Result.Ok -> {
                val server = r.value.items.map { it.toUi(me) }
                _s.update { s ->
                    val pending = s.messages.filter { it.serverId == null }
                    s.copy(messages = (server.fold(pending) { acc, m -> acc.merge(m) }), loading = false, error = null, nextBefore = r.value.nextBefore)
                }
                r.value.items.maxByOrNull { it.seq }?.takeIf { it.senderId != me }?.let { socket.markRead(convId, it.seq) }
            }
            is Result.Err -> _s.update { it.copy(loading = false, error = r.message) }
        }
    }

    fun loadOlder() = viewModelScope.launch {
        val before = _s.value.nextBefore ?: return@launch
        (repo.history(convId, before) as? Result.Ok)?.let { r ->
            _s.update { s -> s.copy(messages = r.value.items.map { it.toUi(me) }.fold(s.messages) { a, m -> a.merge(m) }, nextBefore = r.value.nextBefore) }
        }
    }

    fun send(body: String) {
        val text = body.trim(); if (text.isEmpty()) return
        val clientId = UUID.randomUUID().toString()
        _s.update { it.copy(messages = it.messages.merge(UiMessage(clientId, null, null, text, true, SendState.SENDING, null))) }
        transmit(clientId, text)
    }

    fun retry(clientId: String) {
        _s.value.messages.firstOrNull { it.clientId == clientId && it.state == SendState.FAILED }?.let {
            updateLocal(clientId, SendState.SENDING); transmit(clientId, it.body)
        }
    }

    private fun resendFailed() = _s.value.messages.filter { it.serverId == null && it.state != SendState.SENDING }.forEach { retry(it.clientId) }

    private fun updateLocal(clientId: String, st: SendState) = _s.update { s -> s.copy(messages = s.messages.map { if (it.clientId == clientId) it.copy(state = st) else it }) }

    private fun transmit(clientId: String, text: String) {
        socket.send(convId, clientId, text) { m ->
            if (m != null) _s.update { it.copy(messages = it.messages.merge(m.toUi(me))) } else updateLocal(clientId, SendState.FAILED)
        }
    }

    fun typing(on: Boolean) = socket.typing(convId, on)
}
