package com.mpx.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Defesa em profundidade: credenciais em URLs/cabeçalhos nunca chegam ao log local em claro. */
class LogSanitizerTest {

    private val tk = "0123456789abcdef0123456789abcdef"

    @Test fun `mascara token em query, Bearer e valor literal`() {
        assertEquals("https://h.example/x?token=***", maskSecrets("https://h.example/x?token=$tk"))
        assertEquals("http://h:1/x?a=1&t=***&b=2", maskSecrets("http://h:1/x?a=1&t=SEGREDO&b=2"))
        assertEquals("Authorization: Bearer ***", maskSecrets("Authorization: Bearer abcDEF123"))
        assertEquals("falhou com *** no meio", maskSecrets("falhou com $tk no meio", tk))
    }

    @Test fun `nao mexe em texto sem segredo`() {
        val s = "Unable to connect to https://api.github.com/repos/x/y"
        assertEquals(s, maskSecrets(s, tk))
    }

    @Test fun `segredo curto demais nao e usado para substituir`() {
        assertEquals("abc", maskSecrets("abc", "ab"))
    }

    @Test fun `CrashLog format mascara excecao crua (FATAL, Downloader)`() {
        val raw = RuntimeException(
            "falhou https://h.example/a?token=$tk",
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
}
