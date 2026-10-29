package com.chatwave.app.feature.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatwave.app.core.SocketEvent
import com.chatwave.app.core.SocketManager
import kotlinx.coroutines.flow.*
import org.json.JSONObject

class CallViewModel(
    private val conversationId: String,
    private val peerUserId: String,
    private val socket: SocketManager,
    val context: android.content.Context,
) : ViewModel() {
    private val _status = MutableStateFlow("Starting call…")
    val status: StateFlow<String> = _status
    private var callId: String? = null
    private var client: WebRtcAudioClient? = null
    private var muted = false
    private var started = false

    fun start() {
        if (started) return
        started = true
        socket.connect()
        viewModelScope.launch {
            socket.events.collect { event ->
                when (event) {
                    is SocketEvent.CallSignal -> if (event.callId == callId) {
                        when (event.kind) {
                            "offer" -> client?.acceptOffer(event.data ?: JSONObject())
                            "answer" -> client?.acceptAnswer(event.data ?: JSONObject())
                            "ice" -> client?.addIce(event.data ?: JSONObject())
                        }
                    }
                    is SocketEvent.CallEnded -> if (event.callId == callId) end()
                    else -> Unit
                }
            }
        }
        socket.invite(conversationId, peerUserId) { id ->
            if (id == null) _status.value = "Could not start call"
            else {
                callId = id
                client = WebRtcAudioClient(context, { kind, data -> socket.signal(kind, id, peerUserId, data) }) { _status.value = it }
                client?.createOffer()
                _status.value = "Calling…"
            }
        }
    }

    fun toggleMute() {
        muted = !muted
        client?.setMuted(muted)
        _status.value = if (muted) "Muted" else "Connected"
    }

    fun end() {
        callId?.let { socket.endCall(it, peerUserId) }
        client?.close()
        client = null
        _status.value = "Call ended"
    }

    override fun onCleared() {
        client?.close()
        callId?.let { socket.endCall(it, peerUserId) }
    }
}

@Composable
fun CallScreen(vm: CallViewModel, onBack: () -> Unit) {
    val status by vm.status.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.start()
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(vm.context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Audio call")
        Text(status, Modifier.padding(16.dp))
        Button(onClick = vm::toggleMute) { Text("Mute / unmute") }
        Button(onClick = { vm.end(); onBack() }) { Text("End call") }
    }
}
