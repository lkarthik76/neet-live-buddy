package com.smartstudybuddy.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSLog

private class IosTelemetry : Telemetry {
    override fun trackEvent(name: String, params: Map<String, String>) {
        NSLog("SmartStudyBuddy event=%@ params=%@", name, params.toString())
    }

    override fun recordError(throwable: Throwable, context: String?) {
        NSLog(
            "SmartStudyBuddy error context=%@ message=%@",
            context ?: "",
            throwable.message ?: "unknown",
        )
    }
}

@Composable
actual fun rememberTelemetry(): Telemetry {
    return remember { IosTelemetry() }
}
