package com.klipr.mobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DownloadHistoryItem(
    val title: String,
    val filePath: String,
    val formatLabel: String,
    val timestamp: Long,
    val isAudio: Boolean
)

object KliprPreferences {

    private const val PREFS_NAME = "klipr_prefs"
    private const val KEY_HISTORY = "recent_downloads_json"
    private const val KEY_CUSTOM_FOLDERS = "custom_subfolders_json"
    private const val MAX_HISTORY_ITEMS = 15
    private const val MAX_CUSTOM_FOLDERS = 3

    val DEFAULT_FOLDERS = listOf("Klipr", "Músicas", "Cortes", "Status")

    fun loadHistory(context: Context): List<DownloadHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val items = mutableListOf<DownloadHistoryItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val path = obj.optString("filePath", "")
                if (path.isNotBlank() && File(path).exists()) {
                    items.add(
                        DownloadHistoryItem(
                            title = obj.optString("title", "Mídia Klipr"),
                            filePath = path,
                            formatLabel = obj.optString("formatLabel", "MP4"),
                            timestamp = obj.optLong("timestamp", 0L),
                            isAudio = obj.optBoolean("isAudio", false)
                        )
                    )
                }
            }
            items
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addHistoryItem(context: Context, item: DownloadHistoryItem) {
        val current = loadHistory(context).toMutableList()
        current.removeAll { it.filePath == item.filePath }
        current.add(0, item)
        val trimmed = current.take(MAX_HISTORY_ITEMS)
        saveHistoryList(context, trimmed)
    }

    fun removeHistoryItem(context: Context, filePath: String) {
        val updated = loadHistory(context).filterNot { it.filePath == filePath }
        saveHistoryList(context, updated)
    }

    private fun saveHistoryList(context: Context, list: List<DownloadHistoryItem>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("title", item.title)
                put("filePath", item.filePath)
                put("formatLabel", item.formatLabel)
                put("timestamp", item.timestamp)
                put("isAudio", item.isAudio)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }

    fun loadSubfolders(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CUSTOM_FOLDERS, null)
        val custom = if (!raw.isNullOrBlank()) {
            try {
                val array = JSONArray(raw)
                (0 until array.length())
                    .mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        return (DEFAULT_FOLDERS + custom).distinct()
    }

    fun saveCustomSubfolder(context: Context, folderName: String) {
        val clean = folderName.trim().trim('/')
        if (clean.isBlank() || DEFAULT_FOLDERS.any { it.equals(clean, ignoreCase = true) }) {
            return
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = try {
            val raw = prefs.getString(KEY_CUSTOM_FOLDERS, null)
            if (raw.isNullOrBlank()) mutableListOf()
            else {
                val arr = JSONArray(raw)
                MutableList(arr.length()) { idx -> arr.optString(idx) }
                    .filter { it.isNotBlank() }
                    .toMutableList()
            }
        } catch (e: Exception) {
            mutableListOf()
        }
        existing.removeAll { it.equals(clean, ignoreCase = true) }
        existing.add(0, clean)
        val trimmed = existing.take(MAX_CUSTOM_FOLDERS)
        val array = JSONArray().apply { trimmed.forEach { put(it) } }
        prefs.edit().putString(KEY_CUSTOM_FOLDERS, array.toString()).apply()
    }
}
