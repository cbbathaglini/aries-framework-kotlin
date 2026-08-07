package org.hyperledger.ariesproject

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesproject.databinding.ProofListContentBinding
import org.hyperledger.ariesframework.proofs.models.ProofState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProofAdapter(
    private val parentActivity: ProofListActivity,
    private val values: List<ProofExchangeRecord>
) : RecyclerView.Adapter<ProofAdapter.ViewHolder>() {

    private val onClickListener = View.OnClickListener { v ->
        val item = v.tag as ProofExchangeRecord
        val intent = Intent(v.context, ProofDetailActivity::class.java).apply {
            putExtra(ProofDetailFragment.ARG_PROOF_ID, item.id)
        }
        v.context.startActivity(intent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.proof_list_content, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        val dateString = item.createdAt?.let {
            SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.US)
                .format(Date(it.toEpochMilliseconds()))
        } ?: "-"

        holder.contentView.text = "Proof ${item.id}"
        holder.dateView.text = dateString
        holder.typeView.text = formatState(item.state)

        val color = colorForState(item.state)
        holder.typeView.background = badgeBackground(color)
        holder.typeView.setTextColor(Color.WHITE)

        with(holder.itemView) {
            tag = item
            setOnClickListener(onClickListener)
        }
    }

    private fun colorForState(state: ProofState): Int {
        return when (state) {
            ProofState.Done -> parentActivity.getColor(R.color.status_verified)
            ProofState.Abandoned -> parentActivity.getColor(R.color.status_failed)
            ProofState.RequestReceived,
            ProofState.RequestSent -> parentActivity.getColor(R.color.status_pending)
            ProofState.PresentationSent -> parentActivity.getColor(R.color.teal_700)
            ProofState.ProposalSent,
            ProofState.ProposalReceived -> parentActivity.getColor(R.color.gray_700)
            else -> parentActivity.getColor(R.color.gray_500)
        }
    }

    private fun formatState(state: ProofState): String {
        return state.name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
    }

    private fun badgeBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = parentActivity.resources.displayMetrics.density * 12
            setColor(color)
        }
    }

    override fun getItemCount() = values.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val binding = ProofListContentBinding.bind(view)
        val contentView: TextView = binding.content
        val dateView: TextView = binding.date
        val typeView: TextView = binding.type
    }
}
