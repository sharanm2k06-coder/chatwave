package com.chatwave.app.data

import com.chatwave.app.core.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

sealed interface Result<out T> {
    data class Ok<T>(val value: T) : Result<T>
    data class Err(val message: String, val offline: Boolean = false) : Result<Nothing>
}

class Repository(private val api: Api, private val tokens: TokenStore, private val json: Json) {
    private val refreshLock = Mutex()

    private suspend fun <T> call(block: suspend () -> T): Result<T> = try {
        Result.Ok(block())
    } catch (e: HttpException) {
        val msg = runCatching { json.decodeFromString<ApiErrorBody>(e.response()?.errorBody()?.string().orEmpty()).error?.message }.getOrNull()
        Result.Err(msg ?: "Request failed (${e.code()})")
    } catch (e: IOException) {
        Result.Err("No connection. Check your internet and try again.", offline = true)
    }

    private fun store(r: AuthResponse) = tokens.save(r.accessToken, r.refreshToken, r.user?.id)

    suspend fun register(u: String, p: String, d: String) = call { api.register(RegisterReq(u.trim().lowercase(), p, d.trim())).also(::store).user!! }
    suspend fun login(u: String, p: String) = call { api.login(LoginReq(u.trim().lowercase(), p)).also(::store).user!! }
    suspend fun logout() {
        tokens.refreshToken?.let { runCatching { api.logout(RefreshReq(it)) } }
        tokens.clear()
    }

    /** Used by the OkHttp Authenticator. Returns the new access token or null if the session is gone. */
    fun refreshBlocking(failedToken: String?, force: Boolean = false): String? = runBlocking {
        refreshLock.withLock {
            if (!force && tokens.accessToken != null && tokens.accessToken != failedToken) return@withLock tokens.accessToken
            val rt = tokens.refreshToken ?: return@withLock null
            try { api.refresh(RefreshReq(rt)).also { store(it) }.accessToken }
            catch (e: HttpException) { if (e.code() == 401) tokens.clear(); null }
            catch (e: IOException) { null }
        }
    }

    suspend fun search(q: String) = call { api.search(q).items }
    suspend fun updateProfile(username: String, displayName: String) = call { api.updateMe(ProfileUpdateReq(username, displayName)) }
    suspend fun me() = call { api.me() }
    suspend fun chats() = call { api.chats().items }
    suspend fun openDirect(userId: String) = call { api.direct(DirectReq(userId)) }
    suspend fun createGroup(name: String, userIds: List<String>) = call { api.group(GroupCreateReq(name, userIds)) }
    suspend fun history(id: String, before: Long? = null) = call { api.history(id, before) }
}
