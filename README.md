# Klipr Pocket ⚡ (Android Mobile)

Aplicativo nativo Android ultraleve, construído em **Kotlin + Jetpack Compose**, projetado para download instantâneo de vídeos e extração de áudio com 1 toque.

---

## 📱 Destaques do Aplicativo
1. **Bottom Sheet Rápido no Menu Compartilhar (Zero Fricção):**
   * No YouTube, TikTok, Instagram ou X (Twitter), clique em **Compartilhar** ➔ escolha **Klipr Rápido ⚡**. Um painel flutuante sobe por cima do aplicativo de origem para você escolher o formato e iniciar o download em 2º plano em menos de 2 segundos.
2. **Download em Segundo Plano + Notificações Interativas:**
   * `ForegroundService` dedicado com barra de progresso em tempo real, botão **Cancelar** e atalhos **Abrir** / **Compartilhar** direto na notificação ao concluir.
3. **Seletor 1-Toque com Estimativa de Tamanho:**
   * Alternância direta entre **Vídeo (MP4)** e **Áudio** (`MP3`, `M4A`, `FLAC`, `WAV`) usando chips largos otimizados para o polegar e estimativa aproximada em MB/GB.
4. **Motor Nativo Turbo & Compatível:**
   * Utiliza `youtubedl-android` (`yt-dlp` + `FFmpeg` ARM64) com fragmentos concorrentes (`--concurrent-fragments 4`), priorização de codec **H.264 + AAC** para compatibilidade total no WhatsApp/Instagram e indexação instantânea na Galeria (`MediaScannerConnection` + `FileProvider`).
5. **Corte Visual Inteligente (`RangeSlider` + Capítulos):**
   * Recorte trechos arrastando marcadores visuais, usando botões de ajuste fino (`-5s`, `-1s`, `+1s`, `+5s`) ou tocando diretamente nos capítulos detectados do vídeo, com modo **Corte Instantâneo (Stream Copy)** ou **Frame Exato**.
6. **Histórico Recente & Pastas Rápidas:**
   * Salva em `Downloads/Klipr` (ou subpastas rápidas como `Músicas`, `Cortes`, `Status`) e mantém os downloads recentes na zona do polegar para abrir ou compartilhar com 1 toque.

---

## 🛠️ Estrutura do Projeto

```text
Klipr-Mobile/
├── app/
│   ├── build.gradle.kts                     # Compose BOM, ABI Splits, ProGuard/R8 e youtubedl-android
│   └── src/main/
│       ├── AndroidManifest.xml              # Permissões, ForegroundService, FileProvider e ShareSheetActivity
│       ├── java/com/klipr/mobile/
│       │   ├── KliprApp.kt                  # Boot assíncrono seguro do yt-dlp e FFmpeg
│       │   ├── MainActivity.kt              # Tela principal em Jetpack Compose + Histórico + Clipboard
│       │   ├── ShareSheetActivity.kt        # ModalBottomSheet translúcido para compartilhamento rápido
│       │   ├── data/
│       │   │   ├── DownloaderEngine.kt      # Motor yt-dlp/FFmpeg, cache LRU, tamanhos MB e capítulos
│       │   │   └── KliprPreferences.kt      # Persistência leve de histórico recente e subpastas
│       │   ├── service/
│       │   │   └── DownloadForegroundService.kt # Serviço em 2º plano com progresso e notificações
│       │   └── ui/
│       │       ├── KliprViewModel.kt        # Estado reativo, RangeSlider, steppers e histórico
│       │       ├── Components.kt            # Chips de formato, controles de corte e ações pós-download
│       │       └── theme/
│       │           └── Theme.kt             # Paleta Dark Aurora do Klipr
│       └── res/                             # Vector drawables leves, file_paths e temas
├── .github/workflows/
│   └── build-apk.yml                        # CI para compilar APKs debug (por ABI e universal)
├── build.gradle.kts                         # Configuração raiz Gradle
├── settings.gradle.kts                      # Repositórios Maven e inclusão de módulos
└── README.md
```

---

## 🚀 Como Gerar o Arquivo `.apk`

Você tem **duas formas simples** de compilar:

### Método A: Compilação 1-Clique na Nuvem (Sem instalar nada no PC!)
1. Crie um repositório no seu GitHub (pode ser privado) e suba o conteúdo desta pasta `Klipr-Mobile`.
2. O arquivo `.github/workflows/build-apk.yml` já está configurado. Ao fazer o push, o GitHub Actions compila os APKs por arquitetura (`arm64-v8a` ultraleve e universal) automaticamente.
3. Acesse a aba **Actions** no seu GitHub ➔ clique na compilação ➔ baixe o artefato **`Klipr-Pocket-Debug`**.
4. Instale o arquivo `.apk` correspondente ao seu aparelho (em celulares modernos use `app-arm64-v8a-debug.apk`).

### Método B: Usando o Android Studio Local
1. Baixe e instale o [Android Studio](https://developer.android.com/studio).
2. Selecione **Open Project** e aponte para esta pasta:
   `c:\Users\allan\OneDrive\Documentos\Programação\Klipr-Mobile`.
3. Aguarde o Gradle sincronizar as dependências.
4. Vá no menu **Build** ➔ **Build Bundle(s) / APK(s)** ➔ **Build APK(s)**.
5. Os APKs serão gerados em `app/build/outputs/apk/debug/`.
