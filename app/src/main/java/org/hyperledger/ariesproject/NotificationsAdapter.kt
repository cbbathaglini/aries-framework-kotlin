package org.hyperledger.ariesproject

import android.app.ProgressDialog
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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
            binding.actionButtons.visibility = View.VISIBLE

            when (item.type) {
                NotificationType.ISSUE_CREDENTIAL_V2 -> {
                    binding.btnAccept.visibility = View.VISIBLE
                    binding.btnDecline.visibility = View.VISIBLE
                    binding.btnCheck.visibility = View.GONE
                }
                NotificationType.ACCEPT_PROOF_REQUEST_V2 -> {
                    binding.btnAccept.visibility = View.GONE
                    binding.btnDecline.visibility = View.GONE
                    binding.btnCheck.visibility = View.VISIBLE
                }
                else -> {
                    binding.actionButtons.visibility = View.GONE
                }
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

        holder.itemView.setOnClickListener {
            notification.isRead = true
            handler.saveNotifications()
            notifyItemChanged(position)

            val bottomNavigationView =
                (context as? BaseActivity)?.findViewById<BottomNavigationView>(R.id.bottomNavigation)
            bottomNavigationView?.let {
                val badge = it.getOrCreateBadge(R.id.nav_notifications)
                val unreadCount = handler.notifications.count { n -> !n.isRead }
                if (unreadCount > 0) badge.number = unreadCount else badge.isVisible = false
            }

            when (notification.type) {
                NotificationType.ISSUED_CREDENTIAL_DETAIL_V2 -> {
                    val intent = Intent(context, CredentialDetailActivity::class.java).apply {
                        putExtra(CredentialDetailFragment.ARG_CREDENTIAL_ID, notification.credentialId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                NotificationType.ACCEPT_PROOF_REQUEST_V2 -> {
                    val intent = Intent(context, ProofRequestDetailActivity::class.java).apply {
                        putExtra(ProofRequestDetailFragment.ARG_PROOF_ID, notification.proofRecordId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                else -> {
                    // Ignora outros tipos de notificação
                    Log.d("NotificationsAdapter", "Tipo de notificação sem ação: ${notification.type}")
                }
            }
        }

        holder.itemView.findViewById<View?>(R.id.btnAccept)?.setOnClickListener {
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    if (notification.type == NotificationType.ISSUE_CREDENTIAL_V2) {
                        val record = app.agent.credentialsV2.getById(notification.credentialId!!)
                        getCredentialV2(context, record)
                    }
                    notification.isRead = true
                    notifyItemChanged(position)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context,
                        "Erro ao processar ação: ${e.localizedMessage}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // Botão Recusar → recusa credencial
        holder.itemView.findViewById<View?>(R.id.btnDecline)?.setOnClickListener {
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
                    notification.isRead = true
                    notifyItemChanged(position)
                }
            )
        }
        holder.itemView.findViewById<View?>(R.id.btnCheck)?.setOnClickListener {
            val intent = Intent(context, ProofRequestDetailActivity::class.java).apply {
                putExtra(ProofRequestDetailFragment.ARG_PROOF_ID, notification.proofRecordId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    // Atualiza lisata
    fun updateList(newList: List<NotificationItem>) {
        notifications.clear()
        notifications.addAll(newList)
        notifyDataSetChanged()
    }

    // Aceitar credencial (já existente)
    private fun getCredentialV2(context: android.content.Context, credentialExchangeRecord: CredentialExchangeRecord) {
        val app = context.applicationContext as WalletApp
        val progress = ProgressDialog(context)
        progress.setTitle("Carregando...")
        progress.setCancelable(true)
        progress.show()

        val job = GlobalScope.launch(Dispatchers.IO) {
            try {
                app.agent.credentialsV2.acceptOffer(
                    AcceptCredentialOfferOptionsV2(
                        credentialExchangeRecord = credentialExchangeRecord,
                        credentialFormats = credentialExchangeRecord.formats,
                        autoAcceptCredential = AutoAcceptCredential.Always
                    )
                )
            } catch (e: Exception) {
                GlobalScope.launch(Dispatchers.Main) {
                    progress.dismiss()
                    android.widget.Toast.makeText(context, "Erro ao aceitar credencial: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }

            GlobalScope.launch(Dispatchers.Main) { progress.dismiss() }
        }

        progress.setOnCancelListener { job.cancel() }
    }
}