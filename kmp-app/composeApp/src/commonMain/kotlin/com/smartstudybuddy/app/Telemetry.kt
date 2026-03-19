package com.smartstudybuddy.app

import androidx.compose.runtime.Composable

interface Telemetry {
    fun trackEvent(name: String, params: Map<String, String> = emptyMap())
    fun recordError(throwable: Throwable, context: String? = null)
}

@Composable
expect fun rememberTelemetry(): Telemetry
