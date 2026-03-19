package com.smartstudybuddy.app

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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
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
        val authApi = remember { AuthApi() }
        val billingClient = remember { createBillingClient() }
        val voicePlayer = rememberVoicePlayer()
        val telemetry = rememberTelemetry()
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

        LaunchedEffect(state.showSignIn, state.authToken) {
            if (state.showSignIn && state.isAuthenticated) {
                if (!state.profileLoaded) {
                    try {
                        val profile = api.getProfile(baseUrl, state.authToken)
                        state.updateProfile(profile)
                    } catch (_: Exception) {
                        // Keep profile fields empty if fetch fails.
                    }
                }
                if (!state.billingStatusLoaded) {
                    try {
                        val status = api.getBillingStatus(baseUrl, state.authToken)
                        state.updateBillingStatus(status)
                    } catch (_: Exception) {
                        // Keep billing status empty if fetch fails.
                    }
                }
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
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.92f),
                            )
                        }
                    },
                    actions = {
                        if (state.isFreeTier && state.dailyLimit > 0) {
                            UsageBadge(used = state.dailyUsed, limit = state.dailyLimit)
                            Spacer(Modifier.width(4.dp))
                        }
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Account",
                            tint = if (state.isSignedIn) Gold else Color.White.copy(alpha = 0.9f),
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { state.showSignIn = !state.showSignIn },
                        )
                        Spacer(Modifier.width(4.dp))
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
                        onUpgradeClick = {
                            telemetry.trackEvent(
                                name = "upgrade_prompt_opened",
                                params = mapOf("source" to "free_tier_banner"),
                            )
                            state.showUpgradePrompt = true
                        },
                    )
                }

                if (isDebugBuild()) {
                    DebugTierPanel(
                        tier = state.tier,
                        dailyUsed = state.dailyUsed,
                        dailyLimit = state.dailyLimit,
                        email = state.email,
                        deviceId = deviceId,
                        onSetTier = { selectedTier ->
                            scope.launch {
                                try {
                                    val usage = api.setTier(
                                        baseUrl = baseUrl,
                                        deviceId = deviceId,
                                        email = state.email,
                                        tier = selectedTier,
                                        authToken = state.authToken,
                                    )
                                    state.updateUsage(usage)
                                    snackbarHostState.showSnackbar(
                                        "Debug tier set: ${selectedTier.replaceFirstChar { it.uppercase() }}",
                                    )
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "Failed to set tier")
                                }
                            }
                        },
                        onRefresh = {
                            scope.launch {
                                try {
                                    state.updateUsage(api.getUsage(baseUrl, deviceId))
                                    snackbarHostState.showSnackbar("Usage refreshed")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "Failed to refresh usage")
                                }
                            }
                        },
                    )
                }

                AnimatedVisibility(
                    visible = state.showSignIn,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    AccountCard(
                        state = state,
                        onSignUp = { email, password ->
                            scope.launch {
                                try {
                                    val auth = authApi.signUp(email = email, password = password)
                                    state.setAuth(auth)
                                    snackbarHostState.showSnackbar("Account created: ${auth.email}")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "Sign up failed")
                                }
                            }
                        },
                        onSignIn = { email, password ->
                            scope.launch {
                                try {
                                    val auth = authApi.signIn(email = email, password = password)
                                    state.setAuth(auth)
                                    snackbarHostState.showSnackbar("Signed in: ${auth.email}")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "Sign in failed")
                                }
                            }
                        },
                        onSignOut = {
                            state.clearAuth()
                            scope.launch { snackbarHostState.showSnackbar("Signed out") }
                        },
                        onRefreshBillingStatus = {
                            scope.launch {
                                try {
                                    val status = api.getBillingStatus(baseUrl, state.authToken)
                                    state.updateBillingStatus(status)
                                    snackbarHostState.showSnackbar("Billing status refreshed")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "Failed to load billing status")
                                }
                            }
                        },
                        onSaveProfile = { name, phone, classLevel ->
                            scope.launch {
                                try {
                                    val saved = api.saveProfile(
                                        baseUrl = baseUrl,
                                        profile = StudentProfile(
                                            studentName = name,
                                            email = state.authEmail,
                                            phoneNumber = phone,
                                            classLevel = classLevel,
                                        ),
                                        authToken = state.authToken,
                                    )
                                    state.updateProfile(saved)
                                    snackbarHostState.showSnackbar("Profile saved")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "Failed to save profile")
                                }
                            }
                        },
                        onLink = {
                            scope.launch {
                                try {
                                    val usage = api.linkEmail(
                                        baseUrl = baseUrl,
                                        deviceId = deviceId,
                                        email = state.authEmail,
                                        authToken = state.authToken,
                                    )
                                    state.updateUsage(usage)
                                    state.showSignIn = false
                                    snackbarHostState.showSnackbar("Account linked: ${state.authEmail}")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "Failed to link email")
                                }
                            }
                        },
                        onRestore = {
                            scope.launch {
                                try {
                                    val usage = api.restorePurchase(
                                        baseUrl = baseUrl,
                                        deviceId = deviceId,
                                        email = state.authEmail,
                                        authToken = state.authToken,
                                    )
                                    state.updateUsage(usage)
                                    state.showSignIn = false
                                    snackbarHostState.showSnackbar("Account restored! Tier: ${usage.tier}")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "Restore failed")
                                }
                            }
                        },
                        onDismiss = { state.showSignIn = false },
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
                                telemetry.trackEvent(
                                    name = "upgrade_prompt_opened",
                                    params = mapOf("source" to "daily_limit_reached"),
                                )
                                state.showUpgradePrompt = true
                                state.error = e.message ?: "Daily limit reached"
                            } catch (e: Exception) {
                                telemetry.recordError(e, "ask_tutor_failed")
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
                        currentTier = state.tier,
                        onSelectPlan = { tier ->
                            scope.launch {
                                telemetry.trackEvent(
                                    name = "plan_selected",
                                    params = mapOf("tier" to tier.apiValue),
                                )
                                val billingResult = billingClient.startSubscription(tier)
                                when (billingResult) {
                                    is BillingPurchaseResult.Purchased -> {
                                        telemetry.trackEvent(
                                            name = "purchase_captured",
                                            params = mapOf(
                                                "store" to billingResult.store.name,
                                                "product_id" to billingResult.productId,
                                            ),
                                        )
                                        snackbarHostState.showSnackbar(
                                            "Purchase captured. Verifying with backend...",
                                        )
                                        var activated = false
                                        var verificationAttempt = 0
                                        while (!activated && verificationAttempt < 3) {
                                            verificationAttempt++
                                            try {
                                                val usage = when (billingResult.store) {
                                                    BillingStore.GooglePlay -> api.verifyGooglePurchase(
                                                        baseUrl = baseUrl,
                                                        deviceId = deviceId,
                                                        email = state.authEmail,
                                                        productId = billingResult.productId,
                                                        purchaseToken = billingResult.verificationData,
                                                        packageName = getAppPackageName(),
                                                        authToken = state.authToken,
                                                    )

                                                    BillingStore.AppStore -> api.verifyApplePurchase(
                                                        baseUrl = baseUrl,
                                                        deviceId = deviceId,
                                                        email = state.authEmail,
                                                        productId = billingResult.productId,
                                                        receiptData = billingResult.verificationData,
                                                        authToken = state.authToken,
                                                    )
                                                }
                                                state.updateUsage(usage)
                                                state.showUpgradePrompt = false
                                                telemetry.trackEvent(
                                                    name = "purchase_activated",
                                                    params = mapOf(
                                                        "tier" to usage.tier,
                                                        "store" to billingResult.store.name,
                                                    ),
                                                )
                                                snackbarHostState.showSnackbar(
                                                    "Plan activated: ${usage.tier.replaceFirstChar { it.uppercase() }}",
                                                )
                                                activated = true
                                            } catch (e: Exception) {
                                                telemetry.recordError(
                                                    e,
                                                    "purchase_verification_failed_attempt_$verificationAttempt",
                                                )
                                                telemetry.trackEvent(
                                                    name = "purchase_verification_failed",
                                                    params = mapOf(
                                                        "store" to billingResult.store.name,
                                                        "attempt" to verificationAttempt.toString(),
                                                    ),
                                                )
                                                val message = e.message ?: "Purchase verification failed"
                                                val snackbarResult = snackbarHostState.showSnackbar(
                                                    message = "$message. Retry now?",
                                                    actionLabel = "Retry",
                                                    withDismissAction = true,
                                                )
                                                if (snackbarResult != SnackbarResult.ActionPerformed) {
                                                    snackbarHostState.showSnackbar(
                                                        "You can use Restore Purchase from Account screen later.",
                                                    )
                                                    break
                                                }
                                            }
                                        }
                                    }

                                    BillingPurchaseResult.Cancelled -> {
                                        telemetry.trackEvent(name = "purchase_cancelled")
                                        snackbarHostState.showSnackbar("Purchase cancelled")
                                    }

                                    is BillingPurchaseResult.Unsupported -> {
                                        if (isDebugBuild()) {
                                            try {
                                                val usage = api.setTier(
                                                    baseUrl = baseUrl,
                                                    deviceId = deviceId,
                                                    email = state.authEmail,
                                                    tier = tier.apiValue,
                                                    authToken = state.authToken,
                                                )
                                                state.updateUsage(usage)
                                                state.showUpgradePrompt = false
                                                snackbarHostState.showSnackbar(
                                                    "Debug mode: ${tier.name} plan set",
                                                )
                                            } catch (e: Exception) {
                                                telemetry.recordError(e, "debug_tier_set_failed")
                                                snackbarHostState.showSnackbar(e.message ?: "Failed to set debug tier")
                                            }
                                        } else {
                                            telemetry.trackEvent(
                                                name = "purchase_unsupported",
                                                params = mapOf("reason" to billingResult.reason),
                                            )
                                            snackbarHostState.showSnackbar(billingResult.reason)
                                        }
                                    }

                                    is BillingPurchaseResult.Error -> {
                                        telemetry.recordError(
                                            Exception(billingResult.message),
                                            "purchase_flow_error",
                                        )
                                        telemetry.trackEvent(
                                            name = "purchase_error",
                                            params = mapOf("message" to billingResult.message),
                                        )
                                        snackbarHostState.showSnackbar(billingResult.message)
                                    }
                                }
                            }
                        },
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
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
private fun UpgradePromptCard(
    currentTier: String,
    onSelectPlan: (PlanTier) -> Unit,
    onDismiss: () -> Unit,
) {
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
                    highlighted = currentTier == "pro",
                    onClick = { onSelectPlan(PlanTier.Pro) },
                )
                PricingOption(
                    name = "Ultimate",
                    price = "Rs 299/month",
                    features = "Everything in Pro + practice tests, analytics, offline",
                    highlighted = currentTier == "ultimate",
                    onClick = { onSelectPlan(PlanTier.Ultimate) },
                )
            }

            Text(
                "Coming soon to Google Play Store",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
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
    onClick: () -> Unit,
) {
    val containerColor = if (highlighted) Orange else MaterialTheme.colorScheme.surface
    val contentColor = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurface
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
@OptIn(ExperimentalMaterial3Api::class)
private fun AccountCard(
    state: AppState,
    onSignUp: (String, String) -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onRefreshBillingStatus: () -> Unit,
    onSaveProfile: (String, String, String) -> Unit,
    onLink: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    var emailInput by remember { mutableStateOf(state.authEmail.ifBlank { state.email }) }
    var passwordInput by remember { mutableStateOf("") }
    var studentNameInput by remember { mutableStateOf(state.studentName) }
    var phoneInput by remember { mutableStateOf(state.phoneNumber) }
    var classInput by remember { mutableStateOf(state.classLevel) }
    var classExpanded by remember { mutableStateOf(false) }
    val isAlreadyLinked = state.isSignedIn
    val classOptions = listOf("11", "12", "repeter")
    val isPhoneValid = phoneInput.trim().isBlank() || Regex("^[0-9]{10}$").matches(phoneInput.trim())

    LaunchedEffect(state.studentName, state.phoneNumber, state.classLevel, state.profileLoaded) {
        if (state.profileLoaded) {
            studentNameInput = state.studentName
            phoneInput = state.phoneNumber
            classInput = state.classLevel
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (isAlreadyLinked) "Account" else "Sign In / Create Account",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                if (state.isAuthenticated) "Authenticated as: ${state.authEmail}" else "Authenticate first to secure your account actions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("Email address") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("Password (min 6 chars)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = { onSignIn(emailInput.trim(), passwordInput) },
                    enabled = emailInput.trim().contains("@") && passwordInput.length >= 6,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Sign In", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = { onSignUp(emailInput.trim(), passwordInput) },
                    enabled = emailInput.trim().contains("@") && passwordInput.length >= 6,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Sign Up", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (state.isAuthenticated) {
                Text(
                    "Student profile",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                OutlinedTextField(
                    value = studentNameInput,
                    onValueChange = { studentNameInput = it },
                    label = { Text("Student name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Phone number") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )
                if (!isPhoneValid) {
                    Text(
                        "Enter a valid 10-digit phone number",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Box {
                    OutlinedTextField(
                        value = classInput,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Class") },
                        placeholder = { Text("Select: 11 / 12 / repeter") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select class",
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { classExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                    DropdownMenu(
                        expanded = classExpanded,
                        onDismissRequest = { classExpanded = false },
                    ) {
                        classOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    classInput = option
                                    classExpanded = false
                                },
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        onSaveProfile(
                            studentNameInput.trim(),
                            phoneInput.trim(),
                            classInput.trim(),
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = studentNameInput.trim().isNotBlank() &&
                        classOptions.contains(classInput.trim()) &&
                        isPhoneValid,
                ) {
                    Text("Save Profile", style = MaterialTheme.typography.labelMedium)
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "Billing Status",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val billing = state.billingStatus
                        Text(
                            "Tier: ${(billing?.tier ?: state.tier).replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Linked devices: ${billing?.linkedDeviceIds?.size ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val googleEntitlement = billing?.googleEntitlement
                        if (googleEntitlement != null) {
                            Text(
                                "Google product: ${googleEntitlement.productId.ifBlank { "-" }}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Google status: ${googleEntitlement.status.ifBlank { "-" }}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Last verified: ${googleEntitlement.lastVerifiedAt.ifBlank { "-" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (googleEntitlement.lastError.isNotBlank()) {
                                Text(
                                    "Last error: ${googleEntitlement.lastError}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            Text(
                                "No active store entitlement record yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        OutlinedButton(
                            onClick = onRefreshBillingStatus,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Refresh Billing Status", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign Out", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (isAlreadyLinked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            state.email,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Tier: ${state.tier.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Your subscription is linked to this email. " +
                        "If you switch devices, sign in with the same email to restore your plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Link your email to protect your subscription. " +
                        "If you lose your device, sign in on a new one to restore your plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = onLink,
                        enabled = state.isAuthenticated,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Link Account", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onRestore,
                        enabled = state.isAuthenticated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Restore Purchase", style = MaterialTheme.typography.labelMedium, fontSize = 12.sp)
                    }
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Close", style = MaterialTheme.typography.labelMedium)
            }
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

@Composable
private fun DebugTierPanel(
    tier: String,
    dailyUsed: Int,
    dailyLimit: Int,
    email: String,
    deviceId: String,
    onSetTier: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "DEBUG TIER TEST PANEL",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Tier: ${tier.replaceFirstChar { it.uppercase() }}  |  Used: $dailyUsed  |  Limit: $dailyLimit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Email: ${if (email.isBlank()) "(not linked)" else email}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Device: $deviceId",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(onClick = { onSetTier("free") }, modifier = Modifier.weight(1f)) {
                    Text("Free")
                }
                OutlinedButton(onClick = { onSetTier("pro") }, modifier = Modifier.weight(1f)) {
                    Text("Pro")
                }
                OutlinedButton(onClick = { onSetTier("ultimate") }, modifier = Modifier.weight(1f)) {
                    Text("Ultimate")
                }
            }
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh Usage")
            }
        }
    }
}
