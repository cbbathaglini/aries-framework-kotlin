package org.hyperledger.ariesproject

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesproject.databinding.ProofListContentBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ProofAdapter(
    private val parentActivity: ProofListActivity,
    private val values: List<ProofExchangeRecord>
) : RecyclerView.Adapter<ProofAdapter.ViewHolder>() {

    private val onClickListener = View.OnClickListener { v ->
        val item = v.tag as ProofExchangeRecord
        val intent = Intent(v.context, ProofDetailActivity::class.java).apply {
            putExtra("PROOF_ID", item.id)
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
        val dateString = item.createdAt?.toString() ?: ""

        // Preenche os campos da célula
        holder.contentView.text = "Proof ID: ${item.id}"
        holder.dateView.text = "Criado em: $dateString"
        holder.typeView.text = "Estado: ${item.state}"

        // Configura o clique
        with(holder.itemView) {
            tag = item
            setOnClickListener(onClickListener)
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