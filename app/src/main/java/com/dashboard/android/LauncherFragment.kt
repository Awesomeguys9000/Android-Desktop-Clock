package com.dashboard.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dashboard.android.databinding.FragmentLauncherBinding
import com.dashboard.android.databinding.ItemAppCardBinding

class LauncherFragment : Fragment() {

    private var _binding: FragmentLauncherBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLauncherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.appsGrid.layoutManager = GridLayoutManager(context, 3)
        // Pass both click (open) and long click (restart) listeners
        binding.appsGrid.adapter = AppAdapter(
            AppConfig.defaultApps,
            onAppClick = { appConfig ->
                (activity as? MainActivity)?.showWebApp(appConfig)
            },
            onAppLongClick = { appConfig ->
                // Long press to restart
                (activity as? MainActivity)?.restartWebApp(appConfig)
                true
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // RecyclerView Adapter
    private inner class AppAdapter(
        private val apps: List<AppConfig>,
        private val onAppClick: (AppConfig) -> Unit,
        private val onAppLongClick: (AppConfig) -> Boolean
    ) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

        inner class AppViewHolder(val binding: ItemAppCardBinding) : 
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val binding = ItemAppCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return AppViewHolder(binding)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val app = apps[position]
            holder.binding.appIcon.setImageResource(app.iconResId)
            holder.binding.appName.text = app.name
            holder.binding.root.setOnClickListener { onAppClick(app) }
            holder.binding.root.setOnLongClickListener { onAppLongClick(app) }
        }

        override fun getItemCount() = apps.size
    }
}
