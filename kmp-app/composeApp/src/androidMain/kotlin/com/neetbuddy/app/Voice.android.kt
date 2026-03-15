package com.neetbuddy.app

import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

private class AndroidVoicePlayer(private val tts: TextToSpeech) : VoicePlayer {
    override fun speak(text: String, language: String) {
        val locale = when (language) {
            "tamil" -> Locale("ta", "IN")
            "hindi" -> Locale("hi", "IN")
            else -> Locale("en", "IN")
        }
        tts.language = locale
        tts.setSpeechRate(0.95f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "neet-live-buddy")
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

@Composable
actual fun rememberVoicePlayer(): VoicePlayer {
    val context = LocalContext.current
    val tts = remember { TextToSpeech(context) {} }
    val player = remember(tts) { AndroidVoicePlayer(tts) }
    DisposableEffect(player) {
        onDispose { player.shutdown() }
    }
    return player
}
