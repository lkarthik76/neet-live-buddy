package com.smartstudybuddy.app

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box

@Composable
actual fun AppBarLogo(modifier: Modifier) {
    // iOS: bundle the same artwork later; book emoji matches product theme for now.
    Box(
        modifier = modifier.size(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "📚",
            fontSize = 52.sp,
            textAlign = TextAlign.Center,
        )
    }
}
