package com.mpx.desktop

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** (b) contrato JSON: round-trip do RemoteTrackDto (e da lista) — garante o contrato p/ a sub-2. */
class ContractJsonTest {

    private val json = Json

    private fun sample(id: String, year: Int?, bitrate: Int?) = RemoteTrackDto(
        id = id,
        title = "Signal Decay",
        artist = "Null Sector",
        album = "Midnight Protocol",
        durationMs = 258_000L,
        trackNumber = 3,
        year = year,
        mime = "audio/flac",
        format = "FLAC",
        bitrate = bitrate,
        hasArt = true,
        relPathHint = "Null Sector/Midnight Protocol/03 Signal Decay.flac",
    )

    @Test
    fun roundTrip_umDto_preservaTudo() {
        val dto = sample("abc123", 2024, 1041)
        val restored = json.decodeFromString<RemoteTrackDto>(json.encodeToString(dto))
        assertEquals(dto, restored)
    }

    @Test
    fun roundTrip_lista_preservaOrdemEConteudo() {
        val list = listOf(
            sample("id1", 2024, 320),
            sample("id2", null, null), // nullable year/bitrate
        )
        val restored = json.decodeFromString<List<RemoteTrackDto>>(json.encodeToString(list))
        assertEquals(list, restored)
    }

    @Test
    fun nomesDosCampos_saoOContratoFixo() {
        // A sub-2 parseia por ESTES nomes — qualquer renomeacao quebraria o contrato.
        val encoded = json.encodeToString(sample("id1", 2024, 320))
        listOf(
            "id", "title", "artist", "album", "durationMs", "trackNumber",
            "year", "mime", "format", "bitrate", "hasArt", "relPathHint",
        ).forEach { field ->
            assertTrue("\"$field\"" in encoded, "campo '$field' ausente no JSON do contrato")
        }
    }

    @Test
    fun ping_roundTrip() {
        val p = PingDto("MPX Desktop Agent", "0.1.0", 42)
        assertEquals(p, json.decodeFromString<PingDto>(json.encodeToString(p)))
    }
}
