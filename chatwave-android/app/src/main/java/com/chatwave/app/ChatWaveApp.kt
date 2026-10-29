package com.chatwave.app

import android.app.Application
import com.chatwave.app.core.SocketManager
import com.chatwave.app.core.TokenStore
import com.chatwave.app.data.Api
import com.chatwave.app.data.Repository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class AppContainer(app: Application) {
    val context = app.applicationContext
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val tokens = TokenStore(app)
    lateinit var repository: Repository
    val socket = SocketManager(tokens, json)

    init {
        val authenticator = Authenticator { _, response ->
            if (response.request.url.encodedPath.contains("/auth/")) return@Authenticator null
            if (response.responseCount() > 2) return@Authenticator null
            val failed = response.request.header("Authorization")?.removePrefix("Bearer ")
            val fresh = repository.refreshBlocking(failed) ?: return@Authenticator null
            response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
        }
        val client = OkHttpClient.Builder()
            // Render free instances can take ~30-60s to wake from sleep.
            .connectTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val b = chain.request().newBuilder()
                tokens.accessToken?.let { b.header("Authorization", "Bearer $it") }
                chain.proceed(b.build())
            }
            .authenticator(authenticator)
            .apply { if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC; redactHeader("Authorization") }) }
            .build()
        val api = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(Api::class.java)
        repository = Repository(api, tokens, json)
    }

    private fun okhttp3.Response.responseCount(): Int { var n = 1; var r = priorResponse; while (r != null) { n++; r = r.priorResponse }; return n }
}

class ChatWaveApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}
