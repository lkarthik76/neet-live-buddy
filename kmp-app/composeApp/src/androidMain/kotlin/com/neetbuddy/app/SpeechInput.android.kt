package com.neetbuddy.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidSpeechRecognizer
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun SpeechInputButton(language: String, onTranscript: (String) -> Unit) {
    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    if (audioPermission.status.isGranted) {
        SpeechContent(language, onTranscript)
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val msg = if (audioPermission.status.shouldShowRationale) {
                "Microphone access is needed for voice input. Please grant permission."
            } else {
                "Tap below to enable voice input."
            }
            Text(msg, fontSize = 13.sp, color = Color.Gray)
            Button(onClick = { audioPermission.launchPermissionRequest() }) {
                Text("Grant Microphone Permission")
            }
        }
    }
}

@Composable
private fun SpeechContent(language: String, onTranscript: (String) -> Unit) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<AndroidSpeechRecognizer?>(null) }
    var errorMsg by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                if (isListening) {
                    recognizer?.stopListening()
                    isListening = false
                    return@Button
                }

                errorMsg = ""

                if (!AndroidSpeechRecognizer.isRecognitionAvailable(context)) {
                    errorMsg = "Speech recognition not available on this device"
                    return@Button
                }

                val sr = recognizer ?: AndroidSpeechRecognizer.createSpeechRecognizer(context).also {
                    recognizer = it
                }

                val locale = when (language) {
                    "tamil" -> "ta-IN"
                    "hindi" -> "hi-IN"
                    else -> "en-IN"
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(locale))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("NeetSpeech", "Ready for speech")
                    }
                    override fun onBeginningOfSpeech() {
                        Log.d("NeetSpeech", "Speech started")
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                    }
                    override fun onError(error: Int) {
                        isListening = false
                        val msg = when (error) {
                            AndroidSpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try again."
                            AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Try again."
                            AndroidSpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            AndroidSpeechRecognizer.ERROR_NETWORK -> "Network error for speech"
                            AndroidSpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            else -> "Speech error ($error)"
                        }
                        errorMsg = msg
                        Log.e("NeetSpeech", "Error: $error — $msg")
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            onTranscript(partial)
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val text = results
                            ?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            onTranscript(text)
                        }
                        isListening = false
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                sr.startListening(intent)
                isListening = true
            },
            colors = if (isListening) {
                ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isListening) "\uD83C\uDF99\uFE0F Listening... (tap to stop)" else "\uD83C\uDFA4 Speak your doubt")
        }

        if (errorMsg.isNotBlank()) {
            Text(errorMsg, color = Color.Red, fontSize = 12.sp)
        }
    }
}
