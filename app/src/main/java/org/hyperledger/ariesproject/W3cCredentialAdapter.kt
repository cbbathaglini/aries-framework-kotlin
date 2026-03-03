package org.hyperledger.ariesproject

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRecord
import org.hyperledger.ariesproject.databinding.ItemW3cCredentialBinding

class W3cCredentialAdapter(
    private val items: MutableList<W3cCredentialRecord> = mutableListOf()
) : RecyclerView.Adapter<W3cCredentialAdapter.VH>() {

    fun submit(newItems: List<W3cCredentialRecord>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemW3cCredentialBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class VH(private val b: ItemW3cCredentialBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(record: W3cCredentialRecord) {
            val c = record.credential

            val types = c.type.joinToString(", ")
            val title = c.type.lastOrNull() ?: "W3C Credential"

            b.titleText.text = title
            b.subtitleText.text = "Issuer: ${c.issuer.toString().trim('"')}"
            b.metaText.text = "Issued: ${c.issuanceDate}"
            b.idText.text = "id: ${c.id ?: record.id}"

            b.btnCopy.setOnClickListener {
                val json = try { c.toJson() } catch (_: Exception) { c.toString() }
                copyToClipboard(b.root.context, "w3c_vc", json)
            }
        }

        private fun copyToClipboard(context: Context, label: String, text: String) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }
}