package com.smartstudybuddy.app

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FirebaseAuthResponse(
    @SerialName("idToken")
    val idToken: String = "",
    @SerialName("localId")
    val localId: String = "",
    val email: String = "",
)

@Serializable
private data class FirebaseAuthRequest(
    val email: String,
    val password: String,
    @SerialName("returnSecureToken")
    val returnSecureToken: Boolean = true,
)

class AuthApi {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(jsonParser) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }

    suspend fun signUp(email: String, password: String): FirebaseAuthResponse {
        val key = getFirebaseWebApiKey().trim()
        require(key.isNotBlank()) { "Firebase API key not configured. Set FIREBASE_WEB_API_KEY." }
        return client.post("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$key") {
            setBody(FirebaseAuthRequest(email = email, password = password))
        }.body()
    }

    suspend fun signIn(email: String, password: String): FirebaseAuthResponse {
        val key = getFirebaseWebApiKey().trim()
        require(key.isNotBlank()) { "Firebase API key not configured. Set FIREBASE_WEB_API_KEY." }
        return client.post("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$key") {
            setBody(FirebaseAuthRequest(email = email, password = password))
        }.body()
    }
}
