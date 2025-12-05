package com.nextlab.cona

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationService"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification Listener Connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        Log.d(TAG, "Notification received from: $packageName, Title: $title, Text: $text")

        // Auto-reply logic
        if (text != null && !text.contains("Auto reply")) {
            replyToNotification(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification removed: ${sbn.packageName}")
    }

    private fun replyToNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val actions = notification.actions ?: return

        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null) {
                for (remoteInput in remoteInputs) {
                    // Found a remote input (likely for reply)
                    sendReply(action.actionIntent, remoteInput, "Auto reply: Hello")
                    return // Stop after sending one reply
                }
            }
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
