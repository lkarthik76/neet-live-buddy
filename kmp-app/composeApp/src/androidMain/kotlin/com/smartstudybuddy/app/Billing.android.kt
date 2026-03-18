package com.smartstudybuddy.app

import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private class AndroidBillingClient : BillingClient {
    private var pendingResult: CompletableDeferred<BillingPurchaseResult>? = null

    override suspend fun startSubscription(planTier: PlanTier): BillingPurchaseResult = withContext(Dispatchers.Main) {
        val activity = ActivityHolder.currentActivity
            ?: return@withContext BillingPurchaseResult.Error("No active screen. Open app screen and try again.")

        val purchaseListener = PurchasesUpdatedListener { result, purchases ->
            val deferred = pendingResult ?: return@PurchasesUpdatedListener
            when (result.responseCode) {
                com.android.billingclient.api.BillingClient.BillingResponseCode.OK -> {
                    val purchase = purchases?.firstOrNull()
                    if (purchase == null) {
                        deferred.complete(BillingPurchaseResult.Error("Purchase finished but no purchase token found"))
                        return@PurchasesUpdatedListener
                    }
                    maybeAcknowledgePurchase(activity, purchase)
                    val productId = purchase.products.firstOrNull() ?: planTier.productId
                    deferred.complete(
                        BillingPurchaseResult.Purchased(
                            productId = productId,
                            verificationData = purchase.purchaseToken,
                            store = BillingStore.GooglePlay,
                        ),
                    )
                }
                com.android.billingclient.api.BillingClient.BillingResponseCode.USER_CANCELED -> deferred.complete(BillingPurchaseResult.Cancelled)
                else -> deferred.complete(
                    BillingPurchaseResult.Error(
                        "Billing failed: ${result.debugMessage.ifBlank { "code ${result.responseCode}" }}",
                    ),
                )
            }
        }

        val client = com.android.billingclient.api.BillingClient.newBuilder(activity.applicationContext)
            .setListener(purchaseListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .build()

        try {
            val setupResult = connect(client)
            if (setupResult.responseCode != com.android.billingclient.api.BillingClient.BillingResponseCode.OK) {
                return@withContext BillingPurchaseResult.Error(
                    "Billing setup failed: ${setupResult.debugMessage}",
                )
            }

            val productDetails = querySubscription(client, planTier.productId)
                ?: return@withContext BillingPurchaseResult.Error(
                    "Plan unavailable on Play Console: ${planTier.productId}",
                )

            val offerToken = productDetails.subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken
                ?: return@withContext BillingPurchaseResult.Error(
                    "No subscription offer token for ${planTier.productId}",
                )

            val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()

            val launchResult = client.launchBillingFlow(activity, flowParams)
            if (launchResult.responseCode != com.android.billingclient.api.BillingClient.BillingResponseCode.OK) {
                return@withContext BillingPurchaseResult.Error(
                    "Could not open Play purchase screen: ${launchResult.debugMessage}",
                )
            }

            val deferred = CompletableDeferred<BillingPurchaseResult>()
            pendingResult = deferred
            return@withContext withTimeout(120_000) { deferred.await() }
        } catch (e: Exception) {
            BillingPurchaseResult.Error(e.message ?: "Purchase failed")
        } finally {
            pendingResult = null
            client.endConnection()
        }
    }

    private suspend fun connect(client: com.android.billingclient.api.BillingClient): BillingResult =
        suspendCancellableCoroutine { cont ->
            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (cont.isActive) cont.resume(result)
                    }

                    override fun onBillingServiceDisconnected() {
                        if (cont.isActive) {
                            cont.resume(
                                BillingResult.newBuilder()
                                    .setResponseCode(com.android.billingclient.api.BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                                    .setDebugMessage("Billing service disconnected")
                                    .build(),
                            )
                        }
                    }
                },
            )
        }

    private suspend fun querySubscription(
        client: com.android.billingclient.api.BillingClient,
        productId: String,
    ): ProductDetails? = suspendCancellableCoroutine { cont ->
        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(com.android.billingclient.api.BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()

        client.queryProductDetailsAsync(query) { _, detailsList ->
            if (cont.isActive) cont.resume(detailsList.firstOrNull())
        }
    }

    private fun maybeAcknowledgePurchase(activity: android.app.Activity, purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val client = com.android.billingclient.api.BillingClient.newBuilder(activity.applicationContext)
            .setListener { _, _ -> }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .build()
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == com.android.billingclient.api.BillingClient.BillingResponseCode.OK) {
                        val params = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        client.acknowledgePurchase(params) {
                            client.endConnection()
                        }
                    } else {
                        client.endConnection()
                    }
                }

                override fun onBillingServiceDisconnected() {
                    client.endConnection()
                }
            },
        )
    }
}

actual fun createBillingClient(): BillingClient = AndroidBillingClient()
