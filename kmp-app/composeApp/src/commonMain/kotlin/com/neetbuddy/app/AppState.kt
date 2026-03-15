package com.neetbuddy.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppState {
    var language by mutableStateOf("tamil")
    var subject by mutableStateOf("biology")
    var prompt by mutableStateOf("")
    var imageBase64 by mutableStateOf("")
    var confused by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var result by mutableStateOf<TutorResponse?>(null)
    var error by mutableStateOf("")

    fun clearAll() {
        prompt = ""
        imageBase64 = ""
        result = null
        error = ""
        confused = false
    }
}
