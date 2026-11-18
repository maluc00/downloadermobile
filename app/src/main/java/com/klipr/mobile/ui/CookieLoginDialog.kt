package com.klipr.mobile.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.klipr.mobile.data.CookieAuthManager
import com.klipr.mobile.data.DownloaderEngine
import com.klipr.mobile.ui.theme.*

private enum class AuthTab(val label: String, val url: String) {
    INSTAGRAM("Instagram", "https://www.instagram.com/accounts/login/"),
    YOUTUBE("YouTube", "https://accounts.google.com/ServiceLogin?service=youtube"),
    X_TWITTER("X / Twitter", "https://x.com/i/flow/login"),
    TIKTOK("TikTok", "https://www.tiktok.com/login"),
    PASTE_RAW("Colar cookies.txt", "")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CookieLoginDialog(
    onDismiss: () -> Unit,
    onCookiesSaved: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(AuthTab.INSTAGRAM) }
    var rawCookiesText by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(20.dp),
            color = KliprCard,
            border = BorderStroke(1.dp, KliprBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔐 Cookies & Login",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = KliprTextPrimary
                        )
                        Text(
                            text = "Para vídeos privados, restritos ou +18",
                            fontSize = 11.sp,
                            color = KliprTextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Fechar",
                            tint = KliprTextSecondary
                        )
                    }
                }

                // Abas rápidas de rede social
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AuthTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { activeTab = tab },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) KliprPrimary.copy(alpha = 0.25f) else KliprSurface,
                            border = BorderStroke(1.dp, if (selected) KliprPrimary else KliprBorder)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = tab.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) KliprTextPrimary else KliprTextSecondary
                                )
                            }
                        }
                    }
                }

                if (activeTab == AuthTab.PASTE_RAW) {
                    Text(
                        text = "Cole abaixo o conteúdo de um arquivo cookies.txt (formato Netscape):",
                        fontSize = 12.sp,
                        color = KliprTextSecondary
                    )
                    OutlinedTextField(
                        value = rawCookiesText,
                        onValueChange = { rawCookiesText = it },
                        placeholder = { Text("# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/...", fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = KliprSurface,
                            unfocusedContainerColor = KliprSurface,
                            focusedTextColor = KliprTextPrimary,
                            unfocusedTextColor = KliprTextPrimary
                        )
                    )
                    Button(
                        onClick = {
                            if (CookieAuthManager.saveRawNetscapeCookies(context, rawCookiesText)) {
                                DownloaderEngine.clearCache()
                                Toast.makeText(context, "Cookies salvos com sucesso!", Toast.LENGTH_SHORT).show()
                                onCookiesSaved()
                            } else {
                                Toast.makeText(context, "Conteúdo de cookies inválido", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KliprPrimary)
                    ) {
                        Text("Salvar cookies.txt", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KliprBorder)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    val cm = CookieManager.getInstance()
                                    cm.setAcceptCookie(true)
                                    cm.setAcceptThirdPartyCookies(this, true)
                                    webViewClient = WebViewClient()
                                    loadUrl(activeTab.url)
                                    webViewRef = this
                                }
                            },
                            update = { webView ->
                                if (webView.tag != activeTab.url) {
                                    webView.tag = activeTab.url
                                    webView.loadUrl(activeTab.url)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Button(
                        onClick = {
                            val count = CookieAuthManager.extractFromWebViewCookieManager(context)
                            if (count > 0) {
                                DownloaderEngine.clearCache()
                                Toast.makeText(
                                    context,
                                    "✅ $count cookies exportados para o motor!",
                                    Toast.LENGTH_LONG
                                ).show()
                                onCookiesSaved()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Faça login antes de salvar os cookies.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KliprSuccess)
                    ) {
                        Text(
                            text = "✅ Salvar Cookies da Sessão",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
