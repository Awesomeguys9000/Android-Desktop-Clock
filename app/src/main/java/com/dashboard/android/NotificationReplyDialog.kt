package com.dashboard.android

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.dashboard.android.databinding.DialogNotificationReplyBinding

class NotificationReplyDialog : DialogFragment(), NotificationService.NotificationUpdateListener {

    private var _binding: DialogNotificationReplyBinding? = null
    private val binding get() = _binding!!
    private var notificationKey: String? = null
    private var currentSbn: StatusBarNotification? = null

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
        loadNotification()

        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnSend.setOnClickListener { sendReply() }

        NotificationService.instance?.addListener(this)
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
        val title = extras.getCharSequence(Notification.EXTRA_TITLE) ?: "Unknown"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)

        binding.dialogTitle.text = title
        binding.dialogMessage.text = bigText ?: text

        try {
            val appIcon = context?.packageManager?.getApplicationIcon(sbn.packageName)
            binding.dialogIcon.setImageDrawable(appIcon)
        } catch (e: Exception) {
            binding.dialogIcon.setImageResource(R.drawable.ic_notification)
        }
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

                binding.inputReply.text.clear()
                Toast.makeText(context, "Sent", Toast.LENGTH_SHORT).show()

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
                 Toast.makeText(context, "Conversation closed", Toast.LENGTH_SHORT).show()
                 dismiss()
             }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        NotificationService.instance?.removeListener(this)
        _binding = null
    }
}
