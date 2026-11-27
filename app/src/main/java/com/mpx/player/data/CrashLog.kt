package com.mpx.player.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CrashLogInfo(
    /** Quantos registros de falha existem (arquivo atual + rotacionado). */
    val count: Int = 0,
    /** Primeira linha do registro mais recente, ex.: "2026-11-27 10:00:00 [DOWNLOAD] OutOfMemoryError: ...". */
    val last: String? = null,
)

/**
 * Log de falhas **somente local** (nada é enviado para fora do aparelho): grava o stack trace de
 * erros do app em `filesDir/logs/` para o usuário ler/compartilhar em CFG → SOBRE.
 *
 * Correção PREVENTIVA (crash do downloader de playlist, causa raiz NÃO confirmada): sem stack trace
 * do aparelho não deu para provar a causa; este log garante que a próxima ocorrência traga a
 * evidência real.
 *
 * Tamanho limitado: quando o arquivo atual passaria de [maxBytes], ele vira `.1` (o `.1` anterior é
 * descartado), então o total em disco fica em no máximo ~2 × [maxBytes]. Cada registro é cortado em
 * [maxEntryChars] (stderr do yt-dlp pode ser enorme).
 *
 * JVM puro (só java.io) para ser testável sem Android.
 */
class CrashLog(
    private val dir: File,
    /** Cabeçalho de cada registro (versão do app, aparelho, Android). */
    private val header: String = "",
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxEntryChars: Int = DEFAULT_MAX_ENTRY_CHARS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val file: File get() = File(dir, FILE_NAME)
    val rotatedFile: File get() = File(dir, ROTATED_NAME)
    private val exportFile: File get() = File(dir, EXPORT_NAME)

    private val _info = MutableStateFlow(CrashLogInfo())
    val info: StateFlow<CrashLogInfo> = _info.asStateFlow()

    init {
        runCatching { refresh() }
    }

    /**
     * Grava um registro. Nunca lança: é chamado de dentro de handlers de erro (inclusive do
     * handler de exceção não capturada), onde uma falha aqui não pode piorar a situação.
     */
    @Synchronized
    fun record(tag: String, error: Throwable, thread: String? = null) {
        runCatching {
            dir.mkdirs()
            var entry = format(tag, error, clock(), header, thread, maxEntryChars)
            val bytes = entry.toByteArray(Charsets.UTF_8).size
            if (file.exists() && file.length() + bytes > maxBytes) rotate()
            // Arquivo truncado (kill no meio da escrita) ou com lixo sem "\n" no fim: sem isto o
            // título do novo registro grudaria na última linha e não seria contado.
            if (!endsWithNewline(file)) entry = "\n" + entry
            file.appendText(entry, Charsets.UTF_8)
            refresh()
        }
    }

    /** Conteúdo completo, do mais antigo (rotacionado) para o mais recente. */
    @Synchronized
    fun readAll(): String = buildString {
        if (rotatedFile.exists()) append(runCatching { rotatedFile.readText(Charsets.UTF_8) }.getOrDefault(""))
        if (file.exists()) append(runCatching { file.readText(Charsets.UTF_8) }.getOrDefault(""))
    }

    /** Gera um arquivo único (rotacionado + atual) para compartilhar; null se não há registros. */
    @Synchronized
    fun export(): File? {
        val text = readAll()
        if (text.isBlank()) return null
        dir.mkdirs()
        exportFile.writeText(text, Charsets.UTF_8)
        return exportFile
    }

    @Synchronized
    fun clear() {
        runCatching { file.delete() }
        runCatching { rotatedFile.delete() }
        runCatching { exportFile.delete() }
        refresh()
    }

    private fun endsWithNewline(f: File): Boolean {
        if (!f.exists() || f.length() == 0L) return true
        return RandomAccessFile(f, "r").use { raf ->
            raf.seek(raf.length() - 1)
            raf.read() == '\n'.code
        }
    }

    private fun rotate() {
        rotatedFile.delete()
        if (!file.renameTo(rotatedFile)) file.delete()
    }

    private fun refresh() {
        val text = readAll()
        val heads = text.lineSequence().filter { it.startsWith(ENTRY_MARK) }.toList()
        _info.value = CrashLogInfo(
            count = heads.size,
            last = heads.lastOrNull()?.removePrefix(ENTRY_MARK)?.trim(),
        )
    }

    companion object {
        const val FILE_NAME = "mpx-errors.log"
        const val ROTATED_NAME = "mpx-errors.1.log"
        const val EXPORT_NAME = "mpx-errors-export.txt"
        const val ENTRY_MARK = "===== "
        const val TRUNCATED_MARK = "... [TRUNCADO]"
        const val DEFAULT_MAX_BYTES = 128L * 1024
        const val DEFAULT_MAX_ENTRY_CHARS = 16 * 1024

        /** Formata um registro: linha de título + cabeçalho + stack trace completo (com causas). */
        fun format(
            tag: String,
            error: Throwable,
            timeMs: Long,
            header: String = "",
            thread: String? = null,
            maxChars: Int = DEFAULT_MAX_ENTRY_CHARS,
        ): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getDefault() }
            // Defesa em profundidade: QUALQUER registro (FATAL, Downloader, desktop) passa por
            // maskSecrets — URL com ?token=/&t=, Bearer e variantes URL-encoded nunca vão em claro.
            val headline = maskSecrets(error.message ?: "").lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(160).orEmpty()
            val title = buildString {
                append(ENTRY_MARK).append(fmt.format(Date(timeMs))).append(" [").append(tag).append("] ")
                // SanitizedException já traz a classe original no início da mensagem (e no release o
                // nome dela seria minificado, ex. "c:"): não repete o prefixo.
                if (error is SanitizedException && headline.isNotEmpty()) {
                    append(headline)
                } else {
                    append(error.javaClass.simpleName.ifEmpty { error.javaClass.name })
                    if (headline.isNotEmpty()) append(": ").append(headline)
                }
            }
            val stack = StringWriter().also { sw -> PrintWriter(sw).use { error.printStackTrace(it) } }.toString()
            var body = buildString {
                if (header.isNotBlank()) appendLine(header.trim())
                if (thread != null) appendLine("thread: $thread")
                append(stack.trimEnd())
            }
            body = maskSecrets(body)
            if (body.length > maxChars) body = body.take(maxChars) + "\n" + TRUNCATED_MARK
            // Só o título pode começar com ENTRY_MARK: uma mensagem multilinha (o stack trace a repete
            // inteira) com linha "===== ..." seria contada como registro extra. Indenta essas linhas.
            body = body.lineSequence().joinToString("\n") { if (it.startsWith(ENTRY_MARK)) " $it" else it }
            return title + "\n" + body + "\n\n"
        }
    }
}
