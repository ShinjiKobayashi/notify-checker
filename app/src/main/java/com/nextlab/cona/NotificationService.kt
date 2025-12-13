package com.nextlab.cona

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class NotificationPayload(
    val id: String,
    val sender: String,
    val message: String,
    val timestamp: Long
)

@Serializable
data class ReplyPayload(
    val id: String,
    val replyText: String
)

class NotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationService"
        private const val TARGET_PACKAGE = "com.microsoft.teams"
        private const val SERVER_PORT = 8080
        private const val REMOTE_ENDPOINT = "http://example.com/notifications"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeNotificationsMap = ConcurrentHashMap<String, StatusBarNotification>()

    // Ktor Client
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    // Ktor Server
    private val server by lazy {
        embeddedServer(io.ktor.server.cio.CIO, port = SERVER_PORT) {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            routing {
                post("/reply") {
                    try {
                        val payload = call.receive<ReplyPayload>()
                        Log.d(TAG, "Received reply request: $payload")

                        val sbn = activeNotificationsMap[payload.id]
                        if (sbn != null) {
                            replyToNotification(sbn, payload.replyText)
                            call.respondText("Reply sent")
                        } else {
                            Log.w(TAG, "Notification not found for ID: ${payload.id}")
                            call.respondText("Notification not found", status = io.ktor.http.HttpStatusCode.NotFound)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing reply request", e)
                        call.respondText("Error processing request", status = io.ktor.http.HttpStatusCode.InternalServerError)
                    }
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification Listener Connected")

        serviceScope.launch {
            try {
                server.start(wait = false)
                Log.d(TAG, "Ktor Server started on port $SERVER_PORT")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Ktor Server", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            server.stop(1000, 2000)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
        client.close()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        // Log all notifications for debugging purposes, but only process Teams
        if (packageName != TARGET_PACKAGE) {
             return
        }

        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "Unknown"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val timestamp = sbn.postTime
        val id = sbn.key // Unique ID for the notification

        Log.d(TAG, "Teams Notification received: ID=$id, Title=$title, Text=$text")

        // Store for potential reply
        activeNotificationsMap[id] = sbn

        // Send to external server
        serviceScope.launch {
            try {
                val payload = NotificationPayload(
                    id = id,
                    sender = title,
                    message = text,
                    timestamp = timestamp
                )
                client.post(REMOTE_ENDPOINT) {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
                Log.d(TAG, "Notification sent to remote endpoint")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send notification to remote endpoint", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == TARGET_PACKAGE) {
            activeNotificationsMap.remove(sbn.key)
            Log.d(TAG, "Notification removed from map: ${sbn.key}")
        }
    }

    private fun replyToNotification(sbn: StatusBarNotification, replyText: String) {
        val notification = sbn.notification
        val actions = notification.actions ?: return

        var replied = false
        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null) {
                for (remoteInput in remoteInputs) {
                    // Found a remote input (likely for reply)
                    sendReply(action.actionIntent, remoteInput, replyText)
                    replied = true
                    break // Stop after finding the first remote input in an action
                }
            }
            if (replied) break
        }

        if (!replied) {
             Log.w(TAG, "No reply action found for notification: ${sbn.key}")
        }
    }

    private fun sendReply(pendingIntent: PendingIntent, remoteInput: RemoteInput, replyText: String) {
        val intent = Intent()
        val bundle = Bundle()
        bundle.putCharSequence(remoteInput.resultKey, replyText)
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)

        try {
            pendingIntent.send(this, 0, intent)
            Log.d(TAG, "Replied to notification: $replyText")
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "Failed to send reply", e)
        }
    }
}
