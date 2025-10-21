package org.hyperledger.ariesproject

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class LogsAdapter(private val items: MutableList<String>) :
    RecyclerView.Adapter<LogsAdapter.LogVH>() {

    class LogVH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogVH {
        val tv = TextView(parent.context).apply {
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            textSize = 12f
            typeface = Typeface.MONOSPACE
        }
        return LogVH(tv)
    }

    override fun onBindViewHolder(holder: LogVH, position: Int) {
        holder.tv.text = items[position]
    }

    override fun getItemCount(): Int = items.size

    fun add(line: String) {
        items.add(line)
        notifyItemInserted(items.size - 1)
    }
}