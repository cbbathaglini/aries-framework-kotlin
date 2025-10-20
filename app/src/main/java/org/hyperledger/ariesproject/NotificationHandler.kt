package org.hyperledger.ariesproject.notifications

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
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

    // 🧠 Registro de callbacks (ouvintes)
    fun addOnNotificationsChangedListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    // 🟢 Adiciona nova notificação
    fun addNotification(
        title: String,
        message: String,
        type: NotificationType = NotificationType.OTHER,
        credentialId: String? = null,
        proofRecordId: String? = null,
        presentationMessageId: String? = null,
        connectionId:String? = null
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
        notifyListeners() // ✅ Atualiza a UI automaticamente
    }

    // 🟡 Marca todas como lidas
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
        android.util.Log.d("NOTIFICATION_HANDLER", "💾 Salvou ${_notifications.size} notificações")
    }

    fun loadFromStorage() {
        loadNotifications()
    }

    private fun loadNotifications() {
        val jsonString = prefs.getString("notifications_list", null)
        if (jsonString.isNullOrEmpty()) {
            _notifications = mutableListOf()
            android.util.Log.d("NOTIFICATION_HANDLER", "⚠️ Nenhuma notificação salva")
            return
        }

        try {
            val loaded = json.decodeFromString<MutableList<NotificationItem>>(jsonString)
            _notifications.clear()
            _notifications.addAll(loaded)
            android.util.Log.d("NOTIFICATION_HANDLER", "📥 Carregadas ${_notifications.size} notificações")
        } catch (e: Exception) {
            e.printStackTrace()
            _notifications = mutableListOf()
            android.util.Log.e("NOTIFICATION_HANDLER", "❌ Erro ao carregar notificações: ${e.message}")
        }
    }


}