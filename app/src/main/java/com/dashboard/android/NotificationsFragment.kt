package com.dashboard.android

import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dashboard.android.databinding.FragmentNotificationsBinding
import com.dashboard.android.databinding.ItemNotificationBinding

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        adapter = NotificationAdapter { notification ->
            // Open the source app
            notification.notification.contentIntent?.send()
        }
        
        binding.notificationsList.layoutManager = LinearLayoutManager(context)
        binding.notificationsList.adapter = adapter
        
        // Swipe to dismiss
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val notification = adapter.notifications[position]
                NotificationService.instance?.cancelNotification(notification.key)
                adapter.removeAt(position)
                updateEmptyState()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.notificationsList)
        
        loadNotifications()
    }

    override fun onResume() {
        super.onResume()
        loadNotifications()
    }

    private fun loadNotifications() {
        val notifications = NotificationService.instance?.getActiveNotifications()?.toList() ?: emptyList()
        adapter.updateNotifications(notifications.sortedByDescending { it.postTime })
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (adapter.itemCount == 0) {
            binding.emptyText.visibility = View.VISIBLE
            binding.notificationsList.visibility = View.GONE
        } else {
            binding.emptyText.visibility = View.GONE
            binding.notificationsList.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // RecyclerView Adapter
    private inner class NotificationAdapter(
        private val onClick: (StatusBarNotification) -> Unit
    ) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

        val notifications = mutableListOf<StatusBarNotification>()

        fun updateNotifications(newList: List<StatusBarNotification>) {
            notifications.clear()
            notifications.addAll(newList)
            notifyDataSetChanged()
        }

        fun removeAt(position: Int) {
            if (position in notifications.indices) {
                notifications.removeAt(position)
                notifyItemRemoved(position)
            }
        }

        inner class NotificationViewHolder(val binding: ItemNotificationBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
            val binding = ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return NotificationViewHolder(binding)
        }

        override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
            val sbn = notifications[position]
            val notification = sbn.notification
            val extras = notification.extras
            
            // App icon
            try {
                val appIcon = context?.packageManager?.getApplicationIcon(sbn.packageName)
                holder.binding.appIcon.setImageDrawable(appIcon)
            } catch (e: Exception) {
                holder.binding.appIcon.setImageResource(R.drawable.ic_notification)
            }
            
            // Title and text
            holder.binding.notificationTitle.text = extras.getCharSequence("android.title") ?: ""
            holder.binding.notificationText.text = extras.getCharSequence("android.text") ?: ""
            
            // Timestamp
            val ago = getTimeAgo(sbn.postTime)
            holder.binding.notificationTime.text = ago
            
            holder.binding.root.setOnClickListener { onClick(sbn) }
        }

        override fun getItemCount() = notifications.size

        private fun getTimeAgo(time: Long): String {
            val diff = System.currentTimeMillis() - time
            return when {
                diff < 60000 -> "now"
                diff < 3600000 -> "${diff / 60000}m"
                diff < 86400000 -> "${diff / 3600000}h"
                else -> "${diff / 86400000}d"
            }
        }
    }
}
