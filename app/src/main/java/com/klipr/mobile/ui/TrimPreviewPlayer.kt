package com.klipr.mobile.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.klipr.mobile.R
import com.klipr.mobile.data.DownloaderEngine
import com.klipr.mobile.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun TrimPreviewPlayerSection(
    streamUrl: String,
    headers: Map<String, String>,
    trimStartSeconds: Long,
    trimEndSeconds: Long
) {
    if (streamUrl.isBlank()) return

    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(10.dp),
        color = KliprPrimary.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, KliprPrimary.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_video),
                    contentDescription = null,
                    tint = KliprAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isExpanded) "Ocultar Prévia do Corte" else "🎬 Ver/Ouvir Prévia do Corte",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = KliprTextPrimary
                )
            }
            Icon(
                painter = painterResource(if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = null,
                tint = KliprAccent,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    AnimatedVisibility(visible = isExpanded) {
        EmbeddedTrimExoPlayer(
            streamUrl = streamUrl,
            headers = headers,
            trimStartSeconds = trimStartSeconds,
            trimEndSeconds = trimEndSeconds
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun EmbeddedTrimExoPlayer(
    streamUrl: String,
    headers: Map<String, String>,
    trimStartSeconds: Long,
    trimEndSeconds: Long
) {
    val context = LocalContext.current

    val player = remember(streamUrl) {
        val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
            if (headers.isNotEmpty()) {
                setDefaultRequestProperties(headers)
            }
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(streamUrl))
                prepare()
                playWhenReady = false
                if (trimStartSeconds > 0L) {
                    seekTo(trimStartSeconds * 1000L)
                }
            }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(trimStartSeconds, player) {
        val targetMs = trimStartSeconds * 1000L
        if (abs(player.currentPosition - targetMs) > 900L) {
            player.seekTo(targetMs)
        }
    }

    LaunchedEffect(player, trimStartSeconds, trimEndSeconds) {
        while (true) {
            if (player.isPlaying && trimEndSeconds > trimStartSeconds) {
                val endMs = trimEndSeconds * 1000L
                if (player.currentPosition >= endMs) {
                    player.pause()
                    player.seekTo(trimStartSeconds * 1000L)
                }
            }
            delay(250L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(185.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    player.seekTo(trimStartSeconds * 1000L)
                    player.play()
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                border = BorderStroke(1.dp, KliprPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KliprAccent)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Início (${DownloaderEngine.formatDuration(trimStartSeconds)})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            OutlinedButton(
                onClick = {
                    val previewEndStart = (trimEndSeconds - 3L).coerceAtLeast(trimStartSeconds)
                    player.seekTo(previewEndStart * 1000L)
                    player.play()
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                border = BorderStroke(1.dp, KliprSecondary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KliprSecondary)
            ) {
                Text(
                    text = "🎯 Testar Fim (-3s)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}
