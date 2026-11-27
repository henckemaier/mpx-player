package com.mpx.player.data

import com.mpx.player.model.TrackOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideLogicTest {

    @Test fun withTitleAplicaETrima() {
        val m = OverrideLogic.withTitle(emptyMap(), "k", "  Novo  ")
        assertEquals("Novo", m["k"]?.title)
    }

    @Test fun tituloVazioOuNuloSemCapaRemoveEntrada() {
        val base = OverrideLogic.withTitle(emptyMap(), "k", "x")
        assertTrue(OverrideLogic.withTitle(base, "k", "   ").isEmpty())
        assertTrue(OverrideLogic.withTitle(base, "k", null).isEmpty())
    }

    @Test fun mergeTituloECapaIndependentes() {
        var m = OverrideLogic.withTitle(emptyMap(), "k", "T")
        m = OverrideLogic.withCover(m, "k", "/c.jpg")
        assertEquals(TrackOverride("T", "/c.jpg"), m["k"])
        m = OverrideLogic.withCover(m, "k", null)
        assertEquals(TrackOverride("T", null), m["k"])
        m = OverrideLogic.withTitle(m, "k", null)
        assertTrue(m.isEmpty())
    }

    @Test fun capaEmBrancoEhIgnorada() {
        assertTrue(OverrideLogic.withCover(emptyMap(), "k", "  ").isEmpty())
    }

    @Test fun outrasChavesNaoSaoAfetadas() {
        val m = OverrideLogic.withTitle(OverrideLogic.withTitle(emptyMap(), "a", "A"), "b", "B")
        assertEquals(2, m.size)
        assertEquals(1, OverrideLogic.withTitle(m, "a", null).size)
    }

    @Test fun jsonRoundTrip() {
        val m = mapOf("a" to TrackOverride("T", null), "b" to TrackOverride(null, "/c.png"), "c" to TrackOverride("X", "/y"))
        assertEquals(m, OverrideLogic.fromJson(OverrideLogic.toJson(m)))
    }

    @Test fun fromJsonToleraLixo() {
        assertTrue(OverrideLogic.fromJson("").isEmpty())
        assertTrue(OverrideLogic.fromJson("não é json").isEmpty())
        assertTrue(OverrideLogic.fromJson("[1,2]").isEmpty())
    }

    @Test fun fromJsonDescartaEntradasVaziasOuInvalidas() {
        val r = OverrideLogic.fromJson("""{"a":{"title":null,"cover":null},"b":"x","c":{"title":"  ","cover":"/z"}}""")
        assertEquals(setOf("c"), r.keys)
        assertNull(r["c"]?.title)
        assertEquals("/z", r["c"]?.cover)
    }
}
