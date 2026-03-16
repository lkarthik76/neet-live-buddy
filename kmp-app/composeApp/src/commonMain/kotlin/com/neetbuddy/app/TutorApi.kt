package com.neetbuddy.app

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
}
