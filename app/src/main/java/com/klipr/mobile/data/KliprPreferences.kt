package com.klipr.mobile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_USE_ARIA2C = "use_aria2c"
    private const val KEY_USE_NIGHTLY_YTDLP = "use_nightly_ytdlp"
    private const val KEY_LAST_YTDLP_UPDATE_MS = "last_ytdlp_update_ms"
    private const val KEY_SPONSOR_BLOCK = "sponsor_block_enabled"

    private const val MAX_HISTORY_ITEMS = 15
    private const val MAX_CUSTOM_FOLDERS = 3
    private const val YTDLP_AUTO_UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L

    val DEFAULT_FOLDERS = listOf("Klipr", "Músicas", "Cortes", "Status")

    private val _themeModeFlow = MutableStateFlow("aurora")
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    fun syncFromStorage(context: Context) {
        _themeModeFlow.value = getThemeMode(context)
    }

    fun getThemeMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME_MODE, "aurora") ?: "aurora"
    }

    fun setThemeMode(context: Context, modeId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, modeId)
            .apply()
        _themeModeFlow.value = modeId
    }

    fun isAria2cEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_ARIA2C, true)

    fun setAria2cEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USE_ARIA2C, enabled)
            .apply()
    }

    fun isNightlyYtDlpEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_NIGHTLY_YTDLP, true)

    fun setNightlyYtDlpEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USE_NIGHTLY_YTDLP, enabled)
            .apply()
    }

    fun isSponsorBlockEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SPONSOR_BLOCK, false)

    fun setSponsorBlockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SPONSOR_BLOCK, enabled)
            .apply()
    }

    fun shouldAutoUpdateYtDlp(context: Context): Boolean {
        val lastMs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_YTDLP_UPDATE_MS, 0L)
        return (System.currentTimeMillis() - lastMs) >= YTDLP_AUTO_UPDATE_INTERVAL_MS
    }

    fun markYtDlpUpdated(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_YTDLP_UPDATE_MS, System.currentTimeMillis())
            .apply()
    }

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
