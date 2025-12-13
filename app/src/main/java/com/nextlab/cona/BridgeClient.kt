package com.nextlab.cona

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable

@Serializable
data class BridgeEvent(
    val packageName: String,
    val title: String,
    val text: String,
    val tag: String?
)

object BridgeClient {
    private const val TAG = "BridgeClient"
    private const val BRIDGE_URL = "http://bridge:8090/event"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun sendNotificationEvent(packageName: String, title: String, text: String, tag: String?) {
        try {
            val event = BridgeEvent(packageName, title, text, tag)
            client.post(BRIDGE_URL) {
                contentType(ContentType.Application.Json)
                setBody(event)
            }
            Log.d(TAG, "Sent notification to bridge: $event")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification to bridge", e)
        }
    }
}
