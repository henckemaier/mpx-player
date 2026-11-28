package com.mpx.player.data

import com.mpx.player.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migração 1.7.0 -> 1.7.1: playlists/favoritas e overrides salvos com chaves de faixas REMOTAS (do
 * agente desktop) carregam sem crash. As chaves remotas viram órfãs — iguais às de um arquivo local
 * apagado — e a resolução por chave (LibraryState.tracksForKeys = mapNotNull) simplesmente as ignora.
 * Elas NÃO são apagadas: não dá para distinguir com segurança de uma faixa local temporariamente
 * ausente (ex.: cartão SD), e se a mesma faixa existir no aparelho com a mesma chave ela volta a casar.
 */
class LegacyRemoteStateTest {

    /** playlists.json como a 1.7.0 grava (formato idêntico ao da 1.6.1), com chaves locais e remotas. */
    private val playlists170 = """
        [
          {"id":"fav","name":"FAVORITAS","photo":null,"locked":true,
           "trackKeys":["music/mpx/local.mp3","rock/banda/remota.flac","music/mpx/banda - remota [abc12345].flac"]},
          {"id":"p1","name":"MISTA","photo":"","trackKeys":["rock/banda/remota.flac", 7, null]},
          {"id":"quebrada"}
        ]
    """.trimIndent()

    @Test fun `playlists da 1_7_0 com chaves remotas carregam sem crash`() {
        val list = PlaylistLogic.fromJson(playlists170)
        val fav = list.first { it.id == "fav" }
        assertEquals(3, fav.trackKeys.size)
        assertTrue("music/mpx/local.mp3" in fav.trackKeys)
        // cópia offline (arquivo normal em Music/MPX) continua favoritável/resolvível pela chave local
        assertTrue("music/mpx/banda - remota [abc12345].flac" in fav.trackKeys)
        // entradas não-string são descartadas por-item, a playlist sobrevive
        assertTrue(list.any { it.id == "p1" })
    }

    @Test fun `overrides com chave remota carregam sem crash`() {
        val json = """{"rock/banda/remota.flac":{"title":"X","cover":null},"music/a.mp3":{"title":null,"cover":"/data/x.jpg"}}"""
        val map = OverrideLogic.fromJson(json)
        assertEquals("X", map["rock/banda/remota.flac"]?.title)
        assertEquals("/data/x.jpg", map["music/a.mp3"]?.cover)
    }

    @Test fun `json corrompido nao quebra`() {
        assertEquals(emptyList<Any>(), PlaylistLogic.fromJson("{lixo").filter { it.id != Playlist.FAVORITES_ID })
        assertEquals(emptyMap<String, Any>(), OverrideLogic.fromJson("{lixo"))
    }
}
