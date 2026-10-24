package com.example.susnet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.susnet.data.HistoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : ListAdapter<HistoryItem, HistoryAdapter.HistoryViewHolder>(DiffCallback()) {

    private var currentPalette: StatusPalette? = null

    fun setPalette(palette: StatusPalette?) {
        currentPalette = palette
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_card, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position), currentPalette)
    }

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView = itemView as? com.google.android.material.card.MaterialCardView
        private val ssidText: TextView? = itemView.findViewById(R.id.textHistorySsid)
        private val timeText: TextView? = itemView.findViewById(R.id.textHistoryTime)
        private val scoreBadge: TextView? = itemView.findViewById(R.id.textHistoryScoreBadge)

        fun bind(item: HistoryItem, palette: StatusPalette?) {
            if (palette != null) {
                cardView?.setCardBackgroundColor(palette.surface)
                cardView?.strokeColor = palette.border
                cardView?.strokeWidth = (1f * itemView.resources.displayMetrics.density).toInt()
            }

            val rawSsid = item.ssid.orEmpty().trim()
            val displaySsid = if (rawSsid.isBlank() || rawSsid.equals("<unknown ssid>", ignoreCase = true)) {
                "Jaringan tidak dikenal"
            } else {
                rawSsid
            }
            ssidText?.text = displaySsid

            val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale("id", "ID"))
            val timeStr = dateFormat.format(Date(item.event.timestamp))
            timeText?.text = timeStr

            val statusLevel = StatusLevel.fromScore(item.event.threatLevel)
            val statusLabel: String
            val statusColorRes: Int

            when (statusLevel) {
                StatusLevel.AMAN -> {
                    statusLabel = "Aman"
                    statusColorRes = R.color.text_success
                }
                StatusLevel.WASPADA -> {
                    statusLabel = "Waspada"
                    statusColorRes = R.color.text_warning
                }
                StatusLevel.BAHAYA -> {
                    statusLabel = "Bahaya"
                    statusColorRes = R.color.text_danger
                }
            }

            val color = ContextCompat.getColor(itemView.context, statusColorRes)
            scoreBadge?.text = "Skor ${item.event.threatLevel} — $statusLabel"
            scoreBadge?.setTextColor(color)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem.event.id == newItem.event.id
        }

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
