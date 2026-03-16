package com.neetbuddy.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeetLiveBuddyApp() {
    NeetBuddyTheme {
        val api = remember { TutorApi() }
        val voicePlayer = rememberVoicePlayer()
        val scope = rememberCoroutineScope()
        val state = AppState
        val snackbarHostState = remember { SnackbarHostState() }
        val scrollState = rememberScrollState()

        val baseUrl = getBackendUrl()
        val deviceId = remember { getDeviceId() }

        LaunchedEffect(Unit) {
            try {
                val usage = api.getUsage(baseUrl, deviceId)
                state.updateUsage(usage)
            } catch (_: Exception) { }
        }

        LaunchedEffect(state.error) {
            if (state.error.isNotBlank()) {
                snackbarHostState.showSnackbar(state.error)
                state.error = ""
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "NEET Live Buddy",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                            )
                            Text(
                                "AI-Powered NEET Exam Tutor",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    },
                    actions = {
                        if (state.isFreeTier && state.dailyLimit > 0) {
                            UsageBadge(used = state.dailyUsed, limit = state.dailyLimit)
                            Spacer(Modifier.width(8.dp))
                        }
                        LanguagePill(
                            selected = state.language,
                            onSelect = { state.language = it },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isFreeTier) {
                    FreeTierBanner(
                        questionsLeft = state.questionsRemaining,
                        onUpgradeClick = { state.showUpgradePrompt = true },
                    )
                }

                SubjectSelector(
                    selected = state.subject,
                    onSelect = { state.subject = it },
                )

                InputCard(state)

                AskButton(
                    loading = state.loading,
                    hasInput = state.prompt.isNotBlank() || state.imageBase64.isNotBlank(),
                    onClick = {
                        state.loading = true
                        state.error = ""
                        state.result = null
                        val effectivePrompt =
                            state.prompt.ifBlank { "Explain this question step by step" }
                        scope.launch {
                            try {
                                val response = api.askTutor(
                                    baseUrl = baseUrl,
                                    request = TutorRequest(
                                        deviceId = deviceId,
                                        prompt = effectivePrompt,
                                        subjectHint = state.subject,
                                        language = state.language,
                                        confused = state.confused,
                                        imageBase64 = state.imageBase64,
                                    ),
                                )
                                state.result = response
                                state.updateUsage(response.usage)
                                voicePlayer.speak(response.answer, state.language)
                            } catch (e: DailyLimitReachedException) {
                                state.updateUsage(e.usage)
                                state.showUpgradePrompt = true
                                state.error = e.message ?: "Daily limit reached"
                            } catch (e: Exception) {
                                state.error = e.message ?: "Request failed"
                            } finally {
                                state.loading = false
                            }
                        }
                    },
                )

                AnimatedVisibility(
                    visible = state.loading,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    LoadingIndicator()
                }

                AnimatedVisibility(visible = state.showUpgradePrompt) {
                    UpgradePromptCard(
                        onDismiss = { state.showUpgradePrompt = false },
                    )
                }

                state.result?.let { res ->
                    LaunchedEffect(res) {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }

                    if (state.isFreeTier) {
                        FreeUpsellBanner()
                    }

                    AnswerSection(res)
                    AskAnotherButton { state.clearAll() }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun LanguagePill(selected: String, onSelect: (String) -> Unit) {
    val langs = listOf("english" to "EN", "hindi" to "HI", "tamil" to "TA")
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        langs.forEach { (key, label) ->
            val isSelected = selected == key
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SubjectSelector(selected: String, onSelect: (String) -> Unit) {
    val subjects = listOf(
        "biology" to "Biology",
        "physics" to "Physics",
        "chemistry" to "Chemistry",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        subjects.forEach { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InputCard(state: AppState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Scan or type your question",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            CameraPreviewSection(onImageCaptured = { base64 ->
                state.imageBase64 = base64
            })

            if (state.imageBase64.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Image captured (${state.imageBase64.length / 1024}KB)",
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen,
                        )
                    }
                    Button(
                        onClick = { state.imageBase64 = "" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            SpeechInputButton(
                language = state.language,
                onTranscript = { state.prompt = it },
            )

            OutlinedTextField(
                value = state.prompt,
                onValueChange = { state.prompt = it },
                label = { Text("Type your doubt here...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 2,
            )

            FilterChip(
                selected = state.confused,
                onClick = { state.confused = !state.confused },
                label = {
                    Text(
                        if (state.confused) "Confused mode ON" else "Confused mode OFF",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Gold,
                    selectedLabelColor = Navy,
                ),
            )
        }
    }
}

@Composable
private fun AskButton(loading: Boolean, hasInput: Boolean, onClick: () -> Unit) {
    Button(
        enabled = !loading && hasInput,
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Orange,
            contentColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            "Ask Live Buddy",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun LoadingIndicator() {
    val transition = rememberInfiniteTransition(label = "loading")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Gemini is thinking...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            )
            Text(
                "Analyzing with NCERT knowledge base",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun AnswerSection(res: TutorResponse) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.animateContentSize(),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        res.chapter,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (res.difficulty.isNotBlank()) {
                        DifficultyBadge(res.difficulty)
                    }
                    if (res.correctOption.isNotBlank()) {
                        Badge(
                            text = "Correct: (${res.correctOption})",
                            bgColor = Color(0xFFDCFCE7),
                            textColor = SuccessGreen,
                        )
                    }
                }

                if (res.ncertReference.isNotBlank()) {
                    Text(
                        res.ncertReference,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Text(
                    res.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (res.options.isNotEmpty()) {
            CollapsibleCard(title = "Option Analysis", defaultExpanded = true) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    res.options.forEach { opt ->
                        val bg = if (opt.correct) Color(0xFFDCFCE7) else Color(0xFFFEF2F2)
                        val icon = if (opt.correct) "✅" else "❌"
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bg, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                        ) {
                            Text(
                                "$icon (${opt.option}) ${opt.text}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                opt.explanation,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        CollapsibleCard(title = "Revision Card") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (res.revisionCard.concept.isNotBlank())
                    LabeledField("Concept", res.revisionCard.concept)
                if (res.revisionCard.keyPoint.isNotBlank())
                    LabeledField("Key Point", res.revisionCard.keyPoint)
                if (res.revisionCard.commonTrap.isNotBlank())
                    LabeledField("Common Trap", res.revisionCard.commonTrap)
                if (res.revisionCard.practiceQuestion.isNotBlank())
                    LabeledField("Practice Question", res.revisionCard.practiceQuestion)
            }
        }
    }
}

@Composable
private fun CollapsibleCard(
    title: String,
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.animateContentSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DifficultyBadge(difficulty: String) {
    val (bgColor, textColor) = when (difficulty.lowercase()) {
        "easy" -> Color(0xFFDCFCE7) to SuccessGreen
        "hard" -> Color(0xFFFEF2F2) to ErrorRed
        else -> Color(0xFFFEF9C3) to Color(0xFFCA8A04)
    }
    Badge(text = difficulty, bgColor = bgColor, textColor = textColor)
}

@Composable
private fun Badge(text: String, bgColor: Color, textColor: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = textColor,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun LabeledField(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AskAnotherButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            "Ask Another Question",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun UsageBadge(used: Int, limit: Int) {
    val remaining = (limit - used).coerceAtLeast(0)
    val bgColor = when {
        remaining <= 2 -> ErrorRed
        remaining <= 5 -> Gold
        else -> SuccessGreen
    }
    Text(
        text = "$remaining/$limit",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .background(bgColor.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun FreeTierBanner(questionsLeft: Int, onUpgradeClick: () -> Unit) {
    val bannerColor = when {
        questionsLeft <= 2 -> ErrorRed
        questionsLeft <= 5 -> Gold
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = bannerColor.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Free Plan — $questionsLeft questions left today",
                    style = MaterialTheme.typography.titleSmall,
                    color = bannerColor,
                )
                Text(
                    "Upgrade to Pro for unlimited questions + detailed analysis",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onUpgradeClick,
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Upgrade", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun UpgradePromptCard(onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Upgrade to NEET Pro",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
            )
            Text(
                "Get unlimited questions, option-by-option analysis, " +
                    "NCERT references, and full revision cards.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PricingOption(
                    name = "Pro",
                    price = "Rs 99/month",
                    features = "Unlimited questions, full analysis, revision cards",
                    highlighted = true,
                )
                PricingOption(
                    name = "Ultimate",
                    price = "Rs 299/month",
                    features = "Everything in Pro + practice tests, analytics, offline",
                    highlighted = false,
                )
            }

            Text(
                "Coming soon to Google Play Store",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
            )

            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Maybe Later")
            }
        }
    }
}

@Composable
private fun PricingOption(
    name: String,
    price: String,
    features: String,
    highlighted: Boolean,
) {
    val containerColor = if (highlighted) Orange else MaterialTheme.colorScheme.surface
    val contentColor = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurface
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Text(
                    features,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
            Text(
                price,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun FreeUpsellBanner() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF7ED),
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Upgrade to Pro to unlock option analysis, NCERT references, " +
                    "and detailed revision cards for every answer.",
                style = MaterialTheme.typography.bodySmall,
                color = OrangeDark,
            )
        }
    }
}
