package com.mpx.player.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class UpdateStatus { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, ERROR }

data class UpdateState(
    val status: UpdateStatus = UpdateStatus.IDLE,
    val current: String = "",
    val latest: String = "",
    val notes: String = "",
    val url: String = "",
    val progress: Float = 0f,
    val error: String? = null,
)

/**
 * Verifica a Release mais recente do repositório no GitHub, compara com a versão instalada, baixa o
 * APK e chama o instalador do Android. O update só instala por cima se o APK estiver assinado com a
 * mesma chave (release keystore) do app instalado.
 */
class UpdateRepository(private val context: Context) {

    private val repo = "henckemaier/mpx-player"

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val currentVersion: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()

    suspend fun check() = withContext(Dispatchers.IO) {
        val cur = currentVersion
        _state.value = UpdateState(status = UpdateStatus.CHECKING, current = cur)
        val json = runCatching { httpGetJson("https://api.github.com/repos/$repo/releases/latest") }.getOrNull()
        if (json == null) {
            _state.update {
                it.copy(status = UpdateStatus.ERROR, error = "SEM CONEXÃO OU NENHUMA RELEASE PUBLICADA")
            }
            return@withContext
        }
        val latest = json.optString("tag_name").ifBlank { json.optString("name") }.removePrefix("v").trim()
        val notes = json.optString("body").replace("\r\n", "\n").trim().take(220)
        val url = apkAssetUrl(json)
        if (latest.isBlank() || url.isBlank()) {
            _state.value = UpdateState(status = UpdateStatus.ERROR, current = cur, error = "RELEASE SEM APK")
            return@withContext
        }
        _state.value =
            if (isNewer(latest, cur)) {
                UpdateState(status = UpdateStatus.AVAILABLE, current = cur, latest = latest, notes = notes, url = url)
            } else {
                UpdateState(status = UpdateStatus.UP_TO_DATE, current = cur, latest = latest)
            }
    }

    suspend fun downloadAndInstall() = withContext(Dispatchers.IO) {
        val s = _state.value
        if (s.url.isBlank() || s.status == UpdateStatus.DOWNLOADING) return@withContext
        // Android pede a permissão "instalar apps desconhecidos" por app.
        if (!context.packageManager.canRequestPackageInstalls()) {
            openInstallSettings()
            _state.update { it.copy(error = "LIBERE \"INSTALAR APPS DESCONHECIDOS\" E TOQUE DE NOVO") }
            return@withContext
        }
        _state.update { it.copy(status = UpdateStatus.DOWNLOADING, progress = 0f, error = null) }
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "update").apply { mkdirs() }
        val apk = File(dir, "mpx-latest.apk")
        val ok = runCatching { downloadTo(s.url, apk) }.getOrDefault(false)
        if (!ok || apk.length() == 0L) {
            _state.update { it.copy(status = UpdateStatus.AVAILABLE, error = "FALHA AO BAIXAR O APK") }
            return@withContext
        }
        launchInstall(apk)
        _state.update { it.copy(status = UpdateStatus.AVAILABLE, progress = 1f) }
    }

    fun reset() {
        if (_state.value.status == UpdateStatus.DOWNLOADING) return
        _state.value = UpdateState()
    }

    // ---------------------------------------------------------------- helpers

    private fun apkAssetUrl(json: JSONObject): String {
        val assets = json.optJSONArray("assets") ?: return ""
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                return a.optString("browser_download_url")
            }
        }
        return ""
    }

    /** Compara versões "1.4" / "v1.4.0" numericamente, campo a campo. */
    private fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-', ' ', '_').mapNotNull { seg ->
            seg.takeWhile(Char::isDigit).toIntOrNull()
        }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun httpGetJson(url: String): JSONObject? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("User-Agent", "MPX-Player")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            if (conn.responseCode != 200) null
            else JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadTo(url: String, dest: File): Boolean {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MPX-Player")
        }
        return try {
            if (conn.responseCode !in 200..299) return false
            val total = conn.contentLengthLong
            var read = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            _state.update { it.copy(progress = (read.toFloat() / total).coerceIn(0f, 1f)) }
                        }
                    }
                }
            }
            true
        } finally {
            conn.disconnect()
        }
    }

    private fun openInstallSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun launchInstall(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
