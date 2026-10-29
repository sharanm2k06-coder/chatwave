package com.chatwave.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatwave.app.core.Validation
import com.chatwave.app.data.Repository
import com.chatwave.app.data.Result
import com.chatwave.app.data.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(val username: String = "", val displayName: String = "", val loading: Boolean = true, val error: String? = null, val saved: Boolean = false)
class ProfileViewModel(private val repo: Repository, initial: User?) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState(initial?.username.orEmpty(), initial?.displayName.orEmpty(), loading = initial == null))
    val state: StateFlow<ProfileState> = _state
    init {
        if (initial == null) viewModelScope.launch {
            when (val r = repo.me()) {
                is Result.Ok -> _state.update { it.copy(username = r.value.username, displayName = r.value.displayName, loading = false) }
                is Result.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
    fun update(username: String? = null, displayName: String? = null) = _state.update { it.copy(username = username ?: it.username, displayName = displayName ?: it.displayName, error = null, saved = false) }
    fun save() {
        val s = _state.value
        val error = Validation.username(s.username) ?: Validation.displayName(s.displayName)
        if (error != null) { _state.update { it.copy(error = error) }; return }
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val r = repo.updateProfile(s.username, s.displayName)) {
                is Result.Ok -> _state.update { it.copy(loading = false, saved = true) }
                is Result.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}

data class GroupState(val name: String = "", val query: String = "", val results: List<User> = emptyList(),
                      val selected: Set<String> = emptySet(), val loading: Boolean = false, val error: String? = null, val createdId: String? = null)
class GroupViewModel(private val repo: Repository) : ViewModel() {
    private val _state = MutableStateFlow(GroupState())
    val state: StateFlow<GroupState> = _state
    fun name(value: String) = _state.update { it.copy(name = value, error = null) }
    fun search(value: String) {
        _state.update { it.copy(query = value, error = null) }
        if (value.trim().length < 2) { _state.update { it.copy(results = emptyList()) }; return }
        viewModelScope.launch {
            when (val r = repo.search(value.trim())) {
                is Result.Ok -> _state.update { it.copy(results = r.value) }
                is Result.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }
    fun toggle(user: User) = _state.update { s -> s.copy(selected = if (user.id in s.selected) s.selected - user.id else s.selected + user.id) }
    fun create() {
        val s = _state.value
        if (s.name.trim().isEmpty()) { _state.update { it.copy(error = "Enter a group name") }; return }
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val r = repo.createGroup(s.name, s.selected.toList())) {
                is Result.Ok -> _state.update { it.copy(loading = false, createdId = r.value.id) }
                is Result.Err -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}
