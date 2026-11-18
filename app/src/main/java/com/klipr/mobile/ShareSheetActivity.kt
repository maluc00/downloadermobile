package com.klipr.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.klipr.mobile.data.DownloaderEngine
import com.klipr.mobile.data.SelectedFormat
import com.klipr.mobile.data.TimeTrim
import com.klipr.mobile.data.VideoChapter
import com.klipr.mobile.data.VideoInfo
import com.klipr.mobile.service.DownloadForegroundService
import com.klipr.mobile.ui.FormatSelectorSection
import com.klipr.mobile.ui.MediaInfoHeader
import com.klipr.mobile.ui.TrimControlsPanel
import com.klipr.mobile.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow

class ShareSheetActivity : ComponentActivity() {

    private val incomingUrlFlow = MutableStateFlow<Pair<String, Long>>("" to 0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialUrl = extractUrlFromIntent(intent)
        if (initialUrl.isBlank()) {
            Toast.makeText(this, "Nenhum link válido encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        incomingUrlFlow.value = initialUrl to System.nanoTime()

        setContent {
            val urlEvent by incomingUrlFlow.collectAsState()
            KliprTheme {
                QuickShareBottomSheet(
                    activity = this,
                    urlEvent = urlEvent,
                    onOpenFullApp = { targetUrl ->
                        val fullIntent = Intent(this, MainActivity::class.java).apply {
                            putExtra(MainActivity.EXTRA_PREFILL_URL, targetUrl)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(fullIntent)
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUrl = extractUrlFromIntent(intent)
        if (newUrl.isNotBlank()) {
            incomingUrlFlow.value = newUrl to System.nanoTime()
        }
    }

    private fun extractUrlFromIntent(intent: Intent?): String {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return ""
            return DownloaderEngine.sanitizeUrl(text)
        }
        return ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickShareBottomSheet(
    activity: ComponentActivity,
    urlEvent: Pair<String, Long>,
    onOpenFullApp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val dlState by DownloadForegroundService.downloadState.collectAsState()

    val targetUrl = urlEvent.first
    var isInspecting by remember { mutableStateOf(true) }
    var isDirectPlaylist by remember { mutableStateOf(false) }
    var inspectError by remember { mutableStateOf<String?>(null) }
    var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var retryCounter by remember { mutableIntStateOf(0) }

    var isAudioMode by remember { mutableStateOf(false) }
    var selectedFormat by remember {
        mutableStateOf(SelectedFormat(isAudio = false, height = 1080, ext = "mp4", label = "1080p Full HD"))
    }

    var isTrimmingEnabled by remember { mutableStateOf(false) }
    var trimStartSeconds by remember { mutableStateOf(0L) }
    var trimEndSeconds by remember { mutableStateOf(0L) }
    var trimStartTime by remember { mutableStateOf("00:00") }
    var trimEndTime by remember { mutableStateOf("00:00") }
    var accurateCut by remember { mutableStateOf(false) }

    fun videoLabelForHeight(h: Int): String = when (h) {
        2160 -> "2160p (4K)"
        1440 -> "1440p (2K)"
        1080 -> "1080p Full HD"
        720 -> "720p Rápido ⚡"
        else -> "${h}p"
    }

    fun launchBackgroundDownload() {
        val info = videoInfo ?: return
        val trimConfig = if (isTrimmingEnabled) {
            if (!DownloaderEngine.isValidTrimRange(trimStartTime, trimEndTime, info.durationSeconds)) {
                Toast.makeText(activity, "Intervalo de corte inválido", Toast.LENGTH_SHORT).show()
                return
            }
            TimeTrim(startTime = trimStartTime, endTime = trimEndTime, accurateCut = accurateCut)
        } else {
            null
        }

        val cleanTitle = info.title.replace(Regex("[\\\\/:*?\"<>|]"), "").take(60)
        DownloadForegroundService.startDownload(
            context = activity.applicationContext,
            url = info.url,
            title = info.title,
            format = selectedFormat,
            trim = trimConfig,
            customFileName = cleanTitle,
            targetSubfolder = "Klipr"
        )
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        Toast.makeText(activity, "⚡ Baixando em 2º plano...", Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        launchBackgroundDownload()
    }

    fun triggerDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        launchBackgroundDownload()
    }

    LaunchedEffect(urlEvent, retryCounter) {
        if (targetUrl.isBlank()) return@LaunchedEffect
        if (DownloaderEngine.isDirectPlaylistUrl(targetUrl)) {
            isInspecting = false
            isDirectPlaylist = true
            inspectError = null
            videoInfo = null
            return@LaunchedEffect
        }
        isDirectPlaylist = false
        isInspecting = true
        inspectError = null
        videoInfo = null
        val result = DownloaderEngine.fetchInfo(targetUrl, activity)
        isInspecting = false
        result.onSuccess { info ->
            videoInfo = info
            val bestHeight = info.availableHeights.firstOrNull() ?: 1080
            selectedFormat = SelectedFormat(
                isAudio = false,
                height = bestHeight,
                ext = "mp4",
                label = videoLabelForHeight(bestHeight)
            )
            trimStartSeconds = 0L
            trimEndSeconds = info.durationSeconds.coerceAtLeast(0L)
            trimStartTime = "00:00"
            trimEndTime = info.durationText
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }.onFailure { err ->
            inspectError = err.message ?: "Não foi possível analisar este link."
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KliprCard,
        contentColor = KliprTextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = KliprBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabeçalho do Bottom Sheet
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚡ KLIPR RÁPIDO",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = KliprPrimary,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = KliprSecondary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "2º PLANO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = KliprSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Fechar",
                        tint = KliprTextSecondary
                    )
                }
            }

            if (isInspecting) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = KliprSurface,
                    border = BorderStroke(1.dp, KliprBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = KliprSecondary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mapeando qualidades e tamanhos...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KliprTextPrimary
                            )
                            Text(
                                text = targetUrl,
                                fontSize = 11.sp,
                                color = KliprTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (isDirectPlaylist) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = KliprSurface,
                    border = BorderStroke(1.dp, KliprSecondary.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "📑 Playlist detectada!",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = KliprTextPrimary
                        )
                        Text(
                            text = "Abra no app completo para selecionar exatamente quais músicas ou vídeos deseja baixar na fila.",
                            fontSize = 12.sp,
                            color = KliprTextSecondary
                        )
                        Button(
                            onClick = { onOpenFullApp(targetUrl) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KliprPrimary)
                        ) {
                            Text(
                                text = "↗️ Selecionar Itens da Playlist",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            inspectError?.let { errorMsg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = KliprError.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, KliprError.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Erro ao analisar link: $errorMsg",
                            fontSize = 13.sp,
                            color = KliprError
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { retryCounter++ },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KliprPrimary)
                            ) {
                                Text("Tentar novamente", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onOpenFullApp(targetUrl) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, KliprBorder)
                            ) {
                                Text("Abrir Completo", fontSize = 13.sp, color = KliprTextPrimary)
                            }
                        }
                    }
                }
            }

            videoInfo?.let { info ->
                MediaInfoHeader(info = info)

                HorizontalDivider(color = KliprBorder.copy(alpha = 0.6f))

                FormatSelectorSection(
                    info = info,
                    isAudioMode = isAudioMode,
                    selectedFormat = selectedFormat,
                    enabled = true,
                    videoLabelForHeight = ::videoLabelForHeight,
                    onModeChange = { audio ->
                        isAudioMode = audio
                        selectedFormat = if (audio) {
                            SelectedFormat(isAudio = true, ext = "mp3", label = "MP3 (320kbps)")
                        } else {
                            val bestHeight = info.availableHeights.firstOrNull() ?: 1080
                            SelectedFormat(
                                isAudio = false,
                                height = bestHeight,
                                ext = "mp4",
                                label = videoLabelForHeight(bestHeight)
                            )
                        }
                    },
                    onSelectFormat = { selectedFormat = it }
                )

                // Toggle rápido de Corte dentro do Bottom Sheet
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isTrimmingEnabled = !isTrimmingEnabled },
                    shape = RoundedCornerShape(12.dp),
                    color = KliprSurface,
                    border = BorderStroke(1.dp, if (isTrimmingEnabled) KliprPrimary.copy(alpha = 0.6f) else KliprBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_cut),
                                contentDescription = null,
                                tint = KliprAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cortar Trecho",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = KliprTextPrimary
                            )
                        }
                        Switch(
                            checked = isTrimmingEnabled,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = KliprPrimary
                            )
                        )
                    }
                }

                AnimatedVisibility(visible = isTrimmingEnabled) {
                    TrimControlsPanel(
                        durationSeconds = info.durationSeconds,
                        trimStartSeconds = trimStartSeconds,
                        trimEndSeconds = trimEndSeconds,
                        trimStartTime = trimStartTime,
                        trimEndTime = trimEndTime,
                        accurateCut = accurateCut,
                        isAudioMode = isAudioMode,
                        chapters = info.chapters,
                        enabled = true,
                        previewStreamUrl = info.previewStreamUrl,
                        previewHeaders = info.previewHeaders,
                        onRangeChange = { s, e ->
                            val maxSecs = info.durationSeconds.coerceAtLeast(1L)
                            val safeEnd = e.coerceIn(1L.coerceAtMost(maxSecs), maxSecs)
                            val safeStart = s.coerceIn(0L, (safeEnd - 1L).coerceAtLeast(0L))
                            trimStartSeconds = safeStart
                            trimEndSeconds = safeEnd
                            trimStartTime = DownloaderEngine.formatDuration(safeStart)
                            trimEndTime = DownloaderEngine.formatDuration(safeEnd)
                        },
                        onNudgeStart = { delta ->
                            val maxEnd = (trimEndSeconds - 1L).coerceAtLeast(0L)
                            val newStart = (trimStartSeconds + delta).coerceIn(0L, maxEnd)
                            trimStartSeconds = newStart
                            trimStartTime = DownloaderEngine.formatDuration(newStart)
                        },
                        onNudgeEnd = { delta ->
                            val maxDur = info.durationSeconds
                            val minEnd = (trimStartSeconds + 1L).coerceAtMost(maxDur)
                            val newEnd = (trimEndSeconds + delta).coerceIn(minEnd, maxDur)
                            trimEndSeconds = newEnd
                            trimEndTime = DownloaderEngine.formatDuration(newEnd)
                        },
                        onStartTextChange = { text ->
                            trimStartTime = text
                            DownloaderEngine.parseTimeToSeconds(text)?.let { sec ->
                                if (sec <= info.durationSeconds) trimStartSeconds = sec
                            }
                        },
                        onEndTextChange = { text ->
                            trimEndTime = text
                            DownloaderEngine.parseTimeToSeconds(text)?.let { sec ->
                                if (sec <= info.durationSeconds) trimEndSeconds = sec
                            }
                        },
                        onSelectChapter = { chapter: VideoChapter ->
                            isTrimmingEnabled = true
                            trimStartSeconds = chapter.startSeconds
                            trimEndSeconds = chapter.endSeconds
                            trimStartTime = chapter.startTime
                            trimEndTime = chapter.endTime
                        },
                        onSelectQuickPreset = { limitSecs ->
                            val end = limitSecs.coerceIn(1L, info.durationSeconds.coerceAtLeast(1L))
                            trimStartSeconds = 0L
                            trimEndSeconds = end
                            trimStartTime = "00:00"
                            trimEndTime = DownloaderEngine.formatDuration(end)
                        },
                        onAccurateCutChange = { accurateCut = it }
                    )
                }

                if (dlState.isDownloading) {
                    Text(
                        text = "📥 Download ativo: ${dlState.title} (${dlState.progress.toInt()}%). Este vídeo entrará na fila.",
                        fontSize = 11.sp,
                        color = KliprSecondary
                    )
                }

                // Botão Principal: Baixar em 2º Plano
                Button(
                    onClick = { triggerDownload() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(listOf(KliprPrimary, KliprSecondary)),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isTrimmingEnabled) "Cortar & Baixar em 2º Plano" else "Baixar em 2º Plano",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Botão Secundário: Abrir no App Completo
                OutlinedButton(
                    onClick = { onOpenFullApp(info.url) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, KliprBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KliprTextPrimary)
                ) {
                    Text(
                        text = "↗️ Abrir no App Completo (Nome/Pasta)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
