package com.klipr.mobile.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.LruCache
import com.klipr.mobile.KliprApp
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

data class VideoChapter(
    val title: String,
    val startTime: String,
    val endTime: String,
    val startSeconds: Long,
    val endSeconds: Long
)

data class VideoInfo(
    val title: String,
    val uploader: String,
    val durationText: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val url: String,
    val availableHeights: List<Int> = listOf(1080, 720, 480, 360),
    val formatSizesMb: Map<Int, Double> = emptyMap(),
    val audioSizeMb: Double? = null,
    val chapters: List<VideoChapter> = emptyList()
)

data class SelectedFormat(
    val isAudio: Boolean = false,
    val height: Int = 1080,
    val ext: String = "mp4",
    val label: String = "1080p Full HD"
)

data class TimeTrim(
    val startTime: String,
    val endTime: String,
    val accurateCut: Boolean = false
)

object DownloaderEngine {

    const val DEFAULT_PROCESS_ID = "klipr_download"

    private val activeTasks = AtomicInteger(0)
    private val infoCache = LruCache<String, VideoInfo>(15)

    fun isBusy(): Boolean = activeTasks.get() > 0

    fun cancelDownload(processId: String = DEFAULT_PROCESS_ID): Boolean {
        return try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (e: Exception) {
            false
        }
    }

    fun sanitizeUrl(rawText: String): String {
        val urlRegex = "(https?://[\\w-]+(\\.[\\w-]+)+(/[^\\s]*)?)".toRegex()
        val extracted = urlRegex.find(rawText)
            ?.value
            ?.trim()
            ?.trimEnd('.', ',', ';', ')', ']')
            ?: rawText.trim()

        if (extracted.isBlank()) return ""

        return try {
            val uri = Uri.parse(extracted)
            val host = uri.host?.lowercase().orEmpty()
            val isYouTube = host.contains("youtube.com") || host.contains("youtu.be")
            val trackingKeys = setOf(
                "si", "igsh", "igshid", "utm_source", "utm_medium", "utm_campaign",
                "utm_term", "utm_content", "feature", "s", "fbclid", "gclid", "pp",
                "ref", "ref_src", "ref_url"
            )
            val paramNames = uri.queryParameterNames
            if (paramNames.isEmpty()) {
                extracted
            } else {
                val builder = uri.buildUpon().clearQuery()
                for (name in paramNames) {
                    val lower = name.lowercase()
                    val dropParam = lower in trackingKeys || (lower == "t" && !isYouTube)
                    if (!dropParam) {
                        uri.getQueryParameters(name).forEach { value ->
                            builder.appendQueryParameter(name, value)
                        }
                    }
                }
                builder.build().toString()
            }
        } catch (e: Exception) {
            extracted
        }
    }

