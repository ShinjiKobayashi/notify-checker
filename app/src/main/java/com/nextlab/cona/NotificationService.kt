package com.nextlab.cona

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class NotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationService"
    }

    // Map to store active notifications suitable for reply. Key: Title (Sender Name)
    // We use a TreeMap with CASE_INSENSITIVE_ORDER to handle bridges sending "john doe" instead of "John Doe".
    private val activeNotifications = java.util.Collections.synchronizedMap(java.util.TreeMap<String, StatusBarNotification>(String.CASE_INSENSITIVE_ORDER))
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification Listener Connected")
        BridgeServer.start(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        BridgeServer.stop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val tag = sbn.tag

        Log.d(TAG, "Notification received from: $packageName, Title: $title, Text: $text")

        if (title != null && text != null && !text.contains("Auto reply")) {
            // Check if this notification has reply actions
            if (hasReplyAction(sbn)) {
                activeNotifications[title] = sbn
            }

            // Send to Bridge
            scope.launch {
                BridgeClient.sendNotificationEvent(packageName, title, text, tag)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification removed: ${sbn.packageName}")
        // We generally keep the notification in map even if removed from tray,
        // to allow replying to "dismissed" messages if the pending intent is still valid.
        // However, standard behavior might be to remove it.
        // For this bridge, let's keep it to maximize reply chances, or until a new one replaces it.
    }

    fun replyToMessage(title: String, replyText: String): Boolean {
        val sbn = activeNotifications[title]
        if (sbn == null) {
            Log.w(TAG, "No active notification found for title: $title")
            return false
        }
        return replyToNotification(sbn, replyText)
    }

    private fun hasReplyAction(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        val actions = notification.actions ?: return false
        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                return true
            }
        }
        return false
    }

    private fun replyToNotification(sbn: StatusBarNotification, replyText: String): Boolean {
        val notification = sbn.notification
        val actions = notification.actions ?: return false

        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null) {
                for (remoteInput in remoteInputs) {
                    sendReply(action.actionIntent, remoteInput, replyText)
                    return true
                }
            }
        }
        return false
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
