package com.mpx.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pareamento por QR/deep-link (BROWSABLE): só host:porta de LAN e token no formato do agente. */
class PairPayloadTest {

    private val tk = "0123456789abcdef0123456789abcdef" // UUID hex sem hífens, como o agente gera

    @Test fun `payload valido do agente`() {
        assertEquals(PairInfo("192.168.1.33:8080", tk), parsePairPayload("mpx://pair?v=1&h=192.168.1.33:8080&t=$tk"))
        assertEquals(PairInfo("10.0.2.2:8080", tk), parsePairPayload("  mpx://pair?t=$tk&v=1&h=10.0.2.2:8080  "))
    }

    @Test fun `estrutura invalida`() {
        assertNull(parsePairPayload("http://pair?v=1&h=192.168.1.2:80&t=$tk"))
        assertNull(parsePairPayload("mpx://other?v=1&h=192.168.1.2:80&t=$tk"))
        assertNull(parsePairPayload("mpx://pair?v=2&h=192.168.1.2:80&t=$tk"))
        assertNull(parsePairPayload("mpx://pair?v=1&t=$tk"))
        assertNull(parsePairPayload("mpx://pair?v=1&h=192.168.1.2:80"))
        assertNull(parsePairPayload("mpx://pair?v=1&h=192.168.1.2:80&t="))
    }

    @Test fun `host malicioso no payload e rejeitado`() {
        listOf(
            "evil.com:80",                     // host público
            "8.8.8.8:80",                      // IP público
            "user@192.168.1.2:80",             // userinfo
            "192.168.1.2:80/x",                // path
            "192.168.1.2:80?x=1",              // query (dentro de h)
            "192.168.1.2:80#frag",             // fragmento
            "192.168.1.2 :80",                 // espaço
            "192.168.1.2:80@evil.com:80",      // userinfo disfarçado
            "192.168.1.2.evil.com:80",         // sufixo público
            "evil.local.com:80",               // .local no meio
            "%31%39%32.168.1.2:80",            // percent-encoding
            "evil.local:80",                   // nome mDNS: resolveria por DNS unicast (rebinding)
            "localhost:8080",                  // nome: so IP literal
        ).forEach { h -> assertNull("h=$h", parsePairPayload("mpx://pair?v=1&h=$h&t=$tk")) }
    }

    @Test fun `parametro duplicado e fragmento sao rejeitados`() {
        assertNull(parsePairPayload("mpx://pair?v=1&h=192.168.1.2:80&h=evil.com:80&t=$tk"))
        assertNull(parsePairPayload("mpx://pair?v=1&h=192.168.1.2:80&t=$tk#x"))
    }

    @Test fun `token invalido e rejeitado`() {
        listOf("curto", "a".repeat(129), "abc def ghi jklmnop", "token/../../xxxxxxx", "tóken-com-acento-xx", "aaaaaaaaaaaaaaaa%20")
            .forEach { t -> assertNull("t=$t", parsePairPayload("mpx://pair?v=1&h=192.168.1.2:8080&t=$t")) }
        assertTrue(isValidPairToken("A-b_c0123456789XYZ"))
    }

    @Test fun `faixas de IPv4 privadas e loopback`() {
        listOf("10.0.0.1", "10.255.255.255", "127.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.0.1")
            .forEach { assertTrue(it, isLanHost(it)) }
        listOf("172.15.0.1", "172.32.0.1", "192.169.0.1", "11.0.0.1", "0.0.0.0", "255.255.255.255", "169.254.1.1", "1.1.1.1")
            .forEach { assertFalse(it, isLanHost(it)) }
    }

    @Test fun `IPv4 malformado nao passa`() {
        listOf("192.168.1", "192.168.1.2.3", "192.168.1.256", "192.168.01.2", "192.168..2", "-1.0.0.1")
            .forEach { assertFalse(it, isLanHost(it)) }
    }

    @Test fun `nomes sao rejeitados - so IPv4 literal (sem DNS, sem rebinding)`() {
        listOf("localhost", "pc.local", "estudio.casa.local", "LOCALHOST", "router.lan", "local", "ip6-localhost")
            .forEach { assertFalse(it, isLanHost(it)) }
        assertNull(parseLanHostPort("pc.local:8080"))
        assertNull(parseLanHostPort("localhost:8080"))
    }

    @Test fun `porta obrigatoria e na faixa`() {
        assertEquals("192.168.1.2:1", parseLanHostPort("192.168.1.2:1"))
        assertEquals("192.168.1.2:65535", parseLanHostPort("192.168.1.2:65535"))
        listOf("192.168.1.2", "192.168.1.2:", "192.168.1.2:0", "192.168.1.2:65536", "192.168.1.2:99999", "192.168.1.2:80a", "192.168.1.2:+80", ":80")
            .forEach { assertNull(it, parseLanHostPort(it)) }
    }

    @Test fun `mensagem de conexao distingue token recusado, lockout e sem resposta`() {
        assertTrue(connectErrorMessage(401).startsWith("TOKEN RECUSADO PELO PC"))
        assertTrue(connectErrorMessage(403).startsWith("TOKEN RECUSADO PELO PC"))
        assertTrue(connectErrorMessage(429).startsWith("MUITAS TENTATIVAS"))
        assertTrue(connectErrorMessage(null).startsWith("SEM RESPOSTA"))
        assertTrue(connectErrorMessage(200).startsWith("RESPOSTA INVÁLIDA"))
        assertTrue(connectErrorMessage(500).contains("HTTP 500"))
    }

    @Test fun `IPv6 nao e aceito`() {
        assertNull(parseLanHostPort("[fe80::1]:8080"))
        assertNull(parseLanHostPort("fe80::1:8080"))
        assertNull(parseLanHostPort("::1:8080"))
    }

    @Test fun `corta espacos externos`() {
        assertEquals("192.168.1.2:8080", parseLanHostPort("  192.168.1.2:8080 "))
    }
}
