package com.chatwave.app.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores tokens encrypted with an AES-GCM key held in the Android Keystore (key never leaves the secure hardware/TEE). */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("chatwave_secure", Context.MODE_PRIVATE)

    @Volatile var accessToken: String? = null; private set
    @Volatile var refreshToken: String? = null; private set
    var userId: String? = null; private set

    init {
        accessToken = read("access"); refreshToken = read("refresh"); userId = read("uid")
    }

    @Synchronized fun save(access: String, refresh: String, uid: String? = userId) {
        accessToken = access; refreshToken = refresh; userId = uid
        write("access", access); write("refresh", refresh); if (uid != null) write("uid", uid)
    }

    @Synchronized fun clear() { accessToken = null; refreshToken = null; userId = null; prefs.edit().clear().apply() }
    val isLoggedIn get() = refreshToken != null

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return gen.generateKey()
    }

    private fun write(name: String, value: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val blob = c.iv + c.doFinal(value.toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    private fun read(name: String): String? = runCatching {
        val blob = Base64.decode(prefs.getString(name, null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        String(c.doFinal(blob.copyOfRange(12, blob.size)))
    }.getOrNull()

    private companion object { const val ALIAS = "chatwave_token_key" }
}
