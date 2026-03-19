package com.smartstudybuddy.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate

private class IosVoicePlayer : VoicePlayer {
    private val synthesizer = AVSpeechSynthesizer()

    override fun speak(text: String, language: String) {
        if (text.isBlank()) return

        val languageCode = when (language.lowercase()) {
            "tamil" -> "ta-IN"
            "hindi" -> "hi-IN"
            else -> "en-IN"
        }

        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage(languageCode)
        synthesizer.speakUtterance(utterance)
    }

    override fun shutdown() {
        // No explicit cleanup needed for AVSpeechSynthesizer here.
    }
}

@Composable
actual fun rememberVoicePlayer(): VoicePlayer {
    return remember { IosVoicePlayer() }
}
