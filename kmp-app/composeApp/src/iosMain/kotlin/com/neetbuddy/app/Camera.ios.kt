package com.neetbuddy.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun CameraPreviewSection(onImageCaptured: (base64: String) -> Unit) {
    Text("Camera capture: iOS implementation pending.", modifier = Modifier.fillMaxWidth())
}
