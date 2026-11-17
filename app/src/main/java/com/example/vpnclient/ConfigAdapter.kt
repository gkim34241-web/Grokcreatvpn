package com.example.vpnclient

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class ConfigAdapter(
    private val items: List<VpnConfig>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ConfigAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.card)
        val name: TextView = view.findViewById(R.id.configName)
        val host: TextView = view.findViewById(R.id.configHost)
        val latency: TextView = view.findViewById(R.id.configLatency)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_config, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.host.text = item.host
        holder.latency.text = when {
            item.latency < 0 -> "-- ms"
            else -> "${item.latency} ms"
        }

        if (item.selected) {
            holder.card.setCardBackgroundColor(Color.parseColor("#E3F2FD"))
        } else {
            holder.card.setCardBackgroundColor(Color.WHITE)
        }

        holder.itemView.setOnClickListener { onClick(position) }
    }

    override fun getItemCount() = items.size
}
