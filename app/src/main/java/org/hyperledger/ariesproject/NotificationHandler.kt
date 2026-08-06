package org.hyperledger.ariesproject.notifications

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import org.hyperledger.ariesproject.NotificationItem
import org.hyperledger.ariesproject.NotificationType

class NotificationHandler private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: NotificationHandler? = null

        fun getInstance(context: Context): NotificationHandler {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = NotificationHandler(context.applicationContext)
                    }
                }
            }
            return INSTANCE!!
        }
    }

    private val prefs = context.getSharedPreferences("notifications_prefs", Context.MODE_PRIVATE)
    private val listeners = mutableListOf<() -> Unit>()

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var _notifications: MutableList<NotificationItem> = mutableListOf()
    val notifications: List<NotificationItem>
        get() = _notifications

    init {
        loadNotifications()
    }

    fun addOnNotificationsChangedListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    fun addNotification(
        title: String,
        message: String,
        type: NotificationType = NotificationType.OTHER,
        credentialId: String? = null,
        proofRecordId: String? = null,
        presentationMessageId: String? = null,
        connectionId: String? = null
    ) {
        val item = NotificationItem(
            title = title,
            message = message,
            type = type,
            credentialId = credentialId,
            proofRecordId = proofRecordId,
            presentationMessageId = presentationMessageId,
            connectionId = connectionId
        )

        _notifications.add(0, item)
        saveNotifications()
        notifyListeners()
    }

    fun markAllAsRead() {
        _notifications.forEach { it.isRead = true }
        saveNotifications()
        notifyListeners()
    }

    fun clearAll() {
        _notifications.clear()
        saveNotifications()
        notifyListeners()
    }

    fun saveNotifications() {
        val jsonString = json.encodeToString(_notifications)
        prefs.edit().putString("notifications_list", jsonString).apply()

        Log.d("NOTIFICATION_HANDLER", "Saved ${_notifications.size} notifications")
    }

    fun loadFromStorage() {
        loadNotifications()
    }

    private fun loadNotifications() {
        val jsonString = prefs.getString("notifications_list", null)
        if (jsonString.isNullOrEmpty()) {
            _notifications = mutableListOf()

            Log.d("NOTIFICATION_HANDLER", "No saved notifications")
            return
        }

        try {
            val loaded = json.decodeFromString<MutableList<NotificationItem>>(jsonString)
            _notifications.clear()
            _notifications.addAll(loaded)

            Log.d("NOTIFICATION_HANDLER", "Loaded ${_notifications.size} notifications")
        } catch (e: Exception) {
            e.printStackTrace()
            _notifications = mutableListOf()

            Log.e("NOTIFICATION_HANDLER", "Error loading notifications: ${e.message}")
        }
    }
}