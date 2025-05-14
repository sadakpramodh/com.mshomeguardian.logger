package com.mshomeguardian.logger.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.utils.DeviceIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TranscriptionHistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TranscriptionAdapter
    private lateinit var deviceId: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_transcription_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deviceId = DeviceIdentifier.getPersistentDeviceId(requireContext())

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = TranscriptionAdapter(emptyList())
        recyclerView.adapter = adapter

        loadTranscriptions()
    }

    private fun loadTranscriptions() {
        lifecycleScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val transcriptions = withContext(Dispatchers.IO) {
                    db.collection("devices")
                        .document(deviceId)
                        .collection("transcripts")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(50)
                        .get()
                        .await()
                        .documents
                        .mapNotNull { doc ->
                            val data = doc.data ?: return@mapNotNull null
                            TranscriptionItem(
                                id = doc.id,
                                text = data["transcript"] as? String ?: "",
                                language = data["language"] as? String ?: "",
                                timestamp = (data["timestamp"] as? Long) ?: 0L,
                                audioFileName = data["audioFileName"] as? String ?: ""
                            )
                        }
                }

                withContext(Dispatchers.Main) {
                    adapter.updateTranscriptions(transcriptions)

                    // Show empty view if no transcriptions
                    view?.findViewById<TextView>(R.id.tvEmptyState)?.visibility =
                        if (transcriptions.isEmpty()) View.VISIBLE else View.GONE
                }

            } catch (e: Exception) {
                Log.e("TranscriptionHistory", "Error loading transcriptions", e)
            }
        }
    }
}

data class TranscriptionItem(
    val id: String,
    val text: String,
    val language: String,
    val timestamp: Long,
    val audioFileName: String
)

class TranscriptionAdapter(
    private var transcriptions: List<TranscriptionItem>
) : RecyclerView.Adapter<TranscriptionAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    fun updateTranscriptions(newTranscriptions: List<TranscriptionItem>) {
        transcriptions = newTranscriptions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transcription, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(transcriptions[position])
    }

    override fun getItemCount(): Int = transcriptions.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvLanguage: TextView = itemView.findViewById(R.id.tvLanguage)
        private val tvText: TextView = itemView.findViewById(R.id.tvText)

        fun bind(item: TranscriptionItem) {
            tvTimestamp.text = dateFormat.format(Date(item.timestamp))

            // Format language code to readable name
            val languageName = when (item.language) {
                "en" -> "English"
                "hi" -> "Hindi"
                "te" -> "Telugu"
                "fr" -> "French"
                "es" -> "Spanish"
                "de" -> "German"
                "ru" -> "Russian"
                "it" -> "Italian"
                "ja" -> "Japanese"
                "ko" -> "Korean"
                "zh" -> "Chinese"
                else -> item.language
            }
            tvLanguage.text = languageName

            tvText.text = item.text
        }
    }
}