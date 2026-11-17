package com.example.vpnclient

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
        val protocol: TextView = view.findViewById(R.id.configProtocol)
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
        holder.protocol.text = item.protocol
        holder.protocol.background = GradientDrawable().apply {
            cornerRadius = 24f
            setColor(protocolColor(item.protocol))
        }
        holder.name.text = item.name
        holder.host.text = item.host
        if (item.latency < 0) {
            holder.latency.text = "--"
            holder.latency.setTextColor(Color.parseColor("#9E9E9E"))
        } else {
            holder.latency.text = "${item.latency} ms"
            holder.latency.setTextColor(
                when {
                    item.latency < 150 -> Color.parseColor("#2E7D32")
                    item.latency < 400 -> Color.parseColor("#EF6C00")
                    else -> Color.parseColor("#C62828")
                }
            )
        }
        if (item.selected) {
            holder.card.setCardBackgroundColor(Color.parseColor("#EDE7F6"))
        } else {
            holder.card.setCardBackgroundColor(Color.WHITE)
        }
        holder.itemView.setOnClickListener { onClick(position) }
    }

    override fun getItemCount() = items.size

    private fun protocolColor(protocol: String): Int {
        return when (protocol.uppercase()) {
            "VLESS" -> Color.parseColor("#7E57C2")
            "VMESS" -> Color.parseColor("#5C6BC0")
            "TROJAN" -> Color.parseColor("#00897B")
            "SS", "SHADOWSOCKS" -> Color.parseColor("#546E7A")
            "HYSTERIA", "HYSTERIA2", "HY2" -> Color.parseColor("#8E24AA")
            else -> Color.parseColor("#7E57C2")
        }
    }
}
