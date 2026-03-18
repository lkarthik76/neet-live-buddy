package com.smartstudybuddy.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private class IosVoicePlayer : VoicePlayer {
    override fun speak(text: String, language: String) {
        // TODO: Add AVSpeechSynthesizer for iOS voice output.
    }
}

@Composable
actual fun rememberVoicePlayer(): VoicePlayer {
    return remember { IosVoicePlayer() }
}
