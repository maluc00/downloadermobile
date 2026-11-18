package com.klipr.mobile.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.klipr.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val apkUrl: String,
    val apkName: String,
    val releaseNotes: String,
    val sizeBytes: Long
)

object AppUpdater {

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/maluc00/downloadermobile/releases/latest"

    suspend fun checkForUpdate(): Result<AppReleaseInfo?> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Klipr-Pocket-Android")
            }

            val code = connection.responseCode
            if (code == 404) {
                return@withContext Result.success(null)
            }
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("HTTP $code ao verificar atualizações."))
            }

            val bodyText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(bodyText)
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isBlank()) return@withContext Result.success(null)

            if (!isRemoteNewer(remoteTag = tagName, localVersion = BuildConfig.VERSION_NAME)) {
                return@withContext Result.success(null)
            }

            val assets = json.optJSONArray("assets") ?: JSONArray()
            val selectedAsset = selectBestApkAsset(assets)
                ?: return@withContext Result.success(null)

            Result.success(
                AppReleaseInfo(
                    tagName = tagName,
                    apkUrl = selectedAsset.optString("browser_download_url", ""),
                    apkName = selectedAsset.optString("name", "klipr-update.apk"),
                    releaseNotes = json.optString("body", "").take(220),
                    sizeBytes = selectedAsset.optLong("size", 0L)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun selectBestApkAsset(assets: JSONArray): JSONObject? {
        val apkAssets = mutableListOf<JSONObject>()
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkAssets.add(asset)
            }
        }
        if (apkAssets.isEmpty()) return null

        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull()?.lowercase().orEmpty()
        if (primaryAbi.isNotBlank()) {
            apkAssets.firstOrNull {
                it.optString("name", "").lowercase().contains(primaryAbi)
            }?.let { return it }
        }

        apkAssets.firstOrNull {
            it.optString("name", "").lowercase().contains("universal")
        }?.let { return it }

        return apkAssets.first()
    }

    fun isRemoteNewer(remoteTag: String, localVersion: String): Boolean {
        val remoteParts = remoteTag.removePrefix("v").removePrefix("V")
            .split(".", "-", "_")
            .mapNotNull { it.toIntOrNull() }
        val localParts = localVersion.removePrefix("v").removePrefix("V")
            .split(".", "-", "_")
            .mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        release: AppReleaseInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val cacheBase = context.externalCacheDir ?: context.cacheDir
            val safeTag = release.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val outFile = File(cacheBase, "klipr-update-$safeTag.apk")

            connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Klipr-Pocket-Android")
            }

            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException("Falha HTTP ${connection.responseCode} ao baixar APK.")
                )
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: release.sizeBytes
            connection.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var lastPct = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0L) {
                            val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                launchApkInstaller(context, outFile)
            }

            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }
}
