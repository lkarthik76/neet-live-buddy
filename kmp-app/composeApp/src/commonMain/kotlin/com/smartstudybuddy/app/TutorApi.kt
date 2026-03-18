package com.smartstudybuddy.app

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class DailyLimitReachedException(val usage: UsageInfo?, message: String) : Exception(message)

class TutorApi {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(jsonParser)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }

    suspend fun askTutor(baseUrl: String, request: TutorRequest): TutorResponse {
        val response = client.post("${baseUrl.trimEnd('/')}/tutor") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (response.status.value == 429) {
            val errorBody = response.bodyAsText()
            val parsed = try {
                jsonParser.decodeFromString<UsageLimitError>(errorBody)
            } catch (_: Exception) {
                null
            }
            throw DailyLimitReachedException(
                usage = parsed?.usage,
                message = parsed?.message ?: "Daily question limit reached. Upgrade to Pro!"
            )
        }

        return response.body()
    }

    suspend fun getUsage(baseUrl: String, deviceId: String): UsageInfo {
        return client.get("${baseUrl.trimEnd('/')}/usage?deviceId=$deviceId").body()
    }

    suspend fun linkEmail(baseUrl: String, deviceId: String, email: String): UsageInfo {
        return client.post("${baseUrl.trimEnd('/')}/auth/link") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("deviceId" to deviceId, "email" to email))
        }.body()
    }

    suspend fun restorePurchase(baseUrl: String, deviceId: String, email: String): UsageInfo {
        val response = client.post("${baseUrl.trimEnd('/')}/auth/restore") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("deviceId" to deviceId, "email" to email))
        }
        if (response.status.value == 404) {
            throw Exception("No account found with this email.")
        }
        return response.body()
    }

    suspend fun setTier(baseUrl: String, deviceId: String, email: String, tier: String): UsageInfo {
        return client.post("${baseUrl.trimEnd('/')}/tier") {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "deviceId" to deviceId,
                    "email" to email,
                    "tier" to tier,
                ),
            )
        }.body()
    }

    suspend fun verifyGooglePurchase(
        baseUrl: String,
        deviceId: String,
        email: String,
        productId: String,
        purchaseToken: String,
        packageName: String,
    ): UsageInfo {
        return client.post("${baseUrl.trimEnd('/')}/billing/google/verify") {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "deviceId" to deviceId,
                    "email" to email,
                    "productId" to productId,
                    "purchaseToken" to purchaseToken,
                    "packageName" to packageName,
                ),
            )
        }.body()
    }

    suspend fun verifyApplePurchase(
        baseUrl: String,
        deviceId: String,
        email: String,
        productId: String,
        receiptData: String,
    ): UsageInfo {
        return client.post("${baseUrl.trimEnd('/')}/billing/apple/verify") {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "deviceId" to deviceId,
                    "email" to email,
                    "productId" to productId,
                    "receiptData" to receiptData,
                ),
            )
        }.body()
    }
}
