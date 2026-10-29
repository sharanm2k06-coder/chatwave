package com.chatwave.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatwave.app.core.SocketEvent
import com.chatwave.app.core.SocketManager
import com.chatwave.app.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatListState(val chats: List<Chat> = emptyList(), val loading: Boolean = true, val error: String? = null,
                         val offline: Boolean = false, val connected: Boolean = false)

class ChatListViewModel(private val repo: Repository, private val socket: SocketManager) : ViewModel() {
    private val _s = MutableStateFlow(ChatListState())
    val state: StateFlow<ChatListState> = _s

    init {
        socket.connect()
        viewModelScope.launch { socket.connected.collect { c -> _s.update { it.copy(connected = c) }; if (c) refresh() } }
        viewModelScope.launch { socket.events.filterIsInstance<SocketEvent.NewMessage>().collect { socket.markDelivered(listOf(it.m.id)); refresh() } }
        viewModelScope.launch {
            socket.connectError.collect {
                delay(2000)
                if (repo.refreshBlocking(null, force = true) != null) socket.connect()
            }
        }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        when (val r = repo.chats()) {
            is Result.Ok -> _s.update { it.copy(chats = r.value, loading = false, error = null, offline = false) }
            is Result.Err -> _s.update { it.copy(loading = false, error = r.message, offline = r.offline) }
        }
    }
}

data class SearchState(val query: String = "", val results: List<User> = emptyList(), val loading: Boolean = false,
                       val error: String? = null, val searched: Boolean = false, val openChatId: String? = null, val openChatName: String? = null)

class SearchViewModel(private val repo: Repository) : ViewModel() {
    private val _s = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _s
    private var job: Job? = null

    fun onQuery(q: String) {
        _s.update { it.copy(query = q, error = null) }
        job?.cancel()
        if (q.trim().length < 2) { _s.update { it.copy(results = emptyList(), searched = false, loading = false) }; return }
        job = viewModelScope.launch {
            delay(300)
            _s.update { it.copy(loading = true) }
            when (val r = repo.search(q.trim())) {
                is Result.Ok -> _s.update { it.copy(results = r.value, loading = false, searched = true) }
                is Result.Err -> _s.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun open(u: User) = viewModelScope.launch {
        when (val r = repo.openDirect(u.id)) {
            is Result.Ok -> _s.update { it.copy(openChatId = r.value.id, openChatName = u.displayName) }
            is Result.Err -> _s.update { it.copy(error = r.message) }
        }
    }
    fun consumedOpen() = _s.update { it.copy(openChatId = null, openChatName = null) }
}
