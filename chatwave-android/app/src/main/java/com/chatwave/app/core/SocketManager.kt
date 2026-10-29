package com.chatwave.app.core

import com.chatwave.app.BuildConfig
import com.chatwave.app.data.Message
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.net.URI

sealed interface SocketEvent {
    data class NewMessage(val m: Message) : SocketEvent
    data class Delivered(val messageId: String, val conversationId: String) : SocketEvent
    data class Read(val messageId: String, val conversationId: String) : SocketEvent
    data class Typing(val conversationId: String, val userId: String, val on: Boolean) : SocketEvent
    data class Presence(val userId: String, val online: Boolean) : SocketEvent
    data class IncomingCall(val callId: String, val conversationId: String, val fromUserId: String) : SocketEvent
    data class CallSignal(val kind: String, val callId: String, val fromUserId: String, val data: JSONObject?) : SocketEvent
    data class CallEnded(val callId: String, val fromUserId: String) : SocketEvent
}

class SocketManager(private val tokens: TokenStore, private val json: Json) {
    private var socket: Socket? = null
    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SocketEvent> = _events
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    private val _connectError = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    /** Emits when the server rejected or could not complete the connection (e.g. expired token): caller refreshes and reconnects. */
    val connectError: SharedFlow<Unit> = _connectError

    @Synchronized fun connect() {
        if (socket?.connected() == true) return
        disconnect()
        val opts = IO.Options().apply {
            auth = mapOf("token" to (tokens.accessToken ?: ""))
            transports = arrayOf("websocket"); reconnection = true; reconnectionDelayMax = 10_000
        }
        val s = IO.socket(URI.create(BuildConfig.BASE_URL), opts)
        s.on(Socket.EVENT_CONNECT) { _connected.value = true }
        s.on(Socket.EVENT_DISCONNECT) { _connected.value = false }
        s.on(Socket.EVENT_CONNECT_ERROR) { _connected.value = false; _connectError.tryEmit(Unit) }
        s.on("message:new") { a -> parse(a)?.let { _events.tryEmit(SocketEvent.NewMessage(json.decodeFromString(it.toString()))) } }
        s.on("message:delivered") { a -> parse(a)?.let { _events.tryEmit(SocketEvent.Delivered(it.getString("messageId"), it.getString("conversationId"))) } }
        s.on("message:read") { a -> parse(a)?.let { _events.tryEmit(SocketEvent.Read(it.getString("messageId"), it.getString("conversationId"))) } }
        s.on("typing:start") { a -> parse(a)?.let { _events.tryEmit(SocketEvent.Typing(it.getString("conversationId"), it.getString("userId"), true)) } }
        s.on("typing:stop") { a -> parse(a)?.let { _events.tryEmit(SocketEvent.Typing(it.getString("conversationId"), it.getString("userId"), false)) } }
        s.on("presence:update") { a -> parse(a)?.let { _events.tryEmit(SocketEvent.Presence(it.getString("userId"), it.getBoolean("online"))) } }
        s.on("call:incoming") { a -> parse(a)?.let { _events.tryEmit(SocketEvent.IncomingCall(it.getString("callId"), it.getString("conversationId"), it.getString("fromUserId"))) } }
        for (kind in listOf("offer", "answer", "ice"))
            s.on("call:$kind") { a -> parse(a)?.let { _events.tryEmit(SocketEvent.CallSignal(kind, it.getString("callId"), it.getString("fromUserId"), it.optJSONObject("data"))) } }
        s.on("call:ended") { a -> parse(a)?.let { _events.tryEmit(SocketEvent.CallEnded(it.getString("callId"), it.getString("fromUserId"))) } }
        socket = s; s.connect()
    }

    @Synchronized fun disconnect() { socket?.off(); socket?.disconnect(); socket = null; _connected.value = false }

    /** Calls [onResult] with the server-stored message once the DB commit is acknowledged, or null on failure/timeout. */
    fun send(conversationId: String, clientMessageId: String, body: String, onResult: (Message?) -> Unit) {
        val s = socket
        if (s == null || !s.connected()) { onResult(null); return }
        val payload = JSONObject().put("conversationId", conversationId).put("clientMessageId", clientMessageId).put("body", body)
        s.emit("message:send", payload, io.socket.client.Ack { args ->
            val res = args.firstOrNull() as? JSONObject
            if (res?.optBoolean("ok") == true) onResult(json.decodeFromString<Message>(res.getJSONObject("message").toString()))
            else onResult(null)
        })
    }

    fun markRead(conversationId: String, upToSeq: Long) {
        socket?.emit("message:read", JSONObject().put("conversationId", conversationId).put("upToSeq", upToSeq))
    }
    fun markDelivered(ids: List<String>) {
        if (ids.isNotEmpty()) socket?.emit("message:delivered", JSONObject().put("messageIds", org.json.JSONArray(ids)))
    }
    fun typing(conversationId: String, on: Boolean) {
        socket?.emit(if (on) "typing:start" else "typing:stop", JSONObject().put("conversationId", conversationId))
    }
    fun invite(conversationId: String, peerUserId: String, onResult: (String?) -> Unit) {
        socket?.emit("call:invite", JSONObject().put("conversationId", conversationId).put("peerUserId", peerUserId),
            io.socket.client.Ack { args -> onResult((args.firstOrNull() as? JSONObject)?.optString("callId")?.takeIf { it.isNotEmpty() }) })
    }
    fun signal(kind: String, callId: String, peerUserId: String, data: JSONObject) {
        socket?.emit("call:$kind", JSONObject().put("callId", callId).put("peerUserId", peerUserId).put("data", data))
    }
    fun endCall(callId: String, peerUserId: String) {
        socket?.emit("call:end", JSONObject().put("callId", callId).put("peerUserId", peerUserId))
    }
    private fun parse(a: Array<Any>) = a.firstOrNull() as? JSONObject
}
