package com.klipr.mobile

import android.Manifest
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.klipr.mobile.data.DownloadHistoryItem
import com.klipr.mobile.data.DownloaderEngine
import com.klipr.mobile.ui.DownloadStatusAndActionsCard
import com.klipr.mobile.ui.FormatSelectorSection
import com.klipr.mobile.ui.KliprViewModel
import com.klipr.mobile.ui.MediaInfoHeader
import com.klipr.mobile.ui.SettingsAndThemesSheet
import com.klipr.mobile.ui.TrimControlsPanel
import com.klipr.mobile.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class MainActivity : ComponentActivity() {

    private val sharedUrlEvent = MutableStateFlow<Pair<String, Long>>("" to 0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialSharedUrl = extractUrlFromIntent(intent)
        if (initialSharedUrl.isNotBlank()) {
            sharedUrlEvent.value = initialSharedUrl to System.nanoTime()
        }

        setContent {
            val currentSharedEvent by sharedUrlEvent.collectAsState()
            KliprTheme {
                val isDark = LocalKliprPalette.current.isDark
                SideEffect {
                    enableEdgeToEdge(
                        statusBarStyle = if (isDark) {
                            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        } else {
                            SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT
                            )
                        },
                        navigationBarStyle = if (isDark) {
                            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        } else {
                            SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT
                            )
                        }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KliprPocketScreen(
                        context = this,
                        sharedUrlEvent = currentSharedEvent,
                        onOpenDownloads = { openDownloadsFolder(this) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUrl = extractUrlFromIntent(intent)
        if (newUrl.isNotBlank()) {
            sharedUrlEvent.value = newUrl to System.nanoTime()
        }
    }

    private fun extractUrlFromIntent(intent: Intent?): String {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return ""
            return DownloaderEngine.sanitizeUrl(text)
        }
        val extraUrl = intent?.getStringExtra(EXTRA_PREFILL_URL)
        return extraUrl?.let { DownloaderEngine.sanitizeUrl(it) }.orEmpty()
    }

    companion object {
        const val EXTRA_PREFILL_URL = "extra_prefill_url"

        fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> "*/*"
        }

        fun openMediaFile(context: Context, file: File) {
            if (!file.exists() || !file.isFile) {
                openDownloadsFolder(context)
                return
            }
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeTypeFor(file))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(viewIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Nenhum player encontrado para abrir ${file.name}", Toast.LENGTH_SHORT).show()
                openDownloadsFolder(context)
            }
        }

        fun shareMediaFile(context: Context, file: File) {
            if (!file.exists() || !file.isFile) {
                Toast.makeText(context, "Arquivo não encontrado para compartilhar", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeTypeFor(file)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, "Compartilhar mídia").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(context, "Não foi possível compartilhar o arquivo", Toast.LENGTH_SHORT).show()
            }
        }

        fun openDownloadsFolder(context: Context) {
            try {
                val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Salvo em: Downloads/Klipr", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun KliprPocketScreen(
    context: Context,
    sharedUrlEvent: Pair<String, Long>,
    onOpenDownloads: () -> Unit,
    viewModel: KliprViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dlState by viewModel.downloadState.collectAsState()
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val windowInfo = LocalWindowInfo.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.startDownload()
    }

    fun triggerDownloadWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        viewModel.startDownload()
    }

    fun readClipboardText(): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (e: Exception) {
            null
        }
    }

    fun pasteAndInspect() {
        val clip = readClipboardText()?.trim()
        if (!clip.isNullOrBlank()) {
            viewModel.inspectUrl(clip)
        } else {
            Toast.makeText(context, "Área de transferência vazia", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(uiState.hapticTick) {
        if (uiState.hapticTick > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(sharedUrlEvent) {
        val sharedUrl = sharedUrlEvent.first
        if (sharedUrl.isNotBlank()) {
            viewModel.inspectUrl(sharedUrl)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHistory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(windowInfo.isWindowFocused) {
        if (windowInfo.isWindowFocused) {
            viewModel.checkClipboardForSuggestion(readClipboardText())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar com Botões "Temas & Motor" e "Abrir Pasta" (>= 48dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⚡ KLIPR",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black,
                    color = KliprPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = KliprSecondary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, KliprSecondary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "POCKET",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = KliprSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedIconButton(
                    onClick = { viewModel.setShowSettingsSheet(true) },
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, KliprBorder),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = KliprAccent)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = "Temas e Motores",
                        modifier = Modifier.size(20.dp)
                    )
                }

                OutlinedButton(
                    onClick = onOpenDownloads,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, KliprBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KliprSecondary)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_folder),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Pasta", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Banner de Auto-Atualização via GitHub Releases
        AnimatedVisibility(visible = uiState.availableAppUpdate != null) {
            val release = uiState.availableAppUpdate
            if (release != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = KliprPrimary.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, KliprPrimary.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🚀 Nova versão ${release.tagName} disponível!",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KliprTextPrimary
                                )
                                if (uiState.appUpdateStatus.isNotBlank()) {
                                    Text(
                                        text = uiState.appUpdateStatus,
                                        fontSize = 11.sp,
                                        color = KliprTextSecondary
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.dismissAppUpdateBanner() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Fechar aviso",
                                    tint = KliprTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (uiState.isDownloadingAppUpdate) {
                            LinearProgressIndicator(
                                progress = { uiState.appUpdateProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = KliprPrimary,
                                trackColor = KliprSurface
                            )
                        } else {
                            Button(
                                onClick = { viewModel.downloadAndInstallAppUpdate() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KliprPrimary)
                            ) {
                                Text(
                                    text = "Atualizar Agora (${release.tagName})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sugestão inteligente de Clipboard
        AnimatedVisibility(visible = !uiState.clipboardSuggestionUrl.isNullOrBlank()) {
            val suggested = uiState.clipboardSuggestionUrl.orEmpty()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !uiState.isInspecting) {
                        viewModel.acceptClipboardSuggestion()
                    },
                shape = RoundedCornerShape(12.dp),
                color = KliprSecondary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, KliprSecondary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔗 Link copiado detectado — Toque para analisar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = KliprSecondary
                        )
                        Text(
                            text = suggested,
                            fontSize = 11.sp,
                            color = KliprTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { viewModel.dismissClipboardSuggestion() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Dispensar sugestão",
                            tint = KliprTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Campo de entrada de Link
        OutlinedTextField(
            value = uiState.urlText,
            onValueChange = { viewModel.onUrlChanged(it) },
            placeholder = { Text("Cole o link do vídeo aqui...", color = KliprTextSecondary, fontSize = 14.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = KliprSurface,
                unfocusedContainerColor = KliprSurface,
                focusedBorderColor = KliprPrimary,
                unfocusedBorderColor = KliprBorder,
                focusedTextColor = KliprTextPrimary,
                unfocusedTextColor = KliprTextPrimary
            ),
            trailingIcon = {
                if (uiState.urlText.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearUrl() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpar link",
                            tint = KliprTextSecondary
                        )
                    }
                } else {
                    IconButton(
                        onClick = { pasteAndInspect() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_content_paste),
                            contentDescription = "Colar",
                            tint = KliprAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Botão 1-Toque: "Colar e Analisar Link" / "Analisar Link"
        Button(
            onClick = {
                if (uiState.urlText.isBlank()) {
                    pasteAndInspect()
                } else {
                    viewModel.inspectUrl(uiState.urlText)
                }
            },
            enabled = !uiState.isInspecting,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.urlText.isBlank()) KliprPrimary.copy(alpha = 0.2f) else KliprCard,
                contentColor = KliprTextPrimary
            ),
            border = BorderStroke(
                1.dp,
                if (uiState.urlText.isBlank()) KliprPrimary.copy(alpha = 0.6f) else KliprBorder
            )
        ) {
            if (uiState.isInspecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = KliprPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mapeando qualidades...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else if (uiState.urlText.isBlank()) {
                Icon(
                    painter = painterResource(R.drawable.ic_content_paste),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = KliprAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Colar e Analisar Link", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analisar Link", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card de Mídia Identificada e Configurações
        uiState.videoInfo?.let { info ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, KliprBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = KliprCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    MediaInfoHeader(info = info)

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = KliprBorder.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(12.dp))

                    FormatSelectorSection(
                        info = info,
                        isAudioMode = uiState.isAudioMode,
                        selectedFormat = uiState.selectedFormat,
                        enabled = true,
                        videoLabelForHeight = viewModel::videoLabelForHeight,
                        onModeChange = viewModel::selectMediaMode,
                        onSelectFormat = viewModel::selectFormat
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // RECURSO DE CORTE DE TEMPO
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.toggleTrimming(!uiState.isTrimmingEnabled) },
                        shape = RoundedCornerShape(12.dp),
                        color = KliprSurface,
                        border = BorderStroke(
                            1.dp,
                            if (uiState.isTrimmingEnabled) KliprPrimary.copy(alpha = 0.6f) else KliprBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_cut),
                                    contentDescription = null,
                                    tint = KliprAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Cortar Trecho",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KliprTextPrimary
                                )
                                if (uiState.isTrimmingEnabled) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${uiState.trimStartTime} ➔ ${uiState.trimEndTime}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KliprSecondary,
                                        modifier = Modifier
                                            .background(KliprSecondary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.isTrimmingEnabled,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KliprPrimary
                                )
                            )
                        }
                    }

                    AnimatedVisibility(visible = uiState.isTrimmingEnabled) {
                        TrimControlsPanel(
                            durationSeconds = info.durationSeconds,
                            trimStartSeconds = uiState.trimStartSeconds,
                            trimEndSeconds = uiState.trimEndSeconds,
                            trimStartTime = uiState.trimStartTime,
                            trimEndTime = uiState.trimEndTime,
                            accurateCut = uiState.accurateCut,
                            isAudioMode = uiState.isAudioMode,
                            chapters = info.chapters,
                            enabled = true,
                            onRangeChange = viewModel::updateTrimRangeSeconds,
                            onNudgeStart = viewModel::nudgeTrimStart,
                            onNudgeEnd = viewModel::nudgeTrimEnd,
                            onStartTextChange = viewModel::onTrimStartTextChanged,
                            onEndTextChange = viewModel::onTrimEndTextChanged,
                            onSelectChapter = viewModel::selectChapter,
                            onSelectQuickPreset = viewModel::selectQuickTrimPreset,
                            onAccurateCutChange = viewModel::toggleAccurateCut
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // SEÇÃO "SALVAR COMO"
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.toggleSaveAs() },
                        shape = RoundedCornerShape(12.dp),
                        color = KliprSurface,
                        border = BorderStroke(1.dp, KliprBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = KliprAccent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Salvar Como (${uiState.targetSubfolder})",
                                    fontSize = 13.sp,
                                    color = KliprTextPrimary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                painter = painterResource(
                                    if (uiState.showSaveAs) R.drawable.ic_expand_less else R.drawable.ic_expand_more
                                ),
                                contentDescription = null,
                                tint = KliprTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = uiState.showSaveAs) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .background(KliprSurface, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "Nome do Arquivo:", fontSize = 11.sp, color = KliprTextSecondary)
                            OutlinedTextField(
                                value = uiState.customFileName,
                                onValueChange = viewModel::onCustomFileNameChanged,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                trailingIcon = {
                                    Text(
                                        ".${uiState.selectedFormat.ext}",
                                        fontSize = 12.sp,
                                        color = KliprTextSecondary,
                                        modifier = Modifier.padding(end = 10.dp)
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.background,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                                )
                            )

                            Text(text = "Subpasta Rápida:", fontSize = 11.sp, color = KliprTextSecondary)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                uiState.savedSubfolders.forEach { folder ->
                                    val selected = uiState.targetSubfolder.equals(folder, ignoreCase = true)
                                    Surface(
                                        modifier = Modifier
                                            .heightIn(min = 40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { viewModel.onTargetSubfolderChanged(folder) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) KliprPrimary.copy(alpha = 0.24f) else KliprCard,
                                        border = BorderStroke(1.dp, if (selected) KliprPrimary else KliprBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_folder),
                                                contentDescription = null,
                                                tint = if (selected) KliprAccent else KliprTextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = folder,
                                                fontSize = 12.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) KliprTextPrimary else KliprTextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = uiState.targetSubfolder,
                                onValueChange = viewModel::onTargetSubfolderChanged,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.background,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Botão Principal (suporta enfileirar se outro já estiver rodando!)
                    Button(
                        onClick = { triggerDownloadWithPermissionCheck() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
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
                                    text = when {
                                        dlState.isDownloading -> "Adicionar à Fila 📥"
                                        uiState.isTrimmingEnabled -> "Baixar Trecho Cortado ✂️"
                                        else -> "Baixar Agora"
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    if (dlState.isDownloading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.cancelDownload() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, KliprError),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = KliprError.copy(alpha = 0.12f),
                                contentColor = KliprError
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_stop),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (dlState.queuedCount > 0) {
                                    "Cancelar Tudo (${dlState.queuedCount} na fila)"
                                } else {
                                    "Cancelar Download"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Barra de Progresso
        if (dlState.isDownloading) {
            Spacer(modifier = Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { dlState.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = KliprSecondary,
                trackColor = KliprSurface
            )
        }

        val activeStatus = dlState.statusMessage.ifBlank { uiState.inspectStatusMessage }
        val activeError = if (dlState.statusMessage.isNotBlank()) dlState.isError else uiState.inspectIsError
        if (activeStatus.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            DownloadStatusAndActionsCard(
                context = context,
                statusMessage = activeStatus,
                isError = activeError,
                completedFile = dlState.completedFile,
                onOpenDownloads = onOpenDownloads
            )
        }

        // THUMB ZONE: Histórico de Downloads Recentes ou Guia Rápido
        if (uiState.videoInfo == null && !uiState.isInspecting) {
            Spacer(modifier = Modifier.height(18.dp))
            if (uiState.recentDownloads.isNotEmpty()) {
                RecentDownloadsSection(
                    context = context,
                    items = uiState.recentDownloads,
                    onRemove = viewModel::removeFromHistory
                )
            } else {
                QuickShareOnboardingCard()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (uiState.showSettingsSheet) {
        SettingsAndThemesSheet(
            uiState = uiState,
            onDismiss = { viewModel.setShowSettingsSheet(false) },
            onSelectTheme = viewModel::selectTheme,
            onToggleAria2c = viewModel::setAria2cEnabled,
            onToggleSponsorBlock = viewModel::setSponsorBlockEnabled,
            onToggleNightlyYtDlp = viewModel::setNightlyYtDlpEnabled,
            onUpdateYtDlpNow = viewModel::updateYtDlpNow,
            onCheckAppUpdateNow = { viewModel.checkAppUpdate(manual = true) },
            onInstallAppUpdate = viewModel::downloadAndInstallAppUpdate
        )
    }
}

@Composable
private fun RecentDownloadsSection(
    context: Context,
    items: List<DownloadHistoryItem>,
    onRemove: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "📥 Downloads Recentes",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = KliprTextSecondary,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        items.forEach { item ->
            val file = remember(item.filePath) { File(item.filePath) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { MainActivity.openMediaFile(context, file) },
                shape = RoundedCornerShape(14.dp),
                color = KliprCard,
                border = BorderStroke(1.dp, KliprBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (item.isAudio) KliprSecondary.copy(alpha = 0.18f) else KliprPrimary.copy(alpha = 0.18f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(if (item.isAudio) R.drawable.ic_music else R.drawable.ic_video),
                                contentDescription = null,
                                tint = if (item.isAudio) KliprSecondary else KliprAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KliprTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.formatLabel,
                            fontSize = 11.sp,
                            color = KliprTextSecondary,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = { MainActivity.openMediaFile(context, file) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Abrir",
                            tint = KliprSecondary
                        )
                    }

                    IconButton(
                        onClick = { MainActivity.shareMediaFile(context, file) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartilhar",
                            tint = KliprAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { onRemove(item.filePath) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remover da lista",
                            tint = KliprTextSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickShareOnboardingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = KliprSurface,
        border = BorderStroke(1.dp, KliprBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "⚡ Baixe sem sair do YouTube, TikTok ou Instagram",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = KliprTextPrimary
            )
            Text(
                text = "1. Toque em Compartilhar no vídeo que está assistindo.\n" +
                    "2. Escolha \"Klipr Rápido ⚡\".\n" +
                    "3. Escolha MP4 ou MP3 em 1 toque — e se baixar vários seguidos, o Klipr coloca tudo na fila!",
                fontSize = 12.sp,
                color = KliprTextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}
