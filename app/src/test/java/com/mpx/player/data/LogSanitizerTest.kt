package com.mpx.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

/** O token do agente desktop nunca pode chegar ao log local em claro. */
class LogSanitizerTest {

    private val tk = "0123456789abcdef0123456789abcdef"

    @Test fun `mascara token em query, Bearer e valor literal`() {
        assertEquals("http://10.0.2.2:8099/stream/ab?token=***", maskSecrets("http://10.0.2.2:8099/stream/ab?token=$tk"))
        assertEquals("http://h:1/x?a=1&t=***&b=2", maskSecrets("http://h:1/x?a=1&t=SEGREDO&b=2"))
        assertEquals("Authorization: Bearer ***", maskSecrets("Authorization: Bearer abcDEF123"))
        assertEquals("falhou com *** no meio", maskSecrets("falhou com $tk no meio", tk))
    }

    @Test fun `nao mexe em texto sem segredo`() {
        val s = "Cleartext HTTP traffic to 10.0.2.2 not permitted"
        assertEquals(s, maskSecrets(s, tk))
    }

    @Test fun `segredo curto demais nao e usado para substituir`() {
        assertEquals("abc", maskSecrets("abc", "ab"))
    }

    @Test fun `sanitizeThrowable limpa mensagem e causas e mantem pilha`() {
        val inner = FileNotFoundException("http://192.168.1.2:8080/art/x?token=$tk")
        val outer = IOException("falha ao baixar com $tk", inner)
        val s = sanitizeThrowable(outer, tk)
        assertTrue(s.message!!.startsWith("java.io.IOException: "))
        assertFalse(s.message!!.contains(tk))
        assertFalse(s.cause!!.message!!.contains(tk))
        assertTrue(s.cause!!.message!!.contains("token=***"))
        assertTrue(s.stackTrace.contentEquals(outer.stackTrace))
        val formatted = CrashLog.format("DESKTOP", s, 0L)
        assertFalse("o registro final do CrashLog nao pode conter o token", formatted.contains(tk))
    }

    @Test fun `CrashLog format mascara excecao CRUA (FATAL, Downloader) sem sanitize previo`() {
        val raw = RuntimeException(
            "falhou http://192.168.1.2:8080/stream/a?token=$tk",
            java.io.IOException("Authorization: Bearer $tk; redirect=http%3A%2F%2Fx%3Ftoken%3D$tk"),
        )
        val out = CrashLog.format("FATAL", raw, 0L)
        assertFalse("titulo/corpo nao podem conter o token", out.contains(tk))
        assertTrue(out.lineSequence().first().contains("?token=***"))
        assertTrue(out.contains("Bearer ***"))
        assertTrue(out.contains("%3Ftoken%3D***"))
    }

    @Test fun `mascara token url-encoded`() {
        assertEquals("u=http%3A%2F%2Fh%3Ftoken%3D***%26a%3D1", maskSecrets("u=http%3A%2F%2Fh%3Ftoken%3D$tk%26a%3D1"))
        assertEquals("x%26t%3D***", maskSecrets("x%26t%3D$tk"))
    }

    @Test fun `titulo de SanitizedException nao repete a classe (nome minificado no release)`() {
        val t = sanitizeThrowable(IllegalStateException("HTTP 401 em http://10.0.2.2:8099/library"), tk)
        val first = CrashLog.format("DESKTOP", t, 0L).lineSequence().first()
        assertTrue(first, first.endsWith("[DESKTOP] java.lang.IllegalStateException: HTTP 401 em http://10.0.2.2:8099/library"))
    }

    @Test fun `causa ciclica nao trava`() {
        val a = IOException("a?token=$tk"); val b = IOException("b", a); a.initCause(b)
        assertFalse(CrashLog.format("X", sanitizeThrowable(a, tk), 0L).contains(tk))
    }
}
