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
import java.text.SimpleDateFormat
import java.util.*

class NotificationsAdapter(
    private val notifications: MutableList<NotificationItem>
) : RecyclerView.Adapter<NotificationsAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationItem) {
            binding.title.text = item.title
            binding.message.text = item.message

            val dateFormatted = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault())
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
                    val credentialReceived = notifications.any { notif ->
                        notif.type == NotificationType.ISSUED_CREDENTIAL_DETAIL_V2 &&
                                notif.credentialId == item.credentialId
                    }

                    val credentialDeclined = notifications.any { notif ->
                        notif.type == NotificationType.CREDENTIAL_DECLINED &&
                                notif.credentialId == item.credentialId
                    }

                    when {
                        credentialReceived -> {
                            binding.btnAccept.visibility = View.GONE
                            binding.btnDecline.visibility = View.GONE
                            binding.btnCheck.visibility = View.GONE
                            binding.txtStatus.apply {
                                visibility = View.VISIBLE
                                text = "✅ Credential received"
                                setTextColor(context.getColor(R.color.teal_700))
                            }
                        }
                        credentialDeclined -> {
                            binding.btnAccept.visibility = View.GONE
                            binding.btnDecline.visibility = View.GONE
                            binding.btnCheck.visibility = View.GONE
                            binding.txtStatus.apply {
                                visibility = View.VISIBLE
                                text = "❌ Credential declined"
                                setTextColor(context.getColor(android.R.color.holo_red_dark))
                            }
                        }
                        else -> {
                            binding.btnAccept.visibility = View.VISIBLE
                            binding.btnDecline.visibility = View.VISIBLE
                            binding.btnCheck.visibility = View.GONE
                            binding.txtStatus.visibility = View.GONE
                        }
                    }
                }

                NotificationType.ISSUED_CREDENTIAL_DETAIL_V2 -> {
                    binding.btnAccept.visibility = View.GONE
                    binding.btnDecline.visibility = View.GONE
                    binding.btnCheck.visibility = View.VISIBLE
                    binding.txtStatus.visibility = View.GONE
                    binding.btnCheck.text = "View credential"
                }

                NotificationType.CREDENTIAL_DECLINED -> {
                    binding.btnAccept.visibility = View.GONE
                    binding.btnDecline.visibility = View.GONE
                    binding.btnCheck.visibility = View.VISIBLE
                    binding.txtStatus.visibility = View.GONE
                    binding.btnCheck.text = "View declined credential"
                }

                NotificationType.ACCEPT_PROOF_REQUEST_V2 -> {
                    val proofDoneExists = notifications.any { notif ->
                        notif.type == NotificationType.PROOF_REQUEST_V2 &&
                                notif.proofRecordId == item.proofRecordId
                    }

                    val proofDeclinedExists = notifications.any { notif ->
                        notif.type == NotificationType.PROOF_DECLINED &&
                                notif.proofRecordId == item.proofRecordId
                    }

                    when {
                        proofDoneExists -> {
                            binding.btnAccept.visibility = View.GONE
                            binding.btnDecline.visibility = View.GONE
                            binding.btnCheck.visibility = View.GONE
                            binding.txtStatus.apply {
                                visibility = View.VISIBLE
                                text = "✅ Proof completed"
                                setTextColor(context.getColor(R.color.teal_700))
                            }
                        }

                        proofDeclinedExists -> {
                            binding.btnAccept.visibility = View.GONE
                            binding.btnDecline.visibility = View.GONE
                            binding.btnCheck.visibility = View.GONE
                            binding.txtStatus.apply {
                                visibility = View.VISIBLE
                                text = "❌ Proof declined"
                                setTextColor(context.getColor(android.R.color.holo_red_dark))
                            }
                        }

                        else -> {
                            binding.btnAccept.visibility = View.VISIBLE
                            binding.btnDecline.visibility = View.VISIBLE
                            binding.btnCheck.visibility = View.GONE
                            binding.txtStatus.visibility = View.GONE
                            binding.btnAccept.text = "View details"
                            binding.btnDecline.text = "Decline"
                        }
                    }
                }

                NotificationType.PROOF_REQUEST_V2 -> {
                    binding.btnAccept.visibility = View.GONE
                    binding.btnDecline.visibility = View.GONE
                    binding.btnCheck.visibility = View.VISIBLE
                    binding.txtStatus.visibility = View.GONE
                    binding.btnCheck.text = "View proof"
                }

                else -> {
                    binding.actionButtons.visibility = View.GONE
                    binding.txtStatus.visibility = View.GONE
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

                NotificationType.ACCEPT_PROOF_REQUEST_V2,
                NotificationType.PROOF_REQUEST_V2 -> {
                    val intent = Intent(context, ProofRequestDetailActivity::class.java).apply {
                        putExtra(ProofRequestDetailFragment.ARG_PROOF_ID, notification.proofRecordId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }

                else -> {
                    Log.d("NotificationsAdapter", "Notification type with no direct action: ${notification.type}")
                }
            }
        }

        holder.itemView.findViewById<View?>(R.id.btnAccept)?.setOnClickListener {
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    when (notification.type) {

                        NotificationType.ISSUE_CREDENTIAL_V2 -> {
                            val record =
                                app.agent.credentialsV2.getById(notification.credentialId!!)
                            getCredentialV2(context, record)
                        }

                        NotificationType.ACCEPT_PROOF_REQUEST_V2 -> {
                            val intent = Intent(context, ProofRequestDetailActivity::class.java).apply {
                                putExtra(ProofRequestDetailFragment.ARG_PROOF_ID, notification.proofRecordId)
                                putExtra(ProofRequestDetailFragment.ARG_TYPE, notification.type.name)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }

                        else -> {
                            Log.d("NotificationsAdapter", "btnAccept clicked for: ${notification.type}")
                        }
                    }

                    notification.isRead = true
                    notifyItemChanged(position)

                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context,
                        "Error: ${e.localizedMessage}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        holder.itemView.findViewById<View?>(R.id.btnDecline)?.setOnClickListener {
            if (notification.type == NotificationType.ACCEPT_PROOF_REQUEST_V2) {
                val appContext = context.applicationContext
                if (appContext is WalletApp && notification.proofRecordId != null) {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            appContext.agent.proofCommandV2.declineRequest(notification.proofRecordId!!)
                            handler.addNotification(
                                title = "Proof declined",
                                message = "Proof (${notification.proofRecordId}) was declined",
                                type = NotificationType.PROOF_DECLINED,
                                connectionId = notification.connectionId,
                                proofRecordId = notification.proofRecordId
                            )
                        } catch (e: Exception) {
                            Log.e("NotificationsAdapter", "Decline error: ${e.localizedMessage}")
                        }
                    }
                }

                notification.isRead = true
                notifyItemChanged(position)

            } else {
                val appContext = context.applicationContext
                if (appContext is WalletApp) {
                    appContext.declineCredentialV2(notification.credentialId!!)
                }
                handler.addNotification(
                    title = "Credential Declined",
                    message = "The credential was declined by the user",
                    type = NotificationType.CREDENTIAL_DECLINED,
                    connectionId = notification.connectionId,
                    credentialId = notification.credentialId
                )

                notification.isRead = true
                notifyItemChanged(position)
            }
        }

        holder.itemView.findViewById<View?>(R.id.btnCheck)?.setOnClickListener {
            when (notification.type) {

                NotificationType.ISSUED_CREDENTIAL_DETAIL_V2,
                NotificationType.CREDENTIAL_DECLINED -> {
                    val intent = Intent(context, CredentialDetailActivity::class.java).apply {
                        putExtra(CredentialDetailFragment.ARG_CREDENTIAL_ID, notification.credentialId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }

                NotificationType.PROOF_REQUEST_V2,
                NotificationType.ACCEPT_PROOF_REQUEST_V2,
                NotificationType.PROOF_DECLINED -> {
                    val intent = Intent(context, ProofRequestDetailActivity::class.java).apply {
                        putExtra(ProofRequestDetailFragment.ARG_PROOF_ID, notification.proofRecordId)
                        putExtra(ProofRequestDetailFragment.ARG_TYPE, notification.type)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }

                else -> {
                    Log.d("NotificationsAdapter", "btnCheck clicked for: ${notification.type}")
                }
            }
        }
    }

    fun updateList(newList: List<NotificationItem>) {
        notifications.clear()
        notifications.addAll(newList)
        notifyDataSetChanged()
    }

    private fun getCredentialV2(
        context: android.content.Context,
        credentialExchangeRecord: CredentialExchangeRecord
    ) {
        val app = context.applicationContext as WalletApp
        val progress = ProgressDialog(context)
        progress.setTitle("Loading...")
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
                    android.widget.Toast.makeText(
                        context,
                        "Error accepting credential: ${e.localizedMessage}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }

            GlobalScope.launch(Dispatchers.Main) { progress.dismiss() }
        }

        progress.setOnCancelListener { job.cancel() }
    }
}