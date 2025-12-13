package com.nextlab.cona

import android.util.Log
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ReplyRequest(
    val title: String, // Use title as ID for now
    val text: String
)

object BridgeServer {
    private const val TAG = "BridgeServer"
    private var server: EmbeddedServer<*, *>? = null
    private var notificationService: NotificationService? = null

    fun start(service: NotificationService) {
        notificationService = service
        if (server != null) return

        try {
            server = embeddedServer(CIO, port = 8080) {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    io.ktor.serialization.kotlinx.json.json()
                }
                routing {
                    post("/reply") {
                        try {
                            val request = call.receive<ReplyRequest>()
                            Log.d(TAG, "Received reply request: $request")

                            val success = notificationService?.replyToMessage(request.title, request.text) ?: false

                            if (success) {
                                call.respondText("Reply sent")
                            } else {
                                call.respondText("Failed to reply (notification not found)", status = io.ktor.http.HttpStatusCode.NotFound)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling reply", e)
                            call.respondText("Error: ${e.message}", status = io.ktor.http.HttpStatusCode.InternalServerError)
                        }
                    }
                }
            }.start(wait = false)
            Log.d(TAG, "BridgeServer started on port 8080")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BridgeServer", e)
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        notificationService = null
        Log.d(TAG, "BridgeServer stopped")
    }
}
