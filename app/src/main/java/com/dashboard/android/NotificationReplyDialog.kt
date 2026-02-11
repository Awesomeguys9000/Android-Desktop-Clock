package com.dashboard.android

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.dashboard.android.databinding.DialogNotificationReplyBinding

class NotificationReplyDialog : DialogFragment(), NotificationService.NotificationUpdateListener {

    private var _binding: DialogNotificationReplyBinding? = null
    private val binding get() = _binding!!
    private var notificationKey: String? = null
    private var currentSbn: StatusBarNotification? = null
    private lateinit var messageAdapter: MessageAdapter

    // Cache the icon to prevent flickering/loss
    private var cachedIcon: Drawable? = null

    companion object {
        private const val ARG_KEY = "notification_key"

        fun newInstance(sbn: StatusBarNotification): NotificationReplyDialog {
            val fragment = NotificationReplyDialog()
            val args = Bundle()
            args.putString(ARG_KEY, sbn.key)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogNotificationReplyBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        notificationKey = arguments?.getString(ARG_KEY)

        setupRecyclerView()
        loadNotification()

        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnSend.setOnClickListener { sendReply() }

        NotificationService.instance?.addListener(this)
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter()
        binding.messagesList.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true // Start from bottom
        }
        binding.messagesList.adapter = messageAdapter
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun loadNotification() {
        val notifications = NotificationService.instance?.getAllNotifications() ?: emptyList()
        currentSbn = notifications.find { it.key == notificationKey }

        if (currentSbn == null) {
            dismiss()
            return
        }

        updateUI(currentSbn!!)
    }

    private fun updateUI(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Unknown"

        binding.dialogTitle.text = title

        // Icon handling
        if (cachedIcon == null) {
            try {
                cachedIcon = context?.packageManager?.getApplicationIcon(sbn.packageName)
                binding.dialogIcon.setImageDrawable(cachedIcon)
            } catch (e: Exception) {
                binding.dialogIcon.setImageResource(R.drawable.ic_notification)
            }
        } else {
             binding.dialogIcon.setImageDrawable(cachedIcon)
        }

        // Extract messages
        val messages = extractMessages(sbn.notification)
        messageAdapter.submitList(messages)

        if (messages.isNotEmpty()) {
            binding.messagesList.scrollToPosition(messages.size - 1)
        }
    }

    private fun extractMessages(notification: Notification): List<MessageAdapter.Message> {
        val messagesList = mutableListOf<MessageAdapter.Message>()
        val extras = notification.extras

        // Try MessagingStyle first
        // Fix: Use full class path or check imports. Notification.MessagingStyle should work for API 24+
        // If compilation fails on extractMessagingStyleFromNotification, it might be available only on builder or compat.
        // Actually, Notification.MessagingStyle.extractMessagingStyleFromNotification was added in API 28?
        // Let's check compat.

        // Strategy: Use reflection or Compat if available, or just try-catch for API versions.
        // However, standard Android Notification.MessagingStyle should work if compileSdk is 34.
        // The error said "Unresolved reference". It might be static method on Notification, not the inner class?
        // No, it is on Notification.MessagingStyle.

        // Retrying with androidx.core.app.NotificationCompat which is safer.
        val messagingStyle = try {
            androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        } catch (e: Exception) {
            null
        }

        if (messagingStyle != null) {
            messagingStyle.messages.forEach { msg ->
                val text = msg.text?.toString() ?: ""
                val sender = msg.person?.name?.toString() ?: "Unknown"

                messagesList.add(
                    MessageAdapter.Message(
                        text = text,
                        sender = sender,
                        timestamp = msg.timestamp,
                        isSelf = false
                    )
                )
            }
        } else {
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

            if (!text.isNullOrEmpty()) {
                messagesList.add(
                    MessageAdapter.Message(
                        text = bigText ?: text,
                        sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                        timestamp = currentSbn?.postTime ?: System.currentTimeMillis(),
                        isSelf = false
                    )
                )
            }
        }

        return messagesList
    }

    private fun sendReply() {
        val text = binding.inputReply.text.toString()
        if (text.isBlank()) return

        val sbn = currentSbn ?: return
        val action = findReplyAction(sbn.notification)

        if (action != null) {
            try {
                val remoteInput = action.remoteInputs!!.first()
                val intent = Intent()
                val bundle = Bundle()
                bundle.putCharSequence(remoteInput.resultKey, text)
                RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)

                action.actionIntent.send(context, 0, intent)

                // Add to UI immediately
                val newMessage = MessageAdapter.Message(
                    text = text,
                    sender = "Me",
                    timestamp = System.currentTimeMillis(),
                    isSelf = true
                )
                messageAdapter.addMessage(newMessage)
                binding.messagesList.smoothScrollToPosition(messageAdapter.itemCount - 1)

                binding.inputReply.text.clear()

            } catch (e: Exception) {
                Toast.makeText(context, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
             Toast.makeText(context, "Reply not supported", Toast.LENGTH_SHORT).show()
        }
    }

    private fun findReplyAction(notification: Notification): Notification.Action? {
        notification.actions?.forEach { action ->
            if (action.remoteInputs != null && action.remoteInputs!!.isNotEmpty()) {
                return action
            }
        }
        return null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.key == notificationKey) {
            activity?.runOnUiThread {
                currentSbn = sbn
                updateUI(sbn)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == notificationKey) {
             activity?.runOnUiThread {
                 Toast.makeText(context, "Notification removed", Toast.LENGTH_SHORT).show()
             }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        NotificationService.instance?.removeListener(this)
        _binding = null
    }
}
