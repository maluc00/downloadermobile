package com.klipr.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klipr.mobile.BuildConfig
import com.klipr.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAndThemesSheet(
    uiState: KliprUiState,
    onDismiss: () -> Unit,
    onSelectTheme: (AppThemeMode) -> Unit,
    onToggleAria2c: (Boolean) -> Unit,
    onToggleSponsorBlock: (Boolean) -> Unit,
    onToggleNightlyYtDlp: (Boolean) -> Unit,
    onUpdateYtDlpNow: () -> Unit,
    onCheckAppUpdateNow: () -> Unit,
    onInstallAppUpdate: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎨 Temas & Motores",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = KliprTextPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Fechar",
                        tint = KliprTextSecondary
                    )
                }
            }

            // Seletor dos 5 Temas
            Text(
                text = "Aparência do Aplicativo",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = KliprTextSecondary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppThemeMode.entries.forEach { mode ->
                    val selected = uiState.currentTheme == mode
                    Surface(
                        modifier = Modifier
                            .width(146.dp)
                            .heightIn(min = 86.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectTheme(mode) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) KliprPrimary.copy(alpha = 0.18f) else KliprSurface,
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) KliprPrimary else KliprBorder
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    mode.previewColors.forEach { color ->
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(1.dp, KliprBorder, CircleShape)
                                        )
                                    }
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = KliprPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = mode.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = KliprTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = mode.subtitle,
                                fontSize = 10.sp,
                                color = KliprTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = KliprBorder.copy(alpha = 0.6f))

            // Motores & Recursos Pro
            Text(
                text = "Motores de Extração & Velocidade",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = KliprTextSecondary
            )

            SettingSwitchCard(
                title = "🚀 Motor Aria2c (16 Conexões)",
                subtitle = "Aceleração C++ multi-conexão para downloads completos",
                checked = uiState.useAria2c,
                onCheckedChange = onToggleAria2c
            )

            SettingSwitchCard(
                title = "🛡️ SponsorBlock (YouTube)",
                subtitle = "Remove automaticamente segmentos de patrocínio",
                checked = uiState.sponsorBlockEnabled,
                onCheckedChange = onToggleSponsorBlock
            )

            SettingSwitchCard(
                title = "🌙 Canal Nightly do yt-dlp",
                subtitle = "Recebe correções diárias contra bloqueios (24h)",
                checked = uiState.useNightlyYtDlp,
                onCheckedChange = onToggleNightlyYtDlp
            )

            // Atualização do Motor yt-dlp
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = KliprSurface,
                border = BorderStroke(1.dp, KliprBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Motor Nativo yt-dlp",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = KliprTextPrimary
                            )
                            Text(
                                text = "Versão instalada: ${uiState.ytDlpVersion}",
                                fontSize = 11.sp,
                                color = KliprTextSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = onUpdateYtDlpNow,
                            enabled = !uiState.isUpdatingYtDlp,
                            modifier = Modifier.heightIn(min = 42.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, KliprSecondary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KliprSecondary)
                        ) {
                            if (uiState.isUpdatingYtDlp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = KliprSecondary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Atualizar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (uiState.ytDlpUpdateStatus.isNotBlank()) {
                        Text(
                            text = uiState.ytDlpUpdateStatus,
                            fontSize = 11.sp,
                            color = KliprSecondary
                        )
                    }
                }
            }

            // Atualização do Aplicativo (GitHub Releases)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = KliprSurface,
                border = BorderStroke(1.dp, KliprBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Klipr Pocket v${BuildConfig.VERSION_NAME}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = KliprTextPrimary
                            )
                            Text(
                                text = "Atualização automática via GitHub Releases",
                                fontSize = 11.sp,
                                color = KliprTextSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = onCheckAppUpdateNow,
                            enabled = !uiState.isCheckingAppUpdate && !uiState.isDownloadingAppUpdate,
                            modifier = Modifier.heightIn(min = 42.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, KliprPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KliprPrimary)
                        ) {
                            if (uiState.isCheckingAppUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = KliprPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Verificar APK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (uiState.appUpdateStatus.isNotBlank()) {
                        Text(
                            text = uiState.appUpdateStatus,
                            fontSize = 11.sp,
                            color = KliprAccent
                        )
                    }

                    if (uiState.availableAppUpdate != null) {
                        Button(
                            onClick = onInstallAppUpdate,
                            enabled = !uiState.isDownloadingAppUpdate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KliprPrimary)
                        ) {
                            Text(
                                text = if (uiState.isDownloadingAppUpdate) {
                                    "Baixando APK (${uiState.appUpdateProgress}%)..."
                                } else {
                                    "🚀 Baixar e Instalar ${uiState.availableAppUpdate.tagName}"
                                },
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
}

@Composable
private fun SettingSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = KliprSurface,
        border = BorderStroke(1.dp, if (checked) KliprPrimary.copy(alpha = 0.5f) else KliprBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KliprTextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = KliprTextSecondary
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = KliprPrimary
                )
            )
        }
    }
}
