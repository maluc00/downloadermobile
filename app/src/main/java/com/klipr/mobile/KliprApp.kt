package com.klipr.mobile

import android.app.Application
import android.util.Log
import com.klipr.mobile.data.DownloaderEngine
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class KliprApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "KliprApp"
        private val engineReady = CompletableDeferred<Boolean>()

        suspend fun awaitEngineReady(): Boolean = engineReady.await()
    }

    override fun onCreate() {
        super.onCreate()
        initDownloadEnginesAsync()
    }

    private fun initDownloadEnginesAsync() {
        appScope.launch {
            val initialized = try {
                // Inicializa motor base do yt-dlp e FFmpeg fora da Main Thread para boot instantâneo
                YoutubeDL.getInstance().init(this@KliprApp)
                FFmpeg.getInstance().init(this@KliprApp)
                Log.i(TAG, "Motores Klipr (yt-dlp e FFmpeg) inicializados com sucesso.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Erro crítico ao inicializar motores nativos do Klipr: ${e.message}", e)
                false
            }

            engineReady.complete(initialized)

            if (initialized) {
                // Aguarda janela ociosa antes de atualizar o binário yt-dlp, evitando conflito com Share Sheet
                delay(4000)
                if (!DownloaderEngine.isBusy()) {
                    try {
                        val status = YoutubeDL.getInstance().updateYoutubeDL(this@KliprApp)
                        Log.i(TAG, "Status da atualização yt-dlp: $status")
                    } catch (e: Exception) {
                        Log.w(TAG, "Aviso ao verificar atualizações do yt-dlp: ${e.message}")
                    }
                } else {
                    Log.d(TAG, "Atualização do yt-dlp adiada porque há extração/download em andamento.")
                }
            }
        }
    }
}
