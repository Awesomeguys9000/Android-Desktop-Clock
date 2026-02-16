package com.dashboard.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.dashboard.android.databinding.FragmentOtpBinding
import com.dashboard.android.databinding.ItemOtpBinding
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class OtpFragment : Fragment() {

    private var _binding: FragmentOtpBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: OtpRepository
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var adapter: OtpAdapter

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processImage(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = OtpRepository(requireContext())
        setupRecyclerView()
        setupWebView()
        setupListeners()
        startTimer()
    }

    private fun setupRecyclerView() {
        adapter = OtpAdapter(repository.getEntries(), { id ->
            repository.deleteEntry(id)
            adapter.updateData(repository.getEntries())
        })
        binding.recyclerOtp.layoutManager = LinearLayoutManager(context)
        binding.recyclerOtp.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            (activity as? MainActivity)?.returnToClock()
        }

        binding.btnAdd.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun setupWebView() {
        val webView = binding.hiddenWebView
        webView.settings.javaScriptEnabled = true
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(requireContext()))
            .build()

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: android.webkit.WebResourceRequest
            ): android.webkit.WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView,
                "android",
                setOf("*"),
                object : androidx.webkit.WebViewCompat.WebMessageListener {
                    override fun onPostMessage(
                        view: WebView,
                        message: androidx.webkit.WebMessageCompat,
                        sourceOrigin: Uri,
                        isMainFrame: Boolean,
                        replyProxy: androidx.webkit.JavaScriptReplyProxy
                    ) {
                        message.data?.let { handleJsMessage(it) }
                    }
                }
            )
        }

        // Load the worker HTML from local assets using the AssetLoader domain
        webView.loadUrl("https://appassets.androidplatform.net/assets/qr_worker.html")
    }

    private fun processImage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                val outputStream = ByteArrayOutputStream()
                val scaledBitmap = if (bitmap.width > 800 || bitmap.height > 800) {
                    val ratio = Math.min(800.0 / bitmap.width, 800.0 / bitmap.height)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
                } else {
                    bitmap
                }

                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                val script = "decodeQr('data:image/jpeg;base64,$base64')"
                binding.hiddenWebView.evaluateJavascript(script, null)

                Toast.makeText(context, "Scanning QR...", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleJsMessage(message: String) {
        try {
            val json = JSONObject(message)
            val status = json.optString("status")
            if (status == "success") {
                val data = json.optString("data")
                val entries = GoogleAuthParser.parseUri(data)
                if (entries.isNotEmpty()) {
                    requireActivity().runOnUiThread {
                        repository.addEntries(entries)
                        adapter.updateData(repository.getEntries())
                        Toast.makeText(context, "Imported ${entries.size} codes", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    requireActivity().runOnUiThread {
                        Toast.makeText(context, "No valid Google Auth data found", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "QR Scan failed: ${json.optString("message")}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startTimer() {
        val runnable = object : Runnable {
            override fun run() {
                if (_binding != null) {
                    updateUI()
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(runnable)
    }

    private fun updateUI() {
        binding.progressBar.progress = TotpUtil.getProgress()
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }

    class OtpAdapter(
        private var entries: List<OtpRepository.OtpEntry>,
        private val onDelete: (String) -> Unit
    ) : RecyclerView.Adapter<OtpAdapter.OtpViewHolder>() {

        private val visibleCodes = mutableSetOf<String>()

        fun updateData(newEntries: List<OtpRepository.OtpEntry>) {
            entries = newEntries
            notifyDataSetChanged()
        }

        inner class OtpViewHolder(val binding: ItemOtpBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OtpViewHolder {
            val binding = ItemOtpBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return OtpViewHolder(binding)
        }

        override fun onBindViewHolder(holder: OtpViewHolder, position: Int) {
            val entry = entries[position]
            holder.binding.textIssuer.text = entry.issuer
            holder.binding.textName.text = entry.name

            val isVisible = visibleCodes.contains(entry.id)
            val code = TotpUtil.generateTotp(entry.secret)
            // Format 123456 -> 123 456
            if (code.length == 6) {
                holder.binding.textCode.text = "${code.substring(0, 3)} ${code.substring(3, 6)}"
            } else {
                holder.binding.textCode.text = code
            }

            holder.binding.textCode.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
            holder.binding.imageHidden.visibility = if (isVisible) View.INVISIBLE else View.VISIBLE

            val remaining = TotpUtil.getRemainingSeconds()
            val color = if (remaining <= 10) 0xFFFF5555.toInt() else 0xFFFFFFFF.toInt()
            holder.binding.textCode.setTextColor(color)

            holder.binding.codeContainer.setOnClickListener {
                if (isVisible) {
                    visibleCodes.remove(entry.id)
                } else {
                    visibleCodes.add(entry.id)
                }
                notifyItemChanged(position)
            }

            holder.binding.btnDelete.setOnClickListener {
                onDelete(entry.id)
            }
        }

        override fun getItemCount() = entries.size
    }
}
