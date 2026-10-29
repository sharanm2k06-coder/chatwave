package com.chatwave.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatwave.app.core.Validation
import com.chatwave.app.data.Repository
import com.chatwave.app.data.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val registerMode: Boolean = false, val username: String = "", val password: String = "", val displayName: String = "",
    val loading: Boolean = false, val error: String? = null, val fieldErrors: Map<String, String> = emptyMap(), val done: Boolean = false,
)

class AuthViewModel(private val repo: Repository) : ViewModel() {
    private val _s = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _s

    fun update(username: String? = null, password: String? = null, displayName: String? = null) =
        _s.update { it.copy(username = username ?: it.username, password = password ?: it.password, displayName = displayName ?: it.displayName, error = null) }
    fun toggleMode() = _s.update { it.copy(registerMode = !it.registerMode, error = null, fieldErrors = emptyMap()) }

    fun submit() {
        val s = _s.value
        val errs = buildMap {
            Validation.username(s.username)?.let { put("username", it) }
            Validation.password(s.password)?.let { put("password", it) }
            if (s.registerMode) Validation.displayName(s.displayName)?.let { put("displayName", it) }
        }
        if (errs.isNotEmpty()) { _s.update { it.copy(fieldErrors = errs) }; return }
        _s.update { it.copy(loading = true, error = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val r = if (s.registerMode) repo.register(s.username, s.password, s.displayName) else repo.login(s.username, s.password)
            _s.update {
                when (r) {
                    is Result.Ok -> it.copy(loading = false, done = true)
                    is Result.Err -> it.copy(loading = false, error = r.message)
                }
            }
        }
    }
}
