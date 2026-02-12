package com.dashboard.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    data class Message(
        val text: String,
        val sender: String?,
        val timestamp: Long,
        val isSelf: Boolean
    )

    private val messages = mutableListOf<Message>()

    fun submitList(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isSelf) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_SENT) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val vh = holder as MessageViewHolder

        vh.messageText.text = message.text

        // Show sender name only for received messages
        if (!message.isSelf) {
            vh.senderText?.text = message.sender ?: "Unknown"
            vh.senderText?.visibility = if (message.sender != null) View.VISIBLE else View.GONE
        }
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val senderText: TextView? = itemView.findViewById(R.id.messageSender)
    }

    companion object {
        private const val VIEW_TYPE_RECEIVED = 1
        private const val VIEW_TYPE_SENT = 2
    }
}
