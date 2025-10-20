package org.hyperledger.ariesproject

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.hyperledger.ariesproject.databinding.ItemNotificationBinding
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
            val notification : NotificationItem = notifications[position]
            when (notification.type) {
                NotificationType.CONNECTION -> {
                    val context = holder.itemView.context
                    val intent = Intent(context, HistoricalDetailActivity::class.java).apply {
                        putExtra(HistoricalDetailFragment.ARG_CONNECTION_ID, notification.connectionId)
                        // se você armazena o ConnectionRecordWrapper, pode também colocar:
                        // putParcelable(HistoricalDetailFragment.ARG_CONNECTION_RECORD, notification.connectionRecordWrapper)
                    }
                    context.startActivity(intent)
                }

                else -> {
                    // comportamento padrão — exibir alerta, ou ignorar
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