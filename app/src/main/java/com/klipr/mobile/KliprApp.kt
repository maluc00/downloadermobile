package com.klipr.mobile

import android.app.Application
import android.content.Context
import android.util.Log
import com.klipr.mobile.data.DownloaderEngine
import com.klipr.mobile.data.KliprPreferences
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KliprApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "KliprApp"
        private val engineReady = CompletableDeferred<Boolean>()

        suspend fun awaitEngineReady(): Boolean = engineReady.await()

        fun getYtDlpVersion(context: Context): String {
            return try {
                YoutubeDL.getInstance().version(context)
                    ?: YoutubeDL.getInstance().versionName(context)
                    ?: "Embutido"
            } catch (e: Exception) {
                "Embutido"
            }
        }

        suspend fun updateYtDlpNow(context: Context, nightly: Boolean): Result<String> =
            withContext(Dispatchers.IO) {
                try {
                    val ready = awaitEngineReady()
                    if (!ready) {
                        return@withContext Result.failure(
                            IllegalStateException("Motor ainda não inicializado.")
                        )
                    }
                    val channel = if (nightly) {
                        YoutubeDL.UpdateChannel.NIGHTLY
                    } else {
                        YoutubeDL.UpdateChannel.STABLE
                    }
                    val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, channel)
                    KliprPreferences.markYtDlpUpdated(context.applicationContext)
                    val ver = getYtDlpVersion(context.applicationContext)
                    val msg = when (status) {
                        YoutubeDL.UpdateStatus.DONE -> "Motor atualizado para $ver!"
                        YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Motor já está na versão mais recente ($ver)."
                        null -> "Verificação concluída ($ver)."
                    }
                    Result.success(msg)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
    }

    override fun onCreate() {
        super.onCreate()
        KliprPreferences.syncFromStorage(this)
        initDownloadEnginesAsync()
    }

    private fun initDownloadEnginesAsync() {
        appScope.launch {
            val initialized = try {
                YoutubeDL.getInstance().init(this@KliprApp)
                FFmpeg.getInstance().init(this@KliprApp)
                try {
                    Aria2c.getInstance().init(this@KliprApp)
                } catch (e: Exception) {
                    Log.w(TAG, "Aviso ao inicializar Aria2c: ${e.message}")
                }
                Log.i(TAG, "Motores Klipr (yt-dlp, FFmpeg e Aria2c) inicializados com sucesso.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Erro crítico ao inicializar motores nativos do Klipr: ${e.message}", e)
                false
            }

            engineReady.complete(initialized)

            if (initialized && KliprPreferences.shouldAutoUpdateYtDlp(this@KliprApp)) {
                delay(4000)
                if (!DownloaderEngine.isBusy()) {
                    val nightly = KliprPreferences.isNightlyYtDlpEnabled(this@KliprApp)
                    updateYtDlpNow(this@KliprApp, nightly).onSuccess { msg ->
                        Log.i(TAG, "Auto-update yt-dlp: $msg")
                    }.onFailure { err ->
                        Log.w(TAG, "Aviso no auto-update yt-dlp: ${err.message}")
                    }
                }
            }
        }
    }
}
