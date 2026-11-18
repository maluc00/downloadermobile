package com.klipr.mobile.data

import android.content.Context
import android.webkit.CookieManager
import java.io.File

object CookieAuthManager {

    private const val COOKIES_FILE_NAME = "klipr_cookies.txt"

    val DEFAULT_DOMAINS = listOf(
        "youtube.com" to ".youtube.com",
        "www.youtube.com" to ".youtube.com",
        "google.com" to ".google.com",
        "accounts.google.com" to ".google.com",
        "instagram.com" to ".instagram.com",
        "www.instagram.com" to ".instagram.com",
        "tiktok.com" to ".tiktok.com",
        "www.tiktok.com" to ".tiktok.com",
        "x.com" to ".x.com",
        "twitter.com" to ".twitter.com"
    )

    fun getCookiesFile(context: Context): File? {
        val file = File(context.filesDir, COOKIES_FILE_NAME)
        return if (file.exists() && file.isFile && file.length() > 25L) file else null
    }

    fun hasSavedCookies(context: Context): Boolean = getCookiesFile(context) != null

    fun saveRawNetscapeCookies(context: Context, rawText: String): Boolean {
        val cleanLines = rawText.lines()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
        if (cleanLines.isEmpty()) return false

        val content = buildString {
            if (!cleanLines.first().startsWith("# Netscape HTTP Cookie File")) {
                appendLine("# Netscape HTTP Cookie File")
            }
            cleanLines.forEach { appendLine(it) }
        }

        val file = File(context.filesDir, COOKIES_FILE_NAME)
        file.writeText(content)
        return file.length() > 25L
    }

    fun extractFromWebViewCookieManager(
        context: Context,
        domains: List<Pair<String, String>> = DEFAULT_DOMAINS
    ): Int {
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()

        val seen = LinkedHashMap<Pair<String, String>, String>()
        for ((queryHost, netscapeDomain) in domains) {
            val rawCookie = cookieManager.getCookie("https://$queryHost") ?: continue
            rawCookie.split(";").forEach { chunk ->
                val trimmed = chunk.trim()
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx > 0) {
                    val name = trimmed.substring(0, eqIdx).trim()
                    val value = trimmed.substring(eqIdx + 1).trim()
                    if (name.isNotBlank()) {
                        seen[netscapeDomain to name] = value
                    }
                }
            }
        }

        if (seen.isEmpty()) return 0

        val out = buildString {
            appendLine("# Netscape HTTP Cookie File")
            appendLine("# Exported by Klipr Pocket")
            seen.forEach { (domainAndName, value) ->
                val (domain, name) = domainAndName
                appendLine("$domain\tTRUE\t/\tTRUE\t2147483647\t$name\t$value")
            }
        }

        val file = File(context.filesDir, COOKIES_FILE_NAME)
        file.writeText(out)
        return seen.size
    }

    fun clearCookies(context: Context) {
        val file = File(context.filesDir, COOKIES_FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        } catch (e: Exception) {
            // Ignora falha se WebView ainda não inicializou
        }
    }
}
