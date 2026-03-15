package com.neetbuddy.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun SpeechInputButton(language: String, onTranscript: (String) -> Unit) {
    Text("Speech input: iOS implementation pending.", modifier = Modifier.fillMaxWidth())
}
