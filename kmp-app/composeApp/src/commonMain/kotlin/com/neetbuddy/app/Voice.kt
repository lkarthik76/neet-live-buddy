package com.neetbuddy.app

import androidx.compose.runtime.Composable

interface VoicePlayer {
    fun speak(text: String, language: String)
    fun shutdown() {}
}

@Composable
expect fun rememberVoicePlayer(): VoicePlayer
