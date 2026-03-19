package com.smartstudybuddy.app

import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

private class AndroidTelemetry : Telemetry {
    override fun trackEvent(name: String, params: Map<String, String>) {
        runCatching {
            val bundle = Bundle()
            params.forEach { (key, value) ->
                bundle.putString(key.take(40), value.take(100))
            }
            Firebase.analytics.logEvent(name.take(40), bundle)
        }
        Log.d("SmartStudyTelemetry", "event=$name params=$params")
    }

    override fun recordError(throwable: Throwable, context: String?) {
        if (!context.isNullOrBlank()) {
            Firebase.crashlytics.log(context)
        }
        Firebase.crashlytics.recordException(throwable)
        Log.e("SmartStudyTelemetry", context ?: "error", throwable)
    }
}

@Composable
actual fun rememberTelemetry(): Telemetry {
    return remember { AndroidTelemetry() }
}
