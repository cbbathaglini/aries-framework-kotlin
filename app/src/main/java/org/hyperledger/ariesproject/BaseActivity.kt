package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.hyperledger.ariesproject.notifications.NotificationHandler

open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base)

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

        // Define ícone ativo
        updateBottomNavSelection(bottomNav)
    }

    override fun onResume() {
        super.onResume()
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        updateBottomNavSelection(bottomNav)
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
        } catch (e: Exception) {
            android.util.Log.w("BADGE", "Erro ao atualizar badge: ${e.message}")
        }
    }
}