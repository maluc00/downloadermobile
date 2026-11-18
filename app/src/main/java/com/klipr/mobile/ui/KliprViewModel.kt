package com.klipr.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.klipr.mobile.KliprApp
import com.klipr.mobile.data.AppReleaseInfo
import com.klipr.mobile.data.AppUpdater
import com.klipr.mobile.data.DownloadHistoryItem
import com.klipr.mobile.data.DownloaderEngine
import com.klipr.mobile.data.KliprPreferences
import com.klipr.mobile.data.SelectedFormat
import com.klipr.mobile.data.TimeTrim
import com.klipr.mobile.data.VideoChapter
import com.klipr.mobile.data.VideoInfo
import com.klipr.mobile.service.DownloadForegroundService
import com.klipr.mobile.service.DownloadServiceState
import com.klipr.mobile.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KliprUiState(
    val urlText: String = "",
    val isInspecting: Boolean = false,
    val inspectStatusMessage: String = "",
    val inspectIsError: Boolean = false,
    val videoInfo: VideoInfo? = null,
    val isAudioMode: Boolean = false,
    val selectedFormat: SelectedFormat = SelectedFormat(),
    val isTrimmingEnabled: Boolean = false,
    val trimStartSeconds: Long = 0L,
    val trimEndSeconds: Long = 0L,
    val trimStartTime: String = "00:00",
    val trimEndTime: String = "00:00",
    val accurateCut: Boolean = false,
    val showSaveAs: Boolean = false,
    val customFileName: String = "",
    val targetSubfolder: String = "Klipr",
    val clipboardSuggestionUrl: String? = null,
    val recentDownloads: List<DownloadHistoryItem> = emptyList(),
    val savedSubfolders: List<String> = KliprPreferences.DEFAULT_FOLDERS,
    val hapticTick: Int = 0,
    // Temas, Motor & Auto-Update
    val showSettingsSheet: Boolean = false,
    val currentTheme: AppThemeMode = AppThemeMode.DARK_AURORA,
    val useAria2c: Boolean = true,
    val useNightlyYtDlp: Boolean = true,
    val sponsorBlockEnabled: Boolean = false,
    val ytDlpVersion: String = "...",
    val isUpdatingYtDlp: Boolean = false,
    val ytDlpUpdateStatus: String = "",
    val availableAppUpdate: AppReleaseInfo? = null,
    val isCheckingAppUpdate: Boolean = false,
    val isDownloadingAppUpdate: Boolean = false,
    val appUpdateProgress: Int = 0,
    val appUpdateStatus: String = ""
)

class KliprViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val _uiState = MutableStateFlow(
        KliprUiState(
            recentDownloads = KliprPreferences.loadHistory(appContext),
            savedSubfolders = KliprPreferences.loadSubfolders(appContext),
            currentTheme = AppThemeMode.fromId(KliprPreferences.getThemeMode(appContext)),
            useAria2c = KliprPreferences.isAria2cEnabled(appContext),
            useNightlyYtDlp = KliprPreferences.isNightlyYtDlpEnabled(appContext),
            sponsorBlockEnabled = KliprPreferences.isSponsorBlockEnabled(appContext),
            ytDlpVersion = KliprApp.getYtDlpVersion(appContext)
        )
    )
    val uiState: StateFlow<KliprUiState> = _uiState.asStateFlow()

    val downloadState: StateFlow<DownloadServiceState> = DownloadForegroundService.downloadState

    private var lastObservedCompletionTs = 0L

    init {
        viewModelScope.launch {
            DownloadForegroundService.downloadState.collect { dlState ->
                if (dlState.isCompleted && dlState.completedTimestamp > lastObservedCompletionTs) {
                    lastObservedCompletionTs = dlState.completedTimestamp
                    _uiState.update {
                        it.copy(
                            recentDownloads = KliprPreferences.loadHistory(appContext),
                            savedSubfolders = KliprPreferences.loadSubfolders(appContext),
                            hapticTick = it.hapticTick + 1
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            KliprApp.awaitEngineReady()
            _uiState.update { it.copy(ytDlpVersion = KliprApp.getYtDlpVersion(appContext)) }
        }
        checkAppUpdate(manual = false)
    }

    fun videoLabelForHeight(h: Int): String = when (h) {
        2160 -> "2160p (4K)"
        1440 -> "1440p (2K)"
        1080 -> "1080p Full HD"
        720 -> "720p Rápido ⚡"
        else -> "${h}p"
    }

    fun setShowSettingsSheet(show: Boolean) {
        _uiState.update {
            it.copy(
                showSettingsSheet = show,
                ytDlpVersion = KliprApp.getYtDlpVersion(appContext)
            )
        }
    }

    fun selectTheme(mode: AppThemeMode) {
        KliprPreferences.setThemeMode(appContext, mode.id)
        _uiState.update { it.copy(currentTheme = mode) }
    }

    fun setAria2cEnabled(enabled: Boolean) {
        KliprPreferences.setAria2cEnabled(appContext, enabled)
        _uiState.update { it.copy(useAria2c = enabled) }
    }

    fun setNightlyYtDlpEnabled(enabled: Boolean) {
        KliprPreferences.setNightlyYtDlpEnabled(appContext, enabled)
        _uiState.update { it.copy(useNightlyYtDlp = enabled) }
    }

    fun setSponsorBlockEnabled(enabled: Boolean) {
        KliprPreferences.setSponsorBlockEnabled(appContext, enabled)
        _uiState.update { it.copy(sponsorBlockEnabled = enabled) }
    }

    fun updateYtDlpNow() {
        if (_uiState.value.isUpdatingYtDlp) return
        _uiState.update {
            it.copy(
                isUpdatingYtDlp = true,
                ytDlpUpdateStatus = "Verificando atualização do motor..."
            )
        }
        viewModelScope.launch {
            val nightly = _uiState.value.useNightlyYtDlp
            val result = KliprApp.updateYtDlpNow(appContext, nightly)
            result.onSuccess { msg ->
                _uiState.update {
                    it.copy(
                        isUpdatingYtDlp = false,
                        ytDlpVersion = KliprApp.getYtDlpVersion(appContext),
                        ytDlpUpdateStatus = msg
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isUpdatingYtDlp = false,
                        ytDlpUpdateStatus = "Falha ao atualizar motor: ${err.message}"
                    )
                }
            }
        }
    }

    fun checkAppUpdate(manual: Boolean) {
        if (_uiState.value.isCheckingAppUpdate) return
        _uiState.update {
            it.copy(
                isCheckingAppUpdate = true,
                appUpdateStatus = if (manual) "Consultando GitHub Releases..." else it.appUpdateStatus
            )
        }
        viewModelScope.launch {
            val result = AppUpdater.checkForUpdate()
            result.onSuccess { release ->
                _uiState.update {
                    it.copy(
                        isCheckingAppUpdate = false,
                        availableAppUpdate = release,
                        appUpdateStatus = when {
                            release != null -> "Nova versão ${release.tagName} pronta para instalar!"
                            manual -> "Você já está na versão mais recente."
                            else -> ""
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCheckingAppUpdate = false,
                        appUpdateStatus = if (manual) "Erro ao verificar atualização: ${err.message}" else ""
                    )
                }
            }
        }
    }

    fun downloadAndInstallAppUpdate() {
        val release = _uiState.value.availableAppUpdate ?: return
        if (_uiState.value.isDownloadingAppUpdate) return
        _uiState.update {
            it.copy(
                isDownloadingAppUpdate = true,
                appUpdateProgress = 0,
                appUpdateStatus = "Baixando ${release.apkName}..."
            )
        }
        viewModelScope.launch {
            val result = AppUpdater.downloadAndInstallApk(appContext, release) { pct ->
                _uiState.update { it.copy(appUpdateProgress = pct) }
            }
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isDownloadingAppUpdate = false,
                        appUpdateProgress = 100,
                        appUpdateStatus = "Instalador aberto para ${release.tagName}."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isDownloadingAppUpdate = false,
                        appUpdateStatus = "Erro ao baixar APK: ${err.message}"
                    )
                }
            }
        }
    }

    fun dismissAppUpdateBanner() {
        _uiState.update { it.copy(availableAppUpdate = null) }
    }

    fun onUrlChanged(newUrl: String) {
        _uiState.update { it.copy(urlText = newUrl) }
    }

    fun clearUrl() {
        DownloadForegroundService.clearStatus()
        _uiState.update {
            it.copy(
                urlText = "",
                videoInfo = null,
                inspectStatusMessage = "",
                inspectIsError = false
            )
        }
    }

    fun inspectUrl(rawUrl: String) {
        val clean = DownloaderEngine.sanitizeUrl(rawUrl)
        if (clean.isBlank()) return

        DownloadForegroundService.clearStatus()
        _uiState.update {
            it.copy(
                urlText = clean,
                isInspecting = true,
                inspectStatusMessage = "Mapeando resoluções e faixas de mídia...",
                inspectIsError = false,
                videoInfo = null,
                clipboardSuggestionUrl = null
            )
        }

        viewModelScope.launch {
            val result = DownloaderEngine.fetchInfo(clean)
            result.onSuccess { info ->
                val defaultHeight = info.availableHeights.firstOrNull() ?: 1080
                val currentAudioMode = _uiState.value.isAudioMode
                val nextFormat = if (currentAudioMode) {
                    _uiState.value.selectedFormat
                } else {
                    SelectedFormat(
                        isAudio = false,
                        height = defaultHeight,
                        ext = "mp4",
                        label = videoLabelForHeight(defaultHeight)
                    )
                }
                val durationSecs = info.durationSeconds.coerceAtLeast(0L)
                _uiState.update {
                    it.copy(
                        isInspecting = false,
                        inspectStatusMessage = "",
                        inspectIsError = false,
                        videoInfo = info,
                        selectedFormat = nextFormat,
                        customFileName = info.title.replace(Regex("[\\\\/:*?\"<>|]"), "").take(60),
                        trimStartSeconds = 0L,
                        trimEndSeconds = durationSecs,
                        trimStartTime = "00:00",
                        trimEndTime = info.durationText,
                        hapticTick = it.hapticTick + 1
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isInspecting = false,
                        inspectStatusMessage = "Erro ao inspecionar link: ${err.message}",
                        inspectIsError = true
                    )
                }
            }
        }
    }

    fun checkClipboardForSuggestion(clipboardText: String?) {
        if (clipboardText.isNullOrBlank()) return
        val match = "(https?://[\\w-]+(\\.[\\w-]+)+(/[^\\s]*)?)".toRegex().find(clipboardText)?.value
            ?: return
        val clean = DownloaderEngine.sanitizeUrl(match)
        val current = _uiState.value.urlText.trim()
        val currentVideo = _uiState.value.videoInfo?.url.orEmpty()
        if (clean.isNotBlank() && clean != current && clean != currentVideo) {
            _uiState.update { it.copy(clipboardSuggestionUrl = clean) }
        }
    }

    fun dismissClipboardSuggestion() {
        _uiState.update { it.copy(clipboardSuggestionUrl = null) }
    }

    fun acceptClipboardSuggestion() {
        val suggested = _uiState.value.clipboardSuggestionUrl ?: return
        inspectUrl(suggested)
    }

    fun selectMediaMode(isAudio: Boolean) {
        val info = _uiState.value.videoInfo
        val newFormat = if (isAudio) {
            SelectedFormat(isAudio = true, ext = "mp3", label = "MP3 (320kbps)")
        } else {
            val bestHeight = info?.availableHeights?.firstOrNull() ?: 1080
            SelectedFormat(
                isAudio = false,
                height = bestHeight,
                ext = "mp4",
                label = videoLabelForHeight(bestHeight)
            )
        }
        _uiState.update {
            it.copy(
                isAudioMode = isAudio,
                selectedFormat = newFormat
            )
        }
    }

    fun selectFormat(format: SelectedFormat) {
        _uiState.update { it.copy(selectedFormat = format) }
    }

    fun toggleTrimming(enabled: Boolean) {
        _uiState.update { it.copy(isTrimmingEnabled = enabled) }
    }

    fun updateTrimRangeSeconds(startSecs: Long, endSecs: Long) {
        val maxSecs = _uiState.value.videoInfo?.durationSeconds ?: Long.MAX_VALUE
        val safeEnd = endSecs.coerceIn(1L.coerceAtMost(maxSecs), maxSecs)
        val safeStart = startSecs.coerceIn(0L, (safeEnd - 1L).coerceAtLeast(0L))
        _uiState.update {
            it.copy(
                trimStartSeconds = safeStart,
                trimEndSeconds = safeEnd,
                trimStartTime = DownloaderEngine.formatDuration(safeStart),
                trimEndTime = DownloaderEngine.formatDuration(safeEnd)
            )
        }
    }

    fun nudgeTrimStart(deltaSecs: Long) {
        val state = _uiState.value
        val maxEnd = (state.trimEndSeconds - 1L).coerceAtLeast(0L)
        val newStart = (state.trimStartSeconds + deltaSecs).coerceIn(0L, maxEnd)
        updateTrimRangeSeconds(newStart, state.trimEndSeconds)
    }

    fun nudgeTrimEnd(deltaSecs: Long) {
        val state = _uiState.value
        val maxDur = state.videoInfo?.durationSeconds ?: Long.MAX_VALUE
        val minEnd = (state.trimStartSeconds + 1L).coerceAtMost(maxDur)
        val newEnd = (state.trimEndSeconds + deltaSecs).coerceIn(minEnd, maxDur)
        updateTrimRangeSeconds(state.trimStartSeconds, newEnd)
    }

    fun onTrimStartTextChanged(text: String) {
        val parsed = DownloaderEngine.parseTimeToSeconds(text)
        _uiState.update { state ->
            val maxDur = state.videoInfo?.durationSeconds ?: Long.MAX_VALUE
            val validSec = if (parsed != null && parsed <= maxDur) parsed else state.trimStartSeconds
            state.copy(trimStartTime = text, trimStartSeconds = validSec)
        }
    }

    fun onTrimEndTextChanged(text: String) {
        val parsed = DownloaderEngine.parseTimeToSeconds(text)
        _uiState.update { state ->
            val maxDur = state.videoInfo?.durationSeconds ?: Long.MAX_VALUE
            val validSec = if (parsed != null && parsed <= maxDur) parsed else state.trimEndSeconds
            state.copy(trimEndTime = text, trimEndSeconds = validSec)
        }
    }

    fun selectChapter(chapter: VideoChapter) {
        _uiState.update {
            it.copy(
                isTrimmingEnabled = true,
                trimStartSeconds = chapter.startSeconds,
                trimEndSeconds = chapter.endSeconds,
                trimStartTime = chapter.startTime,
                trimEndTime = chapter.endTime
            )
        }
    }

    fun selectQuickTrimPreset(durationSecondsLimit: Long) {
        val total = _uiState.value.videoInfo?.durationSeconds ?: return
        val end = durationSecondsLimit.coerceIn(1L, total)
        updateTrimRangeSeconds(0L, end)
    }

    fun toggleAccurateCut(accurate: Boolean) {
        _uiState.update { it.copy(accurateCut = accurate) }
    }

    fun toggleSaveAs() {
        _uiState.update { it.copy(showSaveAs = !it.showSaveAs) }
    }

    fun onCustomFileNameChanged(name: String) {
        _uiState.update { it.copy(customFileName = name) }
    }

    fun onTargetSubfolderChanged(folder: String) {
        _uiState.update { it.copy(targetSubfolder = folder) }
    }

    fun isTrimRangeValid(): Boolean {
        val state = _uiState.value
        if (!state.isTrimmingEnabled) return true
        val maxDur = state.videoInfo?.durationSeconds ?: 0L
        return DownloaderEngine.isValidTrimRange(state.trimStartTime, state.trimEndTime, maxDur)
    }

    fun startDownload() {
        val state = _uiState.value
        val info = state.videoInfo ?: return
        if (state.isTrimmingEnabled && !isTrimRangeValid()) {
            _uiState.update {
                it.copy(
                    inspectStatusMessage = "Intervalo de corte inválido. Verifique Início e Fim.",
                    inspectIsError = true
                )
            }
            return
        }

        val trimConfig = if (state.isTrimmingEnabled) {
            TimeTrim(
                startTime = state.trimStartTime,
                endTime = state.trimEndTime,
                accurateCut = state.accurateCut
            )
        } else {
            null
        }

        _uiState.update {
            it.copy(
                inspectStatusMessage = "",
                inspectIsError = false
            )
        }

        DownloadForegroundService.startDownload(
            context = appContext,
            url = info.url,
            title = info.title,
            format = state.selectedFormat,
            trim = trimConfig,
            customFileName = state.customFileName,
            targetSubfolder = state.targetSubfolder
        )
    }

    fun cancelDownload() {
        DownloadForegroundService.cancelDownload(appContext)
    }

    fun refreshHistory() {
        _uiState.update {
            it.copy(
                recentDownloads = KliprPreferences.loadHistory(appContext),
                savedSubfolders = KliprPreferences.loadSubfolders(appContext)
            )
        }
    }

    fun removeFromHistory(filePath: String) {
        KliprPreferences.removeHistoryItem(appContext, filePath)
        _uiState.update {
            it.copy(recentDownloads = KliprPreferences.loadHistory(appContext))
        }
    }
}
