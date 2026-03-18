package com.smartstudybuddy.app

import androidx.compose.runtime.Composable

interface SpeechRecognizer {
    fun startListening(language: String, onResult: (transcript: String) -> Unit, onError: (String) -> Unit)
    fun stopListening()
}

@Composable
expect fun SpeechInputButton(language: String, onTranscript: (String) -> Unit)
