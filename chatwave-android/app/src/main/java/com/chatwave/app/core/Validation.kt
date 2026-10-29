package com.chatwave.app.core

object Validation {
    private val USERNAME = Regex("^[a-z0-9_]{3,30}$")
    fun username(raw: String): String? =
        if (USERNAME.matches(raw.trim().lowercase())) null else "3-30 characters: letters, numbers, underscore"
    fun password(raw: String): String? = if (raw.length in 8..128) null else "At least 8 characters"
    fun displayName(raw: String): String? = if (raw.trim().length in 1..50) null else "Required (max 50 characters)"
}
