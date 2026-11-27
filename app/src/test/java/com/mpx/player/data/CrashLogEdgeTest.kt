package com.mpx.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CancellationException

/** Casos de borda extras do CrashLog (Sonar). */
class CrashLogEdgeTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun log(max: Long = CrashLog.DEFAULT_MAX_BYTES, dir: File = File(tmp.root, "logs")) =
        CrashLog(dir, header = "H", maxBytes = max, clock = { 0L })

    @Test fun `concorrencia - 8 threads x 50 registros nao perde nem corrompe`() {
        val l = log(max = 10_000_000)
        val pool = Executors.newFixedThreadPool(8)
        val go = CountDownLatch(1)
        repeat(8) { t -> pool.execute { go.await(); repeat(50) { i -> l.record("T$t", RuntimeException("e$i")) } } }
        go.countDown(); pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        assertEquals(400, l.info.value.count)
        assertEquals(400, l.readAll().lines().count { it.startsWith(CrashLog.ENTRY_MARK) })
    }

    @Test fun `concorrencia com rotacao nunca lanca e tamanho fica limitado`() {
        val l = log(max = 4_000)
        val pool = Executors.newFixedThreadPool(4)
        repeat(4) { pool.execute { repeat(100) { l.record("X", RuntimeException("m")) } } }
        pool.shutdown(); assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        assertTrue(l.file.length() <= 4_000 + 16_500)
        assertTrue(l.rotatedFile.length() <= 4_000 + 16_500)
    }

    @Test fun `arquivo corrompido (bytes binarios) nao quebra leitura, record nem contagem`() {
        val l = log()
        l.file.parentFile!!.mkdirs()
        l.file.writeBytes(byteArrayOf(0, -1, -2, 0x7f, 10, -128, 13, 0))
        l.record("DOWNLOAD", IllegalStateException("x"))
        assertEquals(1, l.info.value.count)
        assertTrue(l.readAll().contains("[DOWNLOAD]"))
    }

    @Test fun `arquivo inexistente - readAll vazio, export null, clear ok`() {
        val l = log()
        assertEquals("", l.readAll()); assertNull(l.export())
        l.clear(); assertEquals(0, l.info.value.count)
    }

    @Test fun `dir e na verdade um arquivo - record nao lanca`() {
        val f = tmp.newFile("logs")
        val l = log(dir = f)
        l.record("A", RuntimeException("x"))
        assertEquals(0, l.info.value.count)
    }

    @Test fun `registro depois de clear volta a contar de 1`() {
        val l = log(); l.record("A", RuntimeException("1")); l.clear(); l.record("A", RuntimeException("2"))
        assertEquals(1, l.info.value.count)
    }

    @Test fun `Throwable com getMessage lancando nao derruba record`() {
        val bad = object : RuntimeException() { override val message: String get() = throw IllegalStateException("boom") }
        log().record("A", bad) // nao deve lancar
    }

    @Test fun `CancellationException e Error sao registraveis`() {
        val l = log()
        l.record("A", CancellationException("c")); l.record("B", OutOfMemoryError())
        assertEquals(2, l.info.value.count)
        assertTrue(l.info.value.last!!.contains("OutOfMemoryError"))
    }

    @Test fun `causa ciclica nao trava o format`() {
        val a = RuntimeException("a"); val b = RuntimeException("b", a); a.initCause(b)
        assertTrue(CrashLog.format("X", a, 0).isNotEmpty())
    }

    @Test fun `texto unicode e quebras de linha na mensagem nao geram falso titulo`() {
        val l = log()
        l.record("A", RuntimeException("linha1\n===== falso titulo ¬ç✓"))
        assertEquals(1, l.info.value.count) // BUG se contar 2: mensagem injeta ENTRY_MARK no corpo (stack tem a msg completa)
    }

    @Test fun `export nao contem cabecalho vazio e e arquivo legivel`() {
        val l = log(); l.record("A", RuntimeException("x"))
        val e = l.export()!!
        assertTrue(e.exists()); assertFalse(e.readText().isBlank())
    }
}
