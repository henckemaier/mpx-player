package com.mpx.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** QR-gen do pareamento (sub-3): payload exato (contrato do parser do app) + matriz determinística. */
class PairingTest {

    @Test
    fun payload_formatoExato() {
        assertEquals(
            "mpx://pair?v=1&h=192.168.1.33:8080&t=abc123def",
            buildPairPayload("192.168.1.33", 8080, "abc123def"),
        )
    }

    @Test
    fun matriz_naoVazia_eDeterministica() {
        val payload = buildPairPayload("192.168.1.33", 8080, "tok-estavel")
        val m1 = pairQrMatrix(payload)
        val m2 = pairQrMatrix(payload)
        assertTrue(m1.width > 0 && m1.height > 0, "matriz não-vazia")
        // mesmo input -> mesma matriz (determinístico)
        assertEquals(m1.toString(), m2.toString())
    }

    @Test
    fun ansi_cobreTodaAMatriz() {
        val m = pairQrMatrix(buildPairPayload("10.0.0.2", 9000, "t"))
        val ansi = qrToAnsi(m)
        // uma quebra de linha por linha da matriz
        assertEquals(m.height, ansi.count { it == '\n' })
    }
}
