package com.smartstudybuddy.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.StoreKit.SKPayment
import platform.StoreKit.SKPaymentQueue
import platform.StoreKit.SKPaymentTransaction
import platform.StoreKit.SKPaymentTransactionObserverProtocol
import platform.StoreKit.SKProduct
import platform.StoreKit.SKProductsRequest
import platform.StoreKit.SKProductsRequestDelegateProtocol
import platform.StoreKit.SKProductsResponse
import platform.StoreKit.SKRequest
import platform.StoreKit.SKPaymentTransactionState
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.posix.memcpy

private class IosBillingClient : BillingClient {
    private var requestDelegate: NSObjectProtocol? = null
    private var transactionObserver: NSObjectProtocol? = null

    override suspend fun startSubscription(planTier: PlanTier): BillingPurchaseResult = withContext(Dispatchers.Main) {
        if (!SKPaymentQueue.canMakePayments()) {
            return@withContext BillingPurchaseResult.Error("In-app purchases are disabled on this device.")
        }

        val resultDeferred = CompletableDeferred<BillingPurchaseResult>()

        val observer = object : NSObject(), SKPaymentTransactionObserverProtocol {
            override fun paymentQueue(queue: SKPaymentQueue, updatedTransactions: List<*>) {
                updatedTransactions.forEach { item ->
                    val tx = item as? SKPaymentTransaction ?: return@forEach
                    when (tx.transactionState) {
                        SKPaymentTransactionState.SKPaymentTransactionStatePurchased,
                        SKPaymentTransactionState.SKPaymentTransactionStateRestored,
                        -> {
                            val receiptData = readAppStoreReceiptBase64()
                            if (receiptData.isBlank()) {
                                if (!resultDeferred.isCompleted) {
                                    resultDeferred.complete(
                                        BillingPurchaseResult.Error("Purchase succeeded but receipt is missing"),
                                    )
                                }
                            } else if (!resultDeferred.isCompleted) {
                                resultDeferred.complete(
                                    BillingPurchaseResult.Purchased(
                                        productId = tx.payment.productIdentifier,
                                        verificationData = receiptData,
                                        store = BillingStore.AppStore,
                                    ),
                                )
                            }
                            queue.finishTransaction(tx)
                        }

                        SKPaymentTransactionState.SKPaymentTransactionStateFailed -> {
                            val msg = tx.error?.localizedDescription ?: "Purchase cancelled"
                            if (!resultDeferred.isCompleted) {
                                if (msg.contains("cancel", ignoreCase = true)) {
                                    resultDeferred.complete(BillingPurchaseResult.Cancelled)
                                } else {
                                    resultDeferred.complete(BillingPurchaseResult.Error(msg))
                                }
                            }
                            queue.finishTransaction(tx)
                        }
                        else -> Unit
                    }
                }
            }
        }

        val delegate = object : NSObject(), SKProductsRequestDelegateProtocol {
            override fun productsRequest(request: SKProductsRequest, didReceiveResponse: SKProductsResponse) {
                val product = didReceiveResponse.products.firstOrNull() as? SKProduct
                if (product == null) {
                    if (!resultDeferred.isCompleted) {
                        resultDeferred.complete(
                            BillingPurchaseResult.Error("Product not found in App Store: ${planTier.productId}"),
                        )
                    }
                    return
                }
                SKPaymentQueue.defaultQueue().addPayment(SKPayment.paymentWithProduct(product))
            }

            override fun request(request: SKRequest, didFailWithError: platform.Foundation.NSError) {
                if (!resultDeferred.isCompleted) {
                    resultDeferred.complete(
                        BillingPurchaseResult.Error(didFailWithError.localizedDescription),
                    )
                }
            }
        }

        requestDelegate = delegate
        transactionObserver = observer
        SKPaymentQueue.defaultQueue().addTransactionObserver(observer)

        try {
            val request = SKProductsRequest(productIdentifiers = setOf(planTier.productId))
            request.delegate = delegate
            request.start()
            withTimeout(120_000) { resultDeferred.await() }
        } catch (e: Exception) {
            BillingPurchaseResult.Error(e.message ?: "StoreKit purchase failed")
        } finally {
            SKPaymentQueue.defaultQueue().removeTransactionObserver(observer)
            requestDelegate = null
            transactionObserver = null
        }
    }
}

actual fun createBillingClient(): BillingClient = IosBillingClient()

@OptIn(ExperimentalEncodingApi::class, ExperimentalForeignApi::class)
private fun readAppStoreReceiptBase64(): String {
    val receiptUrl = NSBundle.mainBundle.appStoreReceiptURL ?: return ""
    val receiptPath = receiptUrl.path ?: return ""
    val receiptData = NSFileManager.defaultManager.contentsAtPath(receiptPath) ?: return ""
    val length = receiptData.length.toInt()
    if (length <= 0) return ""
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), receiptData.bytes, receiptData.length)
    }
    return Base64.encode(bytes)
}
