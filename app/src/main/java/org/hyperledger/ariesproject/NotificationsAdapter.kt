package org.hyperledger.ariesproject

import android.app.ProgressDialog
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.credentials.models.AcceptCredentialOfferOptionsV2
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential
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

            val context = binding.root.context
            val colorRes = if (item.isRead)
                android.R.color.darker_gray
            else
                R.color.teal_700

            binding.title.setTextColor(context.getColor(colorRes))

            if (item.type == NotificationType.ISSUE_CREDENTIAL_V2) {
                binding.actionButtons.visibility = View.VISIBLE
            } else {
                binding.actionButtons.visibility = View.GONE
            }
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
        val notification = notifications[position]
        val context = holder.itemView.context
        val app = context.applicationContext as WalletApp
        val handler = app.notificationHandler

        holder.bind(notification)

        // ✅ Marca como lida ao clicar no item (não nos botões)
        holder.itemView.setOnClickListener {
            notification.isRead = true
            handler.saveNotifications()
            notifyItemChanged(position)

            // 🔄 Atualiza badge do sino
            val bottomNavigationView =
                (context as? BaseActivity)?.findViewById<BottomNavigationView>(R.id.bottomNavigation)
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
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        try {
                            val record =
                                app.agent.connectionRepository.getById(notification.connectionId!!)
                            val wrapper = ConnectionRecordWrapper(record)

                            val intent =
                                Intent(context, HistoricalDetailActivity::class.java).apply {
                                    putExtra(
                                        HistoricalDetailFragment.ARG_CONNECTION_ID,
                                        notification.connectionId
                                    )
                                    putExtra(
                                        HistoricalDetailFragment.ARG_CONNECTION_RECORD,
                                        wrapper
                                    )
                                }
                            context.startActivity(intent)

                        } catch (e: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                "Erro ao abrir conexão: ${e.localizedMessage}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                NotificationType.ISSUE_CREDENTIAL_V2 -> {
                    Log.d("NotificationsAdapter", "Notificação de credencial v2 exibida")
                }

                NotificationType.ISSUED_CREDENTIAL_DETAIL_V2 -> {
                    Log.d("NotificationsAdapter", "Abrindo detalhe da credencial ${notification.credentialId}")

                    val intent = Intent(context, CredentialDetailActivity::class.java).apply {
                        putExtra(CredentialDetailFragment.ARG_CREDENTIAL_ID, notification.credentialId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    context.startActivity(intent)
                }

                else -> {
                    Log.d("NotificationsAdapter", "Notificação clicada: ${notification.type}")
                }
            }
        }

        // dentro de onBindViewHolder()

        val btnAccept = holder.itemView.findViewById<View?>(R.id.btnAccept)
        val btnDecline = holder.itemView.findViewById<View?>(R.id.btnDecline)


        btnAccept?.setOnClickListener {
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    val context = holder.itemView.context
                    val record = app.agent.credentialsV2.getById(notification.credentialId!!)
                    getCredentialV2(context, record)

                    handler.addNotification(
                        title = "Credencial aceita",
                        message = "A credencial foi aceita com sucesso.",
                        type = NotificationType.ISSUED_CREDENTIAL_DETAIL_V2,
                        connectionId = notification.connectionId,
                        credentialId = notification.credentialId
                    )

                    // Oculta os botões após a ação
                    notification.isRead = true
                    notifyItemChanged(position)

                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context,
                        "Erro ao aceitar credencial: ${e.localizedMessage}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        btnDecline?.setOnClickListener {
            (context as? BaseActivity)?.runOnConfirm(
                "Recusar credencial?",
                action = {
                    if (context is WalletMainActivity) {
                        context.declineCredentialV2(notification.credentialId!!)
                    }

                    handler.addNotification(
                        title = "Credencial recusada",
                        message = "A credencial foi recusada pelo usuário.",
                        type = NotificationType.ISSUED_CREDENTIAL_DETAIL_V2,
                        connectionId = notification.connectionId,
                        credentialId = notification.credentialId
                    )

                    // Oculta os botões após a ação
                    notification.isRead = true
                    notifyItemChanged(position)
                }
            )
        }

//        holder.itemView.findViewById<View?>(R.id.btnAccept)?.setOnClickListener {
//                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
//                    try {
//                        val context = holder.itemView.context
//                        val record =
//                            app.agent.credentialsV2.getById(notification.credentialId!!)
//                            getCredentialV2(context= context, credentialExchangeRecord= record)
//                    } catch (e: Exception) {
//                        android.widget.Toast.makeText(
//                            context,
//                            "Erro ao aceitar credencial: ${e.localizedMessage}",
//                            android.widget.Toast.LENGTH_LONG
//                        ).show()
//                    }
//                }
//
//        }
//                },
//                negAction = {
//                    if (context is WalletMainActivity) {
//                        context.declineCredentialV2(notification.credentialId!!)
//                    }
//                }
//            )
//        }

        holder.itemView.findViewById<View?>(R.id.btnDecline)?.setOnClickListener {
            (context as? BaseActivity)?.runOnConfirm(
                "Recusar credencial?",
                action = {
                    if (context is WalletMainActivity) {
                        context.declineCredentialV2(notification.credentialId!!)
                    }
                }
            )
        }
    }

    fun updateList(newList: List<NotificationItem>) {
        notifications.clear()
        notifications.addAll(newList)
        notifyDataSetChanged()
    }

    private fun getCredentialV2(context: android.content.Context, credentialExchangeRecord: CredentialExchangeRecord) {
        Log.i("CV2", "HERE")

        val app = context.applicationContext as WalletApp
        val progress = ProgressDialog(context)
        progress.setTitle("Carregando...")
        progress.setCancelable(true)
        progress.show()

        val job = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val connectionRecordList = app.agent.connectionRepository.getAll()
                Log.i("connectionRecordList", connectionRecordList.toString())

                val connectionRecord =
                    app.agent.connectionRepository.getById(credentialExchangeRecord.connectionId!!)
                Log.i("IDD", connectionRecord.toString())

                app.agent.credentialsV2.acceptOffer(
                    AcceptCredentialOfferOptionsV2(
                        credentialExchangeRecord = credentialExchangeRecord,
                        credentialFormats = credentialExchangeRecord.formats,
                        autoAcceptCredential = AutoAcceptCredential.Always,
                    )
                )

            } catch (e: Exception) {
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    Log.e("getCredentialV2", e.localizedMessage ?: "Erro desconhecido")
                    progress.dismiss()
                    android.widget.Toast.makeText(context, "Falha ao receber a credencial.", android.widget.Toast.LENGTH_LONG).show()
                }
            }

            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                progress.dismiss()
                //android.widget.Toast.makeText(context, "Credencial aceita com sucesso!", android.widget.Toast.LENGTH_LONG).show()
            }
        }

        progress.setOnCancelListener {
            job.cancel()
        }
    }
}