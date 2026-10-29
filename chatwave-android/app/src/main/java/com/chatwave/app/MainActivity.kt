package com.chatwave.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.chatwave.app.navigation.AppNav
import com.chatwave.app.ui.theme.ChatWaveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ChatWaveApp).container
        setContent { ChatWaveTheme { Surface { AppNav(container) } } }
    }
}
