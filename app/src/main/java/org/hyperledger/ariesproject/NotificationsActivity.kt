package org.hyperledger.ariesproject

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
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

        handler = (application as WalletApp).notificationHandler
        adapter = NotificationsAdapter(mutableListOf())

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { goHome() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goHome()
                }
            }
        )

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

        binding.clearAll.setOnClickListener {
            handler.clearAll()
            reloadNotifications()
            updateNotificationBadge()
        }
    }

    override fun onResume() {
        super.onResume()

        // Log.d: Informs that the activity resumed and notifications will be reloaded.
        Log.d("NOTIFICATIONS", "onResume called — reloading list")

        reloadNotifications()
        updateNotificationBadge()
    }

    private fun reloadNotifications() {
        handler.loadFromStorage()
        val list = handler.notifications

        // Log.i: Shows how many notifications were loaded from storage.
        Log.i("NOTIFICATIONS", "Loaded ${list.size} notifications")

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

    private fun goHome() {
        startActivity(
            android.content.Intent(this, WalletMainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        finish()
        overridePendingTransition(0, 0)
    }
}