    fun formatDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val h = safe / 3600
        val m = (safe % 3600) / 60
        val s = safe % 60
        return if (h > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    fun parseTimeToSeconds(timeStr: String): Long? {
        val clean = timeStr.trim()
        if (clean.isEmpty()) return null
        val parts = clean.split(":")
        return when (parts.size) {
            2 -> {
                val m = parts[0].toLongOrNull() ?: return null
                val s = parts[1].toLongOrNull() ?: return null
                if (m < 0 || s !in 0..59) return null
                m * 60 + s
            }
            3 -> {
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val s = parts[2].toLongOrNull() ?: return null
                if (h < 0 || m !in 0..59 || s !in 0..59) return null
                h * 3600 + m * 60 + s
            }
            1 -> clean.toLongOrNull()?.takeIf { it >= 0 }
            else -> null
        }
    }

    fun isValidTrimRange(startStr: String, endStr: String, maxDurationSecs: Long): Boolean {
        val start = parseTimeToSeconds(startStr) ?: return false
        val end = parseTimeToSeconds(endStr) ?: return false
        if (start >= end) return false
        if (maxDurationSecs > 0 && end > maxDurationSecs) return false
        return true
    }

    fun formatSizeMb(mb: Double?): String {
        if (mb == null || mb <= 0.0) return ""
        return if (mb >= 1024.0) {
            String.format(Locale.US, "~%.1f GB", mb / 1024.0)
        } else if (mb >= 10.0) {
            String.format(Locale.US, "~%.0f MB", mb)
        } else {
            String.format(Locale.US, "~%.1f MB", mb)
        }
    }

    suspend fun fetchInfo(rawUrl: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(rawUrl)
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Link inválido ou vazio."))
        }

        infoCache.get(cleanUrl)?.let { cached ->
            return@withContext Result.success(cached)
        }

        activeTasks.incrementAndGet()
        try {
            val ready = KliprApp.awaitEngineReady()
            if (!ready) {
                return@withContext Result.failure(
                    IllegalStateException("Motores nativos ainda não inicializados. Tente novamente.")
                )
            }

            val request = YoutubeDLRequest(cleanUrl).apply {
                addOption("--no-playlist")
                addOption("--socket-timeout", "15")
            }

            val ytInfo = YoutubeDL.getInstance().getInfo(request)
            val durationSecs = ytInfo.duration.toLong().coerceAtLeast(0L)
            val durationFormatted = formatDuration(durationSecs)

            val formats = ytInfo.formats.orEmpty()
            val heights = mutableSetOf<Int>()
            formats.forEach { fmt ->
                if (fmt.height > 0) heights.add(fmt.height)
            }
            val sortedHeights = if (heights.isNotEmpty()) {
                heights.sortedDescending()
            } else {
                listOf(1080, 720, 480, 360)
            }

            val (formatSizesMb, audioSizeMb) = computeEstimatedSizes(formats, durationSecs)
            val chapters = extractChapters(ytInfo.description, durationSecs)

            val info = VideoInfo(
                title = ytInfo.title ?: "Sem título",
                uploader = ytInfo.uploader ?: "Autor desconhecido",
                durationText = durationFormatted,
                durationSeconds = durationSecs,
                thumbnailUrl = ytInfo.thumbnail ?: "",
                url = cleanUrl,
                availableHeights = sortedHeights,
                formatSizesMb = formatSizesMb,
                audioSizeMb = audioSizeMb,
                chapters = chapters
            )
            infoCache.put(cleanUrl, info)
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            activeTasks.decrementAndGet()
        }
    }

    private fun estimateFormatBytes(fmt: VideoFormat, durationSecs: Long): Long {
        if (fmt.fileSize > 0L) return fmt.fileSize
        if (fmt.fileSizeApproximate > 0L) return fmt.fileSizeApproximate
        val bitrateKbps = when {
            fmt.tbr > 0 -> fmt.tbr.toLong()
            fmt.abr > 0 -> fmt.abr.toLong()
            else -> 0L
        }
        if (bitrateKbps > 0L && durationSecs > 0L) {
            return (bitrateKbps * 1000L / 8L) * durationSecs
        }
        return 0L
    }

    private fun computeEstimatedSizes(
        formats: List<VideoFormat>,
        durationSecs: Long
    ): Pair<Map<Int, Double>, Double?> {
        if (formats.isEmpty()) return emptyMap<Int, Double>() to null

        val bestAudioBytes = formats
            .filter {
                (it.vcodec.equals("none", ignoreCase = true) || it.height == 0) &&
                    !it.acodec.equals("none", ignoreCase = true)
            }
            .mapNotNull { fmt ->
                estimateFormatBytes(fmt, durationSecs).takeIf { it > 0L }
            }
            .maxOrNull() ?: 0L

        val sizesByHeight = mutableMapOf<Int, Double>()
        val groupedByHeight = formats.filter { it.height > 0 }.groupBy { it.height }

        for ((height, candidates) in groupedByHeight) {
            val preferred = if (height <= 1080) {
                candidates.filter {
                    val vc = it.vcodec.orEmpty().lowercase()
                    vc.contains("avc1") || vc.contains("h264")
                }.ifEmpty { candidates }
            } else {
                candidates
            }

            val chosen = preferred.maxByOrNull { estimateFormatBytes(it, durationSecs) } ?: continue
            val videoBytes = estimateFormatBytes(chosen, durationSecs)
            if (videoBytes > 0L) {
                val needsAudioMux = chosen.acodec.isNullOrBlank() ||
                    chosen.acodec.equals("none", ignoreCase = true)
                val totalBytes = videoBytes + if (needsAudioMux) bestAudioBytes else 0L
                sizesByHeight[height] = totalBytes / (1024.0 * 1024.0)
            }
        }

        val audioMb = if (bestAudioBytes > 0L) bestAudioBytes / (1024.0 * 1024.0) else null
        return sizesByHeight to audioMb
    }

