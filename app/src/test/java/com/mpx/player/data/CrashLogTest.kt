package com.mpx.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Testes JVM do log de falhas local (correção PREVENTIVA do crash do downloader). */
class CrashLogTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun log(maxBytes: Long = CrashLog.DEFAULT_MAX_BYTES, maxEntry: Int = CrashLog.DEFAULT_MAX_ENTRY_CHARS) =
        CrashLog(File(tmp.root, "logs"), header = "MPX vTEST", maxBytes = maxBytes, maxEntryChars = maxEntry, clock = { 0L })

    @Test fun `format traz tag, classe, mensagem, cabecalho, thread e causa`() {
        val err = IllegalStateException("falhou aqui\nsegunda linha", OutOfMemoryError("sem memoria"))
        val out = CrashLog.format("DOWNLOAD", err, 0L, header = "MPX v1.6.0", thread = "Thread-7")
        val first = out.lineSequence().first()
        assertTrue(first.startsWith(CrashLog.ENTRY_MARK))
        assertTrue(first.contains("[DOWNLOAD] IllegalStateException: falhou aqui"))
        assertFalse("titulo usa so a 1a linha da mensagem", first.contains("segunda linha"))
        assertTrue(out.contains("MPX v1.6.0"))
        assertTrue(out.contains("thread: Thread-7"))
        assertTrue("stack inclui a causa", out.contains("Caused by: java.lang.OutOfMemoryError: sem memoria"))
        assertTrue(out.endsWith("\n\n"))
    }

    @Test fun `format corta registro gigante (stderr do yt-dlp)`() {
        val err = RuntimeException("x".repeat(50_000))
        val out = CrashLog.format("DOWNLOAD", err, 0L, maxChars = 1_000)
        assertTrue(out.contains(CrashLog.TRUNCATED_MARK))
        assertTrue(out.length < 1_400)
    }

    @Test fun `format sem mensagem usa so a classe`() {
        val first = CrashLog.format("FATAL", OutOfMemoryError(), 0L).lineSequence().first()
        assertTrue(first.endsWith("[FATAL] OutOfMemoryError"))
    }

    @Test fun `record grava, conta e expoe o ultimo`() {
        val l = log()
        assertEquals(0, l.info.value.count)
        assertNull(l.info.value.last)
        l.record("DOWNLOAD", RuntimeException("primeiro"))
        l.record("FATAL", OutOfMemoryError("segundo"), "main")
        assertEquals(2, l.info.value.count)
        assertTrue(l.info.value.last!!.contains("[FATAL] OutOfMemoryError: segundo"))
        val all = l.readAll()
        assertTrue(all.indexOf("primeiro") < all.indexOf("segundo"))
    }

    @Test fun `contagem sobrevive a reabrir o log (reinicio do app)`() {
        log().record("FATAL", RuntimeException("antes do crash"))
        val reaberto = log()
        assertEquals(1, reaberto.info.value.count)
        assertTrue(reaberto.info.value.last!!.contains("antes do crash"))
    }

    @Test fun `rotacao limita o tamanho em disco e mantem os mais recentes`() {
        val max = 4_000L
        val l = log(maxBytes = max, maxEntry = 1_500)
        repeat(60) { i -> l.record("DOWNLOAD", RuntimeException("erro-$i")) }
        assertTrue(l.file.length() <= max)
        assertTrue(l.rotatedFile.exists())
        assertTrue(l.rotatedFile.length() <= max)
        val all = l.readAll()
        assertTrue("o mais recente fica", all.contains("erro-59"))
        assertFalse("o mais antigo foi descartado", all.contains("erro-0\n") || all.contains("erro-0:"))
        assertTrue(l.info.value.last!!.contains("erro-59"))
    }

    @Test fun `export junta rotacionado e atual, e null quando vazio`() {
        val l = log(maxBytes = 3_000, maxEntry = 1_000)
        assertNull(l.export())
        repeat(10) { i -> l.record("DOWNLOAD", RuntimeException("e$i")) }
        val f = l.export()
        assertNotNull(f)
        assertEquals(l.readAll(), f!!.readText())
    }

    @Test fun `clear apaga tudo e zera a contagem`() {
        val l = log()
        l.record("DOWNLOAD", RuntimeException("x"))
        l.export()
        l.clear()
        assertEquals(0, l.info.value.count)
        assertFalse(l.file.exists())
        assertFalse(l.rotatedFile.exists())
        assertEquals("", l.readAll())
    }

    @Test fun `linha da mensagem com a marca de registro nao conta como registro extra`() {
        val l = log()
        l.record("A", RuntimeException("x\n===== y"))
        assertEquals(1, l.info.value.count)
        assertTrue(l.readAll().contains("\n ===== y"))
    }

    @Test fun `arquivo sem newline no fim (escrita interrompida) nao engole o proximo registro`() {
        val l = log()
        l.record("A", RuntimeException("primeiro"))
        l.file.appendText("lixo-truncado-sem-newline")
        l.record("B", RuntimeException("segundo"))
        assertEquals(2, CrashLog(l.file.parentFile!!).info.value.count)
        assertTrue(l.info.value.last!!.contains("[B] RuntimeException: segundo"))
    }

    @Test fun `record nunca lanca mesmo com diretorio invalido`() {
        val arquivoNoLugarDoDir = tmp.newFile("nao-e-dir")
        val l = CrashLog(arquivoNoLugarDoDir)
        l.record("FATAL", RuntimeException("x")) // nao pode lancar dentro do uncaught handler
        assertEquals(0, l.info.value.count)
    }
}
