package org.hyperledger.ariesproject

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import org.hyperledger.ariesproject.databinding.ItemNotificationBinding
import org.hyperledger.ariesproject.wrapper.ConnectionRecordWrapper
import java.text.SimpleDateFormat
import java.util.*

class NotificationsAdapter(
    private val notifications: MutableList<NotificationItem>
) : RecyclerView.Adapter<NotificationsAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationItem) {
            Log.d("ADAPTER_BIND", "Exibindo: ${item.title}")
            binding.title.text = item.title
            binding.message.text = item.message

            val dateFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(Date(item.date))
            binding.date.text = dateFormatted

            // 🔹 Cor do título indica se está lido ou não
            val context = binding.root.context
            val colorRes = if (item.isRead)
                android.R.color.darker_gray
            else
                R.color.teal_700

            binding.title.setTextColor(context.getColor(colorRes))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = notifications.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(notifications[position])

        holder.itemView.setOnClickListener {
            val notification = notifications[position]
            val context = holder.itemView.context
            val app = context.applicationContext as WalletApp
            val handler = app.notificationHandler

            // ✅ Marca a notificação como lida
            notification.isRead = true
            handler.saveNotifications()

            notifyItemChanged(position) // Atualiza a cor do título

            // 🔄 Atualiza badge
            val bottomNavigationView = (context as? BaseActivity)?.findViewById<BottomNavigationView>(R.id.bottomNavigation)
            bottomNavigationView?.let {
                val badge = it.getOrCreateBadge(R.id.nav_notifications)
                val unreadCount = handler.notifications.count { n -> !n.isRead }
                if (unreadCount > 0) {
                    badge.number = unreadCount
                } else {
                    badge.isVisible = false
                }
            }



            when (notification.type) {
                NotificationType.CONNECTION -> {
                    val context = holder.itemView.context
                    val app = context.applicationContext as WalletApp

                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        try {
                            val record = app.agent.connectionRepository.getById(notification.connectionId!!)
                            val wrapper = ConnectionRecordWrapper(record)

                            val intent = Intent(context, HistoricalDetailActivity::class.java).apply {
                                putExtra(HistoricalDetailFragment.ARG_CONNECTION_ID, notification.connectionId)
                                putExtra(HistoricalDetailFragment.ARG_CONNECTION_RECORD, wrapper)
//                                putExtra(HistoricalDetailFragment.ARG_CONNECTION_THREADID, wrapper.threadId)
//                                putExtra(HistoricalDetailFragment.ARG_CONNECTION_MEDIATORID, wrapper.mediatorId)
                            }

                            context.startActivity(intent)

                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Erro ao abrir conexão: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                        }


                    }
                }

                else -> {
                    Log.d("NotificationsAdapter", "Notificação clicada: ${notification.type}")
                }

            }
        }
    }

    // 🔄 Atualiza lista com segurança (chame no listener do NotificationHandler)
    fun updateList(newList: List<NotificationItem>) {
        notifications.clear()
        notifications.addAll(newList)
        notifyDataSetChanged()
    }
}