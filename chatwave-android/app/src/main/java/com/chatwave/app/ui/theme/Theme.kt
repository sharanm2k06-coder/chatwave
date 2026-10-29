package com.chatwave.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(primary = Color(0xFF0B7A75), secondary = Color(0xFF3D6B8E))
private val Dark = darkColorScheme(primary = Color(0xFF4FD1C5), secondary = Color(0xFF8FB8DE))

@Composable
fun ChatWaveTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= 31) {
        val c = LocalContext.current
        if (dark) dynamicDarkColorScheme(c) else dynamicLightColorScheme(c)
    } else if (dark) Dark else Light
    MaterialTheme(colorScheme = scheme, content = content)
}
