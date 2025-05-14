package com.mshomeguardian.logger.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.transcription.ModelInfo
import com.mshomeguardian.logger.transcription.TranscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LanguageModelsFragment : Fragment() {

    private lateinit var transcriptionManager: TranscriptionManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LanguageModelAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_language_models, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        transcriptionManager = TranscriptionManager.getInstance(requireContext())

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Initialize with empty list, will be updated in onResume
        adapter = LanguageModelAdapter(emptyList(), this::onModelAction)
        recyclerView.adapter = adapter

        // Add refresh button
        view.findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            refreshLanguageModels()
        }

        // Initial load
        refreshLanguageModels()
    }

    override fun onResume() {
        super.onResume()
        refreshLanguageModels()
    }

    private fun refreshLanguageModels() {
        lifecycleScope.launch(Dispatchers.IO) {
            val models = transcriptionManager.getAvailableLanguages()

            withContext(Dispatchers.Main) {
                adapter.updateModels(models)
            }
        }
    }

    private fun onModelAction(model: ModelInfo, action: ModelAction) {
        when (action) {
            ModelAction.DOWNLOAD -> downloadModel(model)
            ModelAction.DELETE -> deleteModel(model)
        }
    }

    private fun downloadModel(model: ModelInfo) {
        // Show confirmation dialog for large downloads
        AlertDialog.Builder(requireContext())
            .setTitle("Download Language Model")
            .setMessage("The ${model.displayName} model is approximately 40-50MB in size. Download using Wi-Fi is recommended. Would you like to continue?")
            .setPositiveButton("Download") { _, _ ->
                val progressDialog = AlertDialog.Builder(requireContext())
                    .setTitle("Downloading ${model.displayName}")
                    .setView(R.layout.dialog_download_progress)
                    .setCancelable(false)
                    .create()

                progressDialog.show()

                lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        transcriptionManager.downloadModelSync(model.languageCode)
                    }

                    progressDialog.dismiss()

                    if (success) {
                        Toast.makeText(
                            requireContext(),
                            "${model.displayName} model downloaded successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Failed to download ${model.displayName} model",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    refreshLanguageModels()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteModel(model: ModelInfo) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Language Model")
            .setMessage("Are you sure you want to delete the ${model.displayName} model? You'll need to download it again if you want to transcribe in this language.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        transcriptionManager.deleteModel(model.languageCode)
                    }

                    if (success) {
                        Toast.makeText(
                            requireContext(),
                            "${model.displayName} model deleted",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Failed to delete ${model.displayName} model",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    refreshLanguageModels()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    enum class ModelAction {
        DOWNLOAD, DELETE
    }
}

class LanguageModelAdapter(
    private var models: List<ModelInfo>,
    private val onActionClick: (ModelInfo, LanguageModelsFragment.ModelAction) -> Unit
) : RecyclerView.Adapter<LanguageModelAdapter.ViewHolder>() {

    fun updateModels(newModels: List<ModelInfo>) {
        models = newModels
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_language_model, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(models[position])
    }

    override fun getItemCount(): Int = models.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Declare the views as nullable
        private val tvLanguageName = itemView.findViewById<TextView>(R.id.tvLanguageName)
        private val tvStatus = itemView.findViewById<TextView>(R.id.tvStatus)
        private val btnAction = itemView.findViewById<Button>(R.id.btnAction)

        fun bind(model: ModelInfo) {
            // Null check for safety
            tvLanguageName?.text = model.displayName

            if (model.isDownloaded) {
                tvStatus?.text = "Downloaded"
                btnAction?.text = "Delete"
                btnAction?.setOnClickListener {
                    onActionClick(model, LanguageModelsFragment.ModelAction.DELETE)
                }
            } else {
                tvStatus?.text = "Not Downloaded"
                btnAction?.text = "Download"
                btnAction?.setOnClickListener {
                    onActionClick(model, LanguageModelsFragment.ModelAction.DOWNLOAD)
                }
            }
        }
    }
}