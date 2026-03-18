package com.smartstudybuddy.app

import androidx.compose.runtime.Composable

interface CameraCapture {
    fun captureImage(onResult: (base64: String?, error: String?) -> Unit)
}

@Composable
expect fun CameraPreviewSection(onImageCaptured: (base64: String) -> Unit)
