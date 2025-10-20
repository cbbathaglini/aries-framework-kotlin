package org.hyperledger.ariesproject

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import org.hyperledger.ariesproject.databinding.ActivityNotificationsBinding
import org.hyperledger.ariesproject.notifications.NotificationHandler

class NotificationsActivity : BaseActivity() {
    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var adapter: NotificationsAdapter
    private lateinit var handler: NotificationHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val inflater = layoutInflater
        val baseContainer = findViewById<FrameLayout>(R.id.baseContainer)
        binding = ActivityNotificationsBinding.inflate(inflater)
        baseContainer.addView(binding.root)

        // ✅ Usa o NotificationHandler global do Application
        handler = (application as WalletApp).notificationHandler
        adapter = NotificationsAdapter(mutableListOf())

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Botão "Voltar"
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 🔔 Listener para mudanças
        handler.addOnNotificationsChangedListener {
            runOnUiThread { reloadNotifications() }
        }

        val markAllButton = findViewById<MaterialButton>(R.id.markAllReadButton)
        markAllButton.setOnClickListener {
            handler.markAllAsRead()
            reloadNotifications()
            updateNotificationBadge()

            val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigation)
            val badge = bottomNavigationView.getOrCreateBadge(R.id.nav_notifications)
            badge.isVisible = false
        }
        // Botão "Limpar todas"
        binding.clearAll.setOnClickListener {
            handler.clearAll()
            reloadNotifications()
            updateNotificationBadge()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("NOTIFICATIONS", "onResume chamado — recarregando lista")
        reloadNotifications()
        updateNotificationBadge()
    }

    private fun reloadNotifications() {
        handler.loadFromStorage()
        val list = handler.notifications
        Log.i("NOTIFICATIONS", "Carregadas ${list.size} notificações")

        adapter.updateList(list)
        adapter.notifyDataSetChanged()

        binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        val isEmpty = handler.notifications.isEmpty()
        binding.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun updateNotificationBadge() {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        val badge = bottomNavigationView.getOrCreateBadge(R.id.nav_notifications)
        val unreadCount = handler.notifications.count { !it.isRead }

        if (unreadCount > 0) {
            badge.number = unreadCount
            badge.isVisible = true
        } else {
            badge.clearNumber()
            badge.isVisible = false
        }
    }
}