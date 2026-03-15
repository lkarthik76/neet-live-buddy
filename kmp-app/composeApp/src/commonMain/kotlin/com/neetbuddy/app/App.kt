package com.neetbuddy.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Primary = Color(0xFF0F172A)
private val Surface = Color(0xFFF8FAFC)
private val CardBg = Color.White
private val Accent = Color(0xFF2563EB)
private val SuccessGreen = Color(0xFF16A34A)

@Composable
fun NeetLiveBuddyApp() {
    MaterialTheme {
        val api = remember { TutorApi() }
        val voicePlayer = rememberVoicePlayer()
        val scope = rememberCoroutineScope()
        val state = AppState

        val baseUrl = "https://neet-live-buddy-go-tutor-1092451837072.asia-south1.run.app"

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "NEET Live Buddy",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Primary
            )
            Text(
                "Scan question \u00b7 Speak doubt \u00b7 Get instant explanation",
                fontSize = 13.sp,
                color = Color.Gray
            )

            CardSection("Select Language") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("english" to "English", "hindi" to "\u0939\u093f\u0928\u094d\u0926\u0940", "tamil" to "\u0ba4\u0bae\u0bbf\u0bb4\u0bcd").forEach { (key, label) ->
                        FilterChip(
                            selected = state.language == key,
                            onClick = { state.language = key },
                            label = { Text(label, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            CardSection("Select Subject") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("physics" to "Physics", "chemistry" to "Chemistry", "biology" to "Biology").forEach { (key, label) ->
                        FilterChip(
                            selected = state.subject == key,
                            onClick = { state.subject = key },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            CardSection("\uD83D\uDCF7 Scan Question") {
                CameraPreviewSection(onImageCaptured = { base64 ->
                    state.imageBase64 = base64
                })
                if (state.imageBase64.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("\u2705 Image captured (${state.imageBase64.length / 1024}KB)", color = SuccessGreen, fontSize = 12.sp)
                        Button(
                            onClick = { state.imageBase64 = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("\u2715 Clear", fontSize = 12.sp)
                        }
                    }
                }
            }

            CardSection("\uD83C\uDFA4 Ask by Voice") {
                SpeechInputButton(language = state.language, onTranscript = { transcript ->
                    state.prompt = transcript
                })
                if (state.prompt.isNotBlank()) {
                    Text("Transcript: ${state.prompt}", fontSize = 12.sp, color = Color.Gray)
                }
            }

            CardSection("\u270F\uFE0F Or Type Your Doubt") {
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = { state.prompt = it },
                    label = { Text("Question / doubt") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = state.confused,
                        onClick = { state.confused = !state.confused },
                        label = { Text(if (state.confused) "Confused mode ON" else "Confused mode OFF") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFBBF24),
                            selectedLabelColor = Primary
                        )
                    )
                }
            }

            val hasInput = state.prompt.isNotBlank() || state.imageBase64.isNotBlank()
            Button(
                enabled = !state.loading && hasInput,
                onClick = {
                    state.loading = true
                    state.error = ""
                    state.result = null
                    val effectivePrompt = state.prompt.ifBlank { "Explain this question step by step" }
                    scope.launch {
                        try {
                            val response = api.askTutor(
                                baseUrl = baseUrl,
                                request = TutorRequest(
                                    prompt = effectivePrompt,
                                    subjectHint = state.subject,
                                    language = state.language,
                                    confused = state.confused,
                                    imageBase64 = state.imageBase64
                                )
                            )
                            state.result = response
                            voicePlayer.speak(response.answer, state.language)
                        } catch (e: Exception) {
                            state.error = e.message ?: "Request failed"
                        } finally {
                            state.loading = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Ask Live Buddy", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            if (state.error.isNotBlank()) {
                Text("Error: ${state.error}", color = Color.Red)
            }

            state.result?.let { res ->
                CardSection("\uD83D\uDCA1 ${res.chapter}") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (res.difficulty.isNotBlank()) {
                            val diffColor = when (res.difficulty.lowercase()) {
                                "easy" -> SuccessGreen
                                "hard" -> Color(0xFFDC2626)
                                else -> Color(0xFFF59E0B)
                            }
                            Text(
                                res.difficulty,
                                color = diffColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        if (res.correctOption.isNotBlank()) {
                            Text(
                                "Correct: (${res.correctOption})",
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    if (res.ncertReference.isNotBlank()) {
                        Text(
                            "NCERT: ${res.ncertReference}",
                            fontSize = 12.sp,
                            color = Accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(res.answer, lineHeight = 22.sp)
                }

                if (res.options.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    CardSection("\uD83D\uDD0D Option Analysis") {
                        res.options.forEach { opt ->
                            val icon = if (opt.correct) "\u2705" else "\u274C"
                            val bg = if (opt.correct) Color(0xFFDCFCE7) else Color(0xFFFEF2F2)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bg, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    "$icon (${opt.option}) ${opt.text}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(opt.explanation, fontSize = 13.sp, lineHeight = 20.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                CardSection("\uD83D\uDCDD Revision Card") {
                    if (res.revisionCard.concept.isNotBlank())
                        RevisionRow("Concept", res.revisionCard.concept)
                    if (res.revisionCard.keyPoint.isNotBlank())
                        RevisionRow("Key Point", res.revisionCard.keyPoint)
                    if (res.revisionCard.commonTrap.isNotBlank())
                        RevisionRow("Common Trap", res.revisionCard.commonTrap)
                    if (res.revisionCard.practiceQuestion.isNotBlank())
                        RevisionRow("Practice Q", res.revisionCard.practiceQuestion)
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { state.clearAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64748B)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ask Another Question", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CardSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Primary)
        content()
    }
}

@Composable
private fun RevisionRow(label: String, value: String) {
    Column {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp)
    }
}
