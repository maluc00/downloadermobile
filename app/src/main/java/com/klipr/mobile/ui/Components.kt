package com.klipr.mobile.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.klipr.mobile.MainActivity
import com.klipr.mobile.R
import com.klipr.mobile.data.DownloaderEngine
import com.klipr.mobile.data.SelectedFormat
import com.klipr.mobile.data.VideoChapter
import com.klipr.mobile.data.VideoInfo
import com.klipr.mobile.ui.theme.*
import java.io.File

@Composable
fun MediaInfoHeader(
    info: VideoInfo,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (info.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = info.thumbnailUrl,
                contentDescription = "Capa",
                modifier = Modifier
                    .size(width = 92.dp, height = 60.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = KliprTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${info.uploader} • ⏱ ${info.durationText}",
                fontSize = 12.sp,
                color = KliprTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FormatSelectorSection(
    info: VideoInfo,
    isAudioMode: Boolean,
    selectedFormat: SelectedFormat,
    enabled: Boolean,
    videoLabelForHeight: (Int) -> String,
    onModeChange: (Boolean) -> Unit,
    onSelectFormat: (SelectedFormat) -> Unit
) {
    // Segmented Toggle (Vídeo vs Áudio)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(KliprSurface, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val videoSelected = !isAudioMode
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(9.dp))
                .clickable(enabled = enabled) { onModeChange(false) },
            shape = RoundedCornerShape(9.dp),
            color = if (videoSelected) KliprPrimary else Color.Transparent
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_video),
                    contentDescription = null,
                    tint = if (videoSelected) Color.White else KliprTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Vídeo (MP4)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (videoSelected) Color.White else KliprTextSecondary
                )
            }
        }

        val audioSelected = isAudioMode
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(9.dp))
                .clickable(enabled = enabled) { onModeChange(true) },
            shape = RoundedCornerShape(9.dp),
            color = if (audioSelected) KliprSecondary else Color.Transparent
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_music),
                    contentDescription = null,
                    tint = if (audioSelected) Color.White else KliprTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Áudio",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (audioSelected) Color.White else KliprTextSecondary
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (!isAudioMode) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            info.availableHeights.chunked(2).forEach { rowHeights ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowHeights.forEach { h ->
                        val isSelected = !selectedFormat.isAudio && selectedFormat.height == h
                        val label = videoLabelForHeight(h)
                        val sizeLabel = DownloaderEngine.formatSizeMb(info.formatSizesMb[h])
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = enabled) {
                                    onSelectFormat(
                                        SelectedFormat(
                                            isAudio = false,
                                            height = h,
                                            ext = "mp4",
                                            label = label
                                        )
                                    )
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) KliprPrimary.copy(alpha = 0.22f) else KliprSurface,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) KliprPrimary else KliprBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) KliprTextPrimary else KliprTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (sizeLabel.isNotBlank()) {
                                    Text(
                                        text = sizeLabel,
                                        fontSize = 10.sp,
                                        color = if (isSelected) KliprAccent else KliprTextSecondary
                                    )
                                }
                            }
                        }
                    }
                    if (rowHeights.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    } else {
        val audioSizeLabel = DownloaderEngine.formatSizeMb(info.audioSizeMb)
        val audioOptions = listOf(
            Triple("mp3", "MP3 (320kbps)", if (audioSizeLabel.isNotBlank()) "Alta Fidelidade • $audioSizeLabel" else "Alta Fidelidade"),
            Triple("m4a", "M4A (AAC ⚡)", if (audioSizeLabel.isNotBlank()) "Nativo • $audioSizeLabel" else "Leve & Nativo"),
            Triple("flac", "FLAC", "Lossless Estúdio"),
            Triple("wav", "WAV", "PCM Sem Compressão")
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            audioOptions.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowOptions.forEach { (ext, title, subtitle) ->
                        val isSelected = selectedFormat.isAudio && selectedFormat.ext == ext
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = enabled) {
                                    onSelectFormat(
                                        SelectedFormat(
                                            isAudio = true,
                                            ext = ext,
                                            label = title
                                        )
                                    )
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) KliprSecondary.copy(alpha = 0.22f) else KliprSurface,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) KliprSecondary else KliprBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) KliprTextPrimary else KliprTextSecondary,
                                    maxLines = 1
                                )
                                Text(
                                    text = subtitle,
                                    fontSize = 10.sp,
                                    color = if (isSelected) KliprSecondary else KliprTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimControlsPanel(
    durationSeconds: Long,
    trimStartSeconds: Long,
    trimEndSeconds: Long,
    trimStartTime: String,
    trimEndTime: String,
    accurateCut: Boolean,
    isAudioMode: Boolean,
    chapters: List<VideoChapter>,
    enabled: Boolean,
    previewStreamUrl: String = "",
    previewHeaders: Map<String, String> = emptyMap(),
    onRangeChange: (Long, Long) -> Unit,
    onNudgeStart: (Long) -> Unit,
    onNudgeEnd: (Long) -> Unit,
    onStartTextChange: (String) -> Unit,
    onEndTextChange: (String) -> Unit,
    onSelectChapter: (VideoChapter) -> Unit,
    onSelectQuickPreset: (Long) -> Unit,
    onAccurateCutChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .background(KliprSurface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val selectedDuration = (trimEndSeconds - trimStartSeconds).coerceAtLeast(0L)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Arraste os marcadores:",
                fontSize = 11.sp,
                color = KliprTextSecondary,
                fontWeight = FontWeight.Medium
            )
            Surface(
                color = KliprSecondary.copy(alpha = 0.16f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Trecho: ${DownloaderEngine.formatDuration(selectedDuration)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = KliprSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        if (durationSeconds > 1L) {
            val maxFloat = durationSeconds.toFloat()
            val safeStart = trimStartSeconds.toFloat().coerceIn(0f, (maxFloat - 1f).coerceAtLeast(0f))
            val safeEnd = trimEndSeconds.toFloat().coerceIn(safeStart + 1f, maxFloat)

            RangeSlider(
                value = safeStart..safeEnd,
                onValueChange = { range ->
                    if (enabled) {
                        onRangeChange(range.start.toLong(), range.endInclusive.toLong())
                    }
                },
                enabled = enabled,
                valueRange = 0f..maxFloat,
                colors = SliderDefaults.colors(
                    thumbColor = KliprPrimary,
                    activeTrackColor = KliprSecondary,
                    inactiveTrackColor = KliprBorder
                )
            )
        }

        // Mini-Player de Prévia do Corte (Media3 ExoPlayer)
        if (previewStreamUrl.isNotBlank()) {
            TrimPreviewPlayerSection(
                streamUrl = previewStreamUrl,
                headers = previewHeaders,
                trimStartSeconds = trimStartSeconds,
                trimEndSeconds = trimEndSeconds
            )
        }

        // Campos de tempo + Steppers de ajuste fino (-5s, -1s, +1s, +5s)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimeAdjustColumn(
                label = "Início",
                value = trimStartTime,
                enabled = enabled,
                onValueChange = onStartTextChange,
                onNudge = onNudgeStart,
                modifier = Modifier.weight(1f)
            )
            TimeAdjustColumn(
                label = "Fim",
                value = trimEndTime,
                enabled = enabled,
                onValueChange = onEndTextChange,
                onNudge = onNudgeEnd,
                modifier = Modifier.weight(1f)
            )
        }

        // Capítulos do Vídeo ou Atalhos Rápidos
        if (chapters.isNotEmpty()) {
            Text(
                text = "📑 Capítulos do Vídeo:",
                fontSize = 11.sp,
                color = KliprTextSecondary,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chapters.forEach { chapter ->
                    val active = trimStartSeconds == chapter.startSeconds && trimEndSeconds == chapter.endSeconds
                    Surface(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = enabled) { onSelectChapter(chapter) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) KliprPrimary.copy(alpha = 0.25f) else KliprCard,
                        border = BorderStroke(1.dp, if (active) KliprPrimary else KliprBorder)
                    ) {
                        Text(
                            text = "${chapter.startTime} ${chapter.title}",
                            fontSize = 11.sp,
                            color = if (active) KliprTextPrimary else KliprTextSecondary,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        } else if (durationSeconds > 35L) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(15L to "Primeiros 15s", 30L to "Primeiros 30s", 60L to "Primeiros 60s")
                    .filter { it.first < durationSeconds }
                    .forEach { (secs, title) ->
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = enabled) { onSelectQuickPreset(secs) },
                            shape = RoundedCornerShape(8.dp),
                            color = KliprCard,
                            border = BorderStroke(1.dp, KliprBorder)
                        ) {
                            Text(
                                text = "⚡ $title",
                                fontSize = 11.sp,
                                color = KliprTextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
            }
        }

        // Modo de corte (somente vídeo)
        if (!isAudioMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled) { onAccurateCutChange(false) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (!accurateCut) KliprSecondary.copy(alpha = 0.2f) else KliprCard,
                    border = BorderStroke(1.dp, if (!accurateCut) KliprSecondary else KliprBorder)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = "⚡ Instantâneo",
                            fontSize = 11.sp,
                            fontWeight = if (!accurateCut) FontWeight.Bold else FontWeight.Normal,
                            color = if (!accurateCut) KliprTextPrimary else KliprTextSecondary
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled) { onAccurateCutChange(true) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (accurateCut) KliprPrimary.copy(alpha = 0.2f) else KliprCard,
                    border = BorderStroke(1.dp, if (accurateCut) KliprPrimary else KliprBorder)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = "🎯 Frame Exato",
                            fontSize = 11.sp,
                            fontWeight = if (accurateCut) FontWeight.Bold else FontWeight.Normal,
                            color = if (accurateCut) KliprTextPrimary else KliprTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeAdjustColumn(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onNudge: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 11.sp, color = KliprTextSecondary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedTextColor = KliprTextPrimary,
                unfocusedTextColor = KliprTextPrimary
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(-5L to "-5s", -1L to "-1s", 1L to "+1s", 5L to "+5s").forEach { (delta, text) ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled) { onNudge(delta) },
                    shape = RoundedCornerShape(8.dp),
                    color = KliprCard,
                    border = BorderStroke(1.dp, KliprBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KliprTextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadStatusAndActionsCard(
    context: Context,
    statusMessage: String,
    isError: Boolean,
    completedFile: File?,
    canRetry: Boolean = false,
    onRetry: (() -> Unit)? = null,
    onOpenDownloads: () -> Unit
) {
    if (statusMessage.isBlank()) return

    val isSuccess = statusMessage.startsWith("✅")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = when {
            isSuccess -> KliprSuccess.copy(alpha = 0.15f)
            isError -> KliprError.copy(alpha = 0.12f)
            else -> KliprCard
        },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            when {
                isSuccess -> KliprSuccess.copy(alpha = 0.4f)
                isError -> KliprError.copy(alpha = 0.4f)
                else -> KliprBorder
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = statusMessage,
                color = when {
                    isSuccess -> KliprSuccess
                    isError -> KliprError
                    else -> KliprTextSecondary
                },
                fontSize = 13.sp,
                fontWeight = if (isSuccess) FontWeight.SemiBold else FontWeight.Normal
            )
            if (isError && canRetry && onRetry != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KliprPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "🔄 Retomar Download de Onde Parou",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (isSuccess) {
                Spacer(modifier = Modifier.height(12.dp))
                val file = completedFile?.takeIf { it.exists() && it.isFile }
                if (file != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { MainActivity.shareMediaFile(context, file) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KliprSuccess,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Enviar", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { MainActivity.openMediaFile(context, file) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            border = BorderStroke(1.dp, KliprSuccess),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KliprSuccess)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Abrir", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = onOpenDownloads,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            border = BorderStroke(1.dp, KliprSuccess.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KliprSuccess)
                        ) {
                            Icon(painterResource(R.drawable.ic_folder_open), contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pasta", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenDownloads,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, KliprSuccess)
                    ) {
                        Icon(painterResource(R.drawable.ic_folder_open), contentDescription = null, modifier = Modifier.size(16.dp), tint = KliprSuccess)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver na Pasta", fontSize = 13.sp, color = KliprSuccess, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
