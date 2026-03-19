package com.smartstudybuddy.app

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

    // Usage & tier state
    var dailyUsed by mutableStateOf(0)
    var dailyLimit by mutableStateOf(10)
    var tier by mutableStateOf("free")
    var email by mutableStateOf("")
    var authEmail by mutableStateOf("")
    var authToken by mutableStateOf("")
    var authLocalId by mutableStateOf("")
    var studentName by mutableStateOf("")
    var phoneNumber by mutableStateOf("")
    var classLevel by mutableStateOf("")
    var profileLoaded by mutableStateOf(false)
    var billingStatus by mutableStateOf<BillingStatus?>(null)
    var billingStatusLoaded by mutableStateOf(false)
    var showUpgradePrompt by mutableStateOf(false)
    var showSignIn by mutableStateOf(false)

    val isFreeTier: Boolean get() = tier == "free"
    val isAuthenticated: Boolean get() = authToken.isNotBlank() && authEmail.isNotBlank()
    val isSignedIn: Boolean get() = email.isNotBlank()
    val questionsRemaining: Int get() = if (dailyLimit < 0) Int.MAX_VALUE else (dailyLimit - dailyUsed).coerceAtLeast(0)

    fun updateUsage(usage: UsageInfo?) {
        if (usage == null) return
        dailyUsed = usage.used
        dailyLimit = usage.limit
        tier = usage.tier
        if (usage.email.isNotBlank()) email = usage.email
    }

    fun clearAll() {
        prompt = ""
        imageBase64 = ""
        result = null
        error = ""
        confused = false
        showUpgradePrompt = false
    }

    fun setAuth(auth: FirebaseAuthResponse) {
        authEmail = auth.email
        authToken = auth.idToken
        authLocalId = auth.localId
        studentName = ""
        phoneNumber = ""
        classLevel = ""
        profileLoaded = false
        billingStatus = null
        billingStatusLoaded = false
    }

    fun clearAuth() {
        authEmail = ""
        authToken = ""
        authLocalId = ""
        studentName = ""
        phoneNumber = ""
        classLevel = ""
        profileLoaded = false
        billingStatus = null
        billingStatusLoaded = false
    }

    fun updateProfile(profile: StudentProfile) {
        studentName = profile.studentName
        phoneNumber = profile.phoneNumber
        classLevel = profile.classLevel
        profileLoaded = true
    }

    fun updateBillingStatus(status: BillingStatus) {
        billingStatus = status
        billingStatusLoaded = true
    }
}
