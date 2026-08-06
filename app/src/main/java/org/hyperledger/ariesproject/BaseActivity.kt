package org.hyperledger.ariesproject

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.hyperledger.ariesproject.notifications.NotificationHandler

open class BaseActivity : AppCompatActivity() {

    companion object {
        const val BADGE_UPDATE_ACTION = "org.hyperledger.ariesproject.UPDATE_BADGE"
    }

    private val badgeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BADGE_UPDATE_ACTION) {
                Log.d("BADGE", "Updating badge via broadcast in ${this@BaseActivity::class.simpleName}")
                updateNotificationBadge()
            }
        }
    }

    private var badgeReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    if (this !is WalletMainActivity) {
                        startActivity(Intent(this, WalletMainActivity::class.java))
                        overridePendingTransition(0, 0)
                        finish()
                    }
                    true
                }

                R.id.nav_notifications -> {
                    if (this !is NotificationsActivity) {
                        startActivity(Intent(this, NotificationsActivity::class.java))
                        overridePendingTransition(0, 0)
                        finish()
                    }
                    true
                }

                else -> false
            }
        }

        // Set active icon
        updateBottomNavSelection(bottomNav)
    }

    override fun onResume() {
        super.onResume()
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        updateBottomNavSelection(bottomNav)

        val filter = IntentFilter(BADGE_UPDATE_ACTION)
        ContextCompat.registerReceiver(
            this,
            badgeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        badgeReceiverRegistered = true
    }

    override fun onPause() {
        if (badgeReceiverRegistered) {
            unregisterReceiver(badgeReceiver)
            badgeReceiverRegistered = false
        }
        super.onPause()
    }

    protected fun setChildContent(layoutResId: Int) {
        val container = findViewById<android.widget.FrameLayout>(R.id.baseContainer)
        layoutInflater.inflate(layoutResId, container, true)
    }

    private fun updateBottomNavSelection(bottomNav: BottomNavigationView) {
        when (this) {
            is WalletMainActivity -> bottomNav.selectedItemId = R.id.nav_home
            is NotificationsActivity -> bottomNav.selectedItemId = R.id.nav_notifications
            else -> {
                // ⚡ Reinicia o estado interno do BottomNavigationView
                bottomNav.menu.setGroupCheckable(0, false, true)
                bottomNav.menu.findItem(R.id.nav_home).isChecked = false
                bottomNav.menu.findItem(R.id.nav_notifications).isChecked = false
                bottomNav.menu.setGroupCheckable(0, true, true)
            }
        }
    }

    protected fun clearBottomNavigationSelection() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.menu.setGroupCheckable(0, false, true)
        bottomNav.menu.findItem(R.id.nav_home).isChecked = false
        bottomNav.menu.findItem(R.id.nav_notifications).isChecked = false
        bottomNav.menu.setGroupCheckable(0, true, true)
    }

    open fun updateNotificationBadge() {
        try {
            val handler = (application as WalletApp).notificationHandler
            val unreadCount = handler.notifications.count { !it.isRead }

            val bottomNavigationView = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
                R.id.bottomNavigation
            )
            val badge = bottomNavigationView.getOrCreateBadge(R.id.nav_notifications)

            if (unreadCount > 0) {
                badge.number = unreadCount
                badge.isVisible = true
            } else {
                badge.clearNumber()
                badge.isVisible = false
            }

            Log.d("BADGE", "bottomNavigationView: $bottomNavigationView")

        } catch (e: Exception) {
            android.util.Log.w("BADGE", "Erro ao atualizar badge: ${e.message}")
        }
    }

    fun runOnConfirm(
        message: String,
        action: () -> Unit,
        negAction: (() -> Unit)? = null
    ) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> action() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> negAction?.invoke() }
            .show()
    }
}