    private fun extractChapters(description: String?, durationSecs: Long): List<VideoChapter> {
        if (description.isNullOrBlank()) return emptyList()

        val lineRegex = "^\\s*[\\[\\(]?(\\d{1,2}:\\d{2}(?::\\d{2})?)[\\]\\)]?\\s*[-–—|:]?\\s*(.+?)\\s*$".toRegex()
        val parsed = mutableListOf<Pair<Long, String>>()

        description.lineSequence().forEach { line ->
            val match = lineRegex.find(line)
            if (match != null) {
                val timeStr = match.groupValues[1]
                val rawTitle = match.groupValues[2].trim()
                val secs = parseTimeToSeconds(timeStr)
                if (secs != null && rawTitle.isNotBlank()) {
                    if (parsed.isEmpty() || secs > parsed.last().first) {
                        parsed.add(secs to rawTitle.take(40))
                    }
                }
            }
        }

        if (parsed.size < 2) return emptyList()

        return parsed.mapIndexedNotNull { index, (startSec, title) ->
            val endSec = if (index + 1 < parsed.size) {
                parsed[index + 1].first
            } else {
                durationSecs
            }
            if (endSec > startSec) {
                VideoChapter(
                    title = title,
                    startTime = formatDuration(startSec),
                    endTime = formatDuration(endSec),
                    startSeconds = startSec,
                    endSeconds = endSec
                )
            } else {
                null
            }
        }
    }

