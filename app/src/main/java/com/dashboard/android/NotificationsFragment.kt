package com.dashboard.android

import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dashboard.android.databinding.FragmentNotificationsBinding
import com.dashboard.android.databinding.ItemNotificationCardBinding

class NotificationsFragment : Fragment(), NotificationService.NotificationUpdateListener {

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
            // Open the detail dialog
            NotificationReplyDialog.newInstance(notification)
                .show(childFragmentManager, "reply_dialog")
        }
        
        binding.notificationsList.layoutManager = GridLayoutManager(context, 3)
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
                // Also remove mock if it is one
                NotificationService.instance?.removeMockNotification(notification.key)
                adapter.removeAt(position)
                updateEmptyState()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.notificationsList)
        
        NotificationService.instance?.addListener(this)
        loadNotifications()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        NotificationService.instance?.removeListener(this)
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        loadNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        activity?.runOnUiThread {
            loadNotifications()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        activity?.runOnUiThread {
            loadNotifications()
        }
    }

    private fun loadNotifications() {
        val allNotifications = NotificationService.instance?.getAllNotifications() ?: emptyList()

        val filtered = allNotifications.filter {
            AppConfig.MESSAGING_APP_PACKAGES.contains(it.packageName)
        }

        adapter.updateNotifications(filtered.sortedByDescending { it.postTime })
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

        inner class NotificationViewHolder(val binding: ItemNotificationCardBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
            val binding = ItemNotificationCardBinding.inflate(
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
                // If mocking, we might not have the app installed, so fallback
                val appIcon = context?.packageManager?.getApplicationIcon(sbn.packageName)
                holder.binding.notificationIcon.setImageDrawable(appIcon)
            } catch (e: Exception) {
                holder.binding.notificationIcon.setImageResource(R.drawable.ic_notification)
            }
            
            // Title (Sender)
            val title = extras.getCharSequence("android.title") ?: "Unknown"
            holder.binding.notificationTitle.text = title
            
            // Message Snippet
            val text = extras.getCharSequence("android.text") ?: ""
            holder.binding.notificationText.text = text
            holder.binding.notificationText.visibility = View.VISIBLE
            
            holder.binding.root.setOnClickListener { onClick(sbn) }
        }

        override fun getItemCount() = notifications.size
    }
}
