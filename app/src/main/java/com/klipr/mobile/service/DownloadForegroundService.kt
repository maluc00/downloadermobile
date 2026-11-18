package com.klipr.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.klipr.mobile.MainActivity
import com.klipr.mobile.R
import com.klipr.mobile.data.DownloadHistoryItem
import com.klipr.mobile.data.DownloaderEngine
import com.klipr.mobile.data.KliprPreferences
import com.klipr.mobile.data.SelectedFormat
import com.klipr.mobile.data.TimeTrim
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class DownloadServiceState(
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val title: String = "",
    val url: String = "",
    val formatLabel: String = "",
    val isAudio: Boolean = false,
    val targetSubfolder: String = "Klipr",
    val completedFile: File? = null,
    val isCompleted: Boolean = false,
    val isError: Boolean = false,
    val completedTimestamp: Long = 0L,
    val queuedCount: Int = 0
)

private data class QueuedDownload(
    val url: String,
    val title: String,
    val format: SelectedFormat,
    val trim: TimeTrim?,
    val customFileName: String?,
    val subfolder: String
)

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queueLock = Any()
    private val pendingQueue = ArrayDeque<QueuedDownload>()
    private var queueProcessorJob: Job? = null
    private var activeProcessId: String = DownloaderEngine.DEFAULT_PROCESS_ID

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                synchronized(queueLock) {
                    pendingQueue.clear()
                }
                DownloaderEngine.cancelDownload(activeProcessId)
                queueProcessorJob?.cancel()
                _downloadState.value = _downloadState.value.copy(
                    isDownloading = false,
                    isCompleted = false,
                    isError = false,
                    queuedCount = 0,
                    statusMessage = "Download cancelado."
                )
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL).orEmpty()
                if (url.isBlank()) {
                    if (queueProcessorJob?.isActive != true) stopSelf()
                    return START_NOT_STICKY
                }
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Mídia Klipr" }
                val isAudio = intent.getBooleanExtra(EXTRA_IS_AUDIO, false)
                val height = intent.getIntExtra(EXTRA_HEIGHT, 1080)
                val ext = intent.getStringExtra(EXTRA_EXT).orEmpty().ifBlank { if (isAudio) "mp3" else "mp4" }
                val formatLabel = intent.getStringExtra(EXTRA_FORMAT_LABEL).orEmpty().ifBlank { ext.uppercase() }
                val trimEnabled = intent.getBooleanExtra(EXTRA_TRIM_ENABLED, false)
                val trimStart = intent.getStringExtra(EXTRA_TRIM_START).orEmpty()
                val trimEnd = intent.getStringExtra(EXTRA_TRIM_END).orEmpty()
                val trimAccurate = intent.getBooleanExtra(EXTRA_TRIM_ACCURATE, false)
                val customFileName = intent.getStringExtra(EXTRA_FILE_NAME)
                val subfolder = intent.getStringExtra(EXTRA_SUBFOLDER).orEmpty().ifBlank { "Klipr" }

                val queuedItem = QueuedDownload(
                    url = url,
                    title = title,
                    format = SelectedFormat(
                        isAudio = isAudio,
                        height = height,
                        ext = ext,
                        label = formatLabel
                    ),
                    trim = if (trimEnabled && trimStart.isNotBlank() && trimEnd.isNotBlank()) {
                        TimeTrim(startTime = trimStart, endTime = trimEnd, accurateCut = trimAccurate)
                    } else {
                        null
                    },
                    customFileName = customFileName,
                    subfolder = subfolder
                )

                val alreadyRunning: Boolean
                val queueSizeAfterAdd: Int
                synchronized(queueLock) {
                    alreadyRunning = queueProcessorJob?.isActive == true
                    pendingQueue.addLast(queuedItem)
                    queueSizeAfterAdd = pendingQueue.size
                }

                // Sempre chamar startForeground imediatamente para cumprir o contrato do Android
                val currentState = _downloadState.value
                val notifTitle = if (alreadyRunning && currentState.title.isNotBlank()) {
                    currentState.title
                } else {
                    title
                }
                val notifProgress = if (alreadyRunning) currentState.progress.toInt() else 0
                val notifStatus = if (alreadyRunning) {
                    currentState.statusMessage.ifBlank { "Baixando..." }
                } else {
                    "Preparando download..."
                }
                val foregroundNotification = buildProgressNotification(
                    title = notifTitle,
                    progress = notifProgress,
                    status = notifStatus,
                    queuedCount = if (alreadyRunning) queueSizeAfterAdd else 0
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        PROGRESS_NOTIFICATION_ID,
                        foregroundNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(PROGRESS_NOTIFICATION_ID, foregroundNotification)
                }

                if (alreadyRunning) {
                    _downloadState.value = _downloadState.value.copy(queuedCount = queueSizeAfterAdd)
                    Toast.makeText(
                        this,
                        "📥 Adicionado à fila ($queueSizeAfterAdd na espera)",
                        Toast.LENGTH_SHORT
                    ).show()
                    return START_NOT_STICKY
                }

                startQueueProcessor()
            }
        }
        return START_NOT_STICKY
    }

    private fun startQueueProcessor() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        queueProcessorJob = serviceScope.launch {
            var nextCompletionId = COMPLETION_NOTIFICATION_ID_BASE
            while (true) {
                val item: QueuedDownload?
                val remainingCount: Int
                synchronized(queueLock) {
                    item = pendingQueue.removeFirstOrNull()
                    remainingCount = pendingQueue.size
                }
                if (item == null) break

                activeProcessId = "klipr_${System.currentTimeMillis()}"
                val processId = activeProcessId

                _downloadState.value = DownloadServiceState(
                    isDownloading = true,
                    progress = 0f,
                    statusMessage = "Iniciando download...",
                    title = item.title,
                    url = item.url,
                    formatLabel = item.format.label,
                    isAudio = item.format.isAudio,
                    targetSubfolder = item.subfolder,
                    completedFile = null,
                    isCompleted = false,
                    isError = false,
                    queuedCount = remainingCount
                )

                val result = DownloaderEngine.startDownload(
                    context = applicationContext,
                    url = item.url,
                    format = item.format,
                    trim = item.trim,
                    customFileName = item.customFileName,
                    targetSubfolder = item.subfolder,
                    processId = processId,
                    onProgress = { progress, line ->
                        val currentQueued = synchronized(queueLock) { pendingQueue.size }
                        _downloadState.value = _downloadState.value.copy(
                            isDownloading = true,
                            progress = progress,
                            statusMessage = line,
                            queuedCount = currentQueued
                        )
                        notificationManager.notify(
                            PROGRESS_NOTIFICATION_ID,
                            buildProgressNotification(
                                title = item.title,
                                progress = progress.toInt(),
                                status = line,
                                queuedCount = currentQueued
                            )
                        )
                    }
                )

                val currentQueuedAfter = synchronized(queueLock) { pendingQueue.size }

                result.onSuccess { outputFile ->
                    val realFile = outputFile.takeIf { it.isFile }
                    KliprPreferences.saveCustomSubfolder(applicationContext, item.subfolder)
                    if (realFile != null) {
                        KliprPreferences.addHistoryItem(
                            applicationContext,
                            DownloadHistoryItem(
                                title = item.customFileName?.takeIf { it.isNotBlank() } ?: item.title,
                                filePath = realFile.absolutePath,
                                formatLabel = item.format.label,
                                timestamp = System.currentTimeMillis(),
                                isAudio = item.format.isAudio
                            )
                        )
                    }

                    _downloadState.value = _downloadState.value.copy(
                        isDownloading = currentQueuedAfter > 0,
                        progress = 100f,
                        statusMessage = "✅ Concluído! Salvo em Downloads/${item.subfolder}",
                        completedFile = realFile,
                        isCompleted = true,
                        isError = false,
                        completedTimestamp = System.currentTimeMillis(),
                        queuedCount = currentQueuedAfter
                    )
                    notificationManager.notify(
                        nextCompletionId++,
                        buildCompletionNotification(
                            title = item.title,
                            subfolder = item.subfolder,
                            file = realFile,
                            requestCodeSeed = nextCompletionId
                        )
                    )
                }.onFailure { err ->
                    val wasCanceled = err is YoutubeDL.CanceledException
                    _downloadState.value = _downloadState.value.copy(
                        isDownloading = currentQueuedAfter > 0,
                        isCompleted = false,
                        isError = !wasCanceled,
                        statusMessage = if (wasCanceled) {
                            "Download cancelado."
                        } else {
                            "Erro no download: ${err.message}"
                        },
                        queuedCount = currentQueuedAfter
                    )
                    if (!wasCanceled) {
                        notificationManager.notify(
                            nextCompletionId++,
                            buildErrorNotification(item.title, err.message ?: "Falha inesperada")
                        )
                    }
                }
            }

            ServiceCompat.stopForeground(
                this@DownloadForegroundService,
                ServiceCompat.STOP_FOREGROUND_REMOVE
            )
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS,
                "Downloads em Andamento",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Exibe o progresso dos downloads em segundo plano"
            }
            val doneChannel = NotificationChannel(
                CHANNEL_DONE,
                "Downloads Concluídos",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações com atalhos para abrir ou compartilhar mídia"
            }
            manager.createNotificationChannel(progressChannel)
            manager.createNotificationChannel(doneChannel)
        }
    }

    private fun buildProgressNotification(
        title: String,
        progress: Int,
        status: String,
        queuedCount: Int
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this,
            10,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this,
            11,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val headerText = if (queuedCount > 0) {
            "Baixando (+$queuedCount na fila): $title"
        } else {
            "Baixando: $title"
        }

        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(headerText)
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .setContentIntent(contentPending)
            .addAction(R.drawable.ic_stop, "Cancelar", cancelPending)
            .build()
    }

    private fun buildCompletionNotification(
        title: String,
        subfolder: String,
        file: File?,
        requestCodeSeed: Int
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("✅ Download concluído")
            .setContentText("$title • Downloads/$subfolder")
            .setAutoCancel(true)

        if (file != null && file.exists()) {
            try {
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                val mime = MainActivity.mimeTypeFor(file)

                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val openPending = PendingIntent.getActivity(
                    this,
                    requestCodeSeed * 10 + 1,
                    viewIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, "Compartilhar mídia").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val sharePending = PendingIntent.getActivity(
                    this,
                    requestCodeSeed * 10 + 2,
                    chooser,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                builder.setContentIntent(openPending)
                builder.addAction(R.drawable.ic_video, "Abrir", openPending)
                builder.addAction(R.drawable.ic_download, "Compartilhar", sharePending)
            } catch (e: Exception) {
                // Fallback para abrir o app
            }
        } else {
            val openAppIntent = Intent(this, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                this,
                requestCodeSeed * 10 + 3,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pending)
        }

        return builder.build()
    }

    private fun buildErrorNotification(title: String, message: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            30,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stop)
            .setContentTitle("Falha ao baixar: $title")
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
    }

    companion object {
        private const val CHANNEL_PROGRESS = "klipr_downloads"
        private const val CHANNEL_DONE = "klipr_downloads_done"
        private const val PROGRESS_NOTIFICATION_ID = 1001
        private const val COMPLETION_NOTIFICATION_ID_BASE = 1002

        const val ACTION_START = "com.klipr.mobile.action.START_DOWNLOAD"
        const val ACTION_CANCEL = "com.klipr.mobile.action.CANCEL_DOWNLOAD"

        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_IS_AUDIO = "extra_is_audio"
        private const val EXTRA_HEIGHT = "extra_height"
        private const val EXTRA_EXT = "extra_ext"
        private const val EXTRA_FORMAT_LABEL = "extra_format_label"
        private const val EXTRA_TRIM_ENABLED = "extra_trim_enabled"
        private const val EXTRA_TRIM_START = "extra_trim_start"
        private const val EXTRA_TRIM_END = "extra_trim_end"
        private const val EXTRA_TRIM_ACCURATE = "extra_trim_accurate"
        private const val EXTRA_FILE_NAME = "extra_file_name"
        private const val EXTRA_SUBFOLDER = "extra_subfolder"

        private val _downloadState = MutableStateFlow(DownloadServiceState())
        val downloadState: StateFlow<DownloadServiceState> = _downloadState.asStateFlow()

        fun clearStatus() {
            if (!_downloadState.value.isDownloading) {
                _downloadState.value = DownloadServiceState()
            }
        }

        fun startDownload(
            context: Context,
            url: String,
            title: String,
            format: SelectedFormat,
            trim: TimeTrim?,
            customFileName: String?,
            targetSubfolder: String
        ) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_IS_AUDIO, format.isAudio)
                putExtra(EXTRA_HEIGHT, format.height)
                putExtra(EXTRA_EXT, format.ext)
                putExtra(EXTRA_FORMAT_LABEL, format.label)
                putExtra(EXTRA_TRIM_ENABLED, trim != null)
                putExtra(EXTRA_TRIM_START, trim?.startTime.orEmpty())
                putExtra(EXTRA_TRIM_END, trim?.endTime.orEmpty())
                putExtra(EXTRA_TRIM_ACCURATE, trim?.accurateCut ?: false)
                putExtra(EXTRA_FILE_NAME, customFileName)
                putExtra(EXTRA_SUBFOLDER, targetSubfolder)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelDownload(context: Context) {
            if (_downloadState.value.isDownloading) {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_CANCEL
                }
                context.startService(intent)
            }
        }
    }
}