    suspend fun startDownload(
        context: Context,
        url: String,
        format: SelectedFormat,
        trim: TimeTrim? = null,
        customFileName: String? = null,
        targetSubfolder: String = "Klipr",
        processId: String = DEFAULT_PROCESS_ID,
        onProgress: (progress: Float, line: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        activeTasks.incrementAndGet()
        val cleanUrl = sanitizeUrl(url)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val cleanSubfolder = targetSubfolder.trim().trim('/').ifBlank { "Klipr" }
        val targetDir = File(downloadsDir, cleanSubfolder).apply {
            if (!exists()) mkdirs()
        }
        val startTimeMs = System.currentTimeMillis() - 1500L

        try {
            val ready = KliprApp.awaitEngineReady()
            if (!ready) {
                return@withContext Result.failure(
                    IllegalStateException("Motores nativos ainda não inicializados.")
                )
            }

            val request = YoutubeDLRequest(cleanUrl)

            // Tratamento do nome do arquivo com indicação de corte se ativo
            var safeBaseName = if (!customFileName.isNullOrBlank()) {
                customFileName.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().take(70)
            } else {
                "%(title).70s"
            }

            if (trim != null && trim.startTime.isNotBlank() && trim.endTime.isNotBlank()) {
                val cleanStart = trim.startTime.replace(":", "_")
                val cleanEnd = trim.endTime.replace(":", "_")
                safeBaseName += " [$cleanStart-$cleanEnd]"
            }

            val outputTemplate = "${targetDir.absolutePath}/$safeBaseName.%(ext)s"
            request.addOption("-o", outputTemplate)
            request.addOption("--no-mtime")
            request.addOption("--no-playlist")
            request.addOption("--concurrent-fragments", "4")
            request.addOption("--buffer-size", "16K")

            // Motor C++ Aria2c (16 conexões simultâneas) quando não há recorte por seção
            if (trim == null && KliprPreferences.isAria2cEnabled(context)) {
                request.addOption("--downloader", "libaria2c.so")
                request.addOption("--external-downloader-args", "aria2c:-x 16 -s 16 -k 1M")
            }

            // SponsorBlock opcional (pula patrocínios e autopromoção)
            if (KliprPreferences.isSponsorBlockEnabled(context)) {
                request.addOption("--sponsorblock-remove", "sponsor,selfpromo")
            }

            // INJEÇÃO DO CORTE DE TRECHO (Download Sections)
            if (trim != null && trim.startTime.isNotBlank() && trim.endTime.isNotBlank()) {
                request.addOption("--download-sections", "*${trim.startTime.trim()}-${trim.endTime.trim()}")
                if (trim.accurateCut && !format.isAudio) {
                    request.addOption("--force-keyframes-at-cuts")
                }
            }

            if (format.isAudio) {
                request.addOption("-x")
                request.addOption("--audio-format", format.ext)
                request.addOption("--audio-quality", "0")
                request.addOption("--embed-metadata")
                if (!format.ext.equals("wav", ignoreCase = true)) {
                    request.addOption("--embed-thumbnail")
                    request.addOption("--convert-thumbnails", "jpg")
                }
            } else {
                val h = format.height
                request.addOption("-f", "bv*[height<=$h]+ba/b[height<=$h]/best")
                if (h <= 1080) {
                    request.addOption("-S", "vcodec:h264,res,acodec:m4a")
                }
                request.addOption("--merge-output-format", format.ext)
            }

            var lastUpdateMs = 0L
            var lastReportedProgress = -1f

            YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
                val progressFloat = progress.toFloat().coerceIn(0f, 100f)
                val now = System.currentTimeMillis()
                val shouldEmit = (now - lastUpdateMs >= 120L) ||
                    abs(progressFloat - lastReportedProgress) >= 2f ||
                    progressFloat >= 99.5f

                if (shouldEmit) {
                    lastUpdateMs = now
                    lastReportedProgress = progressFloat
                    val statusText = if (etaInSeconds > 0) {
                        "Progresso: ${progressFloat.toInt()}% (Restam aprox. ${etaInSeconds}s)"
                    } else {
                        line.ifBlank { "Processando mídia..." }
                    }
                    onProgress(progressFloat, statusText)
                }
            }

            val finalFile = resolveOutputFile(targetDir, startTimeMs, safeBaseName, format.ext)

            if (finalFile.isFile) {
                try {
                    MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null, null)
                } catch (e: Exception) {
                    // Silencioso se scanner falhar
                }
            }

            Result.success(finalFile)
        } catch (e: YoutubeDL.CanceledException) {
            cleanupTempFiles(targetDir, startTimeMs)
            Result.failure(e)
        } catch (e: YoutubeDLException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            activeTasks.decrementAndGet()
        }
    }

    private fun cleanupTempFiles(targetDir: File, sinceMs: Long) {
        targetDir.listFiles()?.forEach { file ->
            if (file.isFile &&
                file.lastModified() >= sinceMs &&
                (file.name.endsWith(".part", ignoreCase = true) ||
                    file.name.endsWith(".ytdl", ignoreCase = true) ||
                    file.name.endsWith(".temp", ignoreCase = true))
            ) {
                file.delete()
            }
        }
    }

    private fun resolveOutputFile(
        targetDir: File,
        startTimeMs: Long,
        safeBaseName: String,
        expectedExt: String
    ): File {
        val candidates = targetDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    !file.name.endsWith(".part", ignoreCase = true) &&
                    !file.name.endsWith(".ytdl", ignoreCase = true) &&
                    !file.name.endsWith(".temp", ignoreCase = true) &&
                    !file.name.endsWith(".tmp", ignoreCase = true)
            }
            .orEmpty()

        val recent = candidates
            .filter { it.lastModified() >= startTimeMs }
            .sortedByDescending { it.lastModified() }

        val recentExactExt = recent.firstOrNull { it.extension.equals(expectedExt, ignoreCase = true) }
        if (recentExactExt != null) return recentExactExt

        val recentAnyMedia = recent.firstOrNull {
            !it.extension.equals("jpg", ignoreCase = true) &&
                !it.extension.equals("jpeg", ignoreCase = true) &&
                !it.extension.equals("webp", ignoreCase = true) &&
                !it.extension.equals("png", ignoreCase = true)
        }
        if (recentAnyMedia != null) return recentAnyMedia

        val matchingName = candidates
            .filter { !safeBaseName.startsWith("%(") && it.nameWithoutExtension.startsWith(safeBaseName) }
            .sortedByDescending { it.lastModified() }
            .firstOrNull()

        return matchingName ?: recent.firstOrNull() ?: targetDir
    }
}
