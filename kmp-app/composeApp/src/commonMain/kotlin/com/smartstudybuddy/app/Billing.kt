package com.smartstudybuddy.app

enum class PlanTier(val apiValue: String, val productId: String) {
    Pro(apiValue = "pro", productId = "neet_pro_monthly"),
    Ultimate(apiValue = "ultimate", productId = "neet_ultimate_monthly"),
}

enum class BillingStore {
    GooglePlay,
    AppStore,
}

sealed interface BillingPurchaseResult {
    data class Purchased(
        val productId: String,
        val verificationData: String,
        val store: BillingStore,
    ) : BillingPurchaseResult

    data object Cancelled : BillingPurchaseResult

    data class Unsupported(val reason: String) : BillingPurchaseResult

    data class Error(val message: String) : BillingPurchaseResult
}

interface BillingClient {
    suspend fun startSubscription(planTier: PlanTier): BillingPurchaseResult
}

expect fun createBillingClient(): BillingClient
