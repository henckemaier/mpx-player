package com.mpx.player.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Migração 1.7.0 -> 1.7.1: limpeza do pareamento/índice do desktop-sync removido. */
class LegacyDesktopCleanupTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>

    private val theme = intPreferencesKey("theme")
    private val glow = booleanPreferencesKey("glow")
    private val eqPreset = stringPreferencesKey("eq_preset")
    private val dsHost = stringPreferencesKey("ds_host")
    private val dsToken = stringPreferencesKey("ds_token")

    @Before fun setUp() {
        filesDir = tmp.newFolder("files")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = PreferenceDataStoreFactory.create(scope = scope) { File(filesDir, "datastore/config_sys.preferences_pb") }
    }

    @After fun tearDown() = scope.cancel()

    private fun write(rel: String, text: String = "x"): File =
        File(filesDir, rel).apply { parentFile?.mkdirs(); writeText(text) }

    /** Estado deixado por uma 1.7.0 pareada e com favoritas sincronizadas. */
    private fun seed170() = runBlocking {
        write("datastore/ds_pairing.preferences_pb", "host+token")
        write("sync_index.json", """[{"r":"abc","k":"music/mpx/a - b [abc].mp3","m":42}]""")
        write("playlists.json", "[]")
        write("overrides.json", "{}")
        write("logs/mpx-errors.log", "log")
        store.edit { it[theme] = 3; it[glow] = true; it[eqPreset] = "ROCK"; it[dsHost] = "192.168.0.10:8099"; it[dsToken] = "tok_1234567890abcdef" }
    }

    @Test fun `remove pareamento indice e chaves ds sem tocar no resto`() = runBlocking {
        seed170()
        val r = LegacyDesktopCleanup.run(filesDir, store)

        assertEquals(listOf("datastore/ds_pairing.preferences_pb", "sync_index.json"), r.removedFiles)
        assertEquals(2, r.removedKeys)
        assertTrue(r.changed)
        assertFalse(File(filesDir, "datastore/ds_pairing.preferences_pb").exists())
        assertFalse(File(filesDir, "sync_index.json").exists())
        // Dados que NÃO são do desktop ficam intactos.
        assertTrue(File(filesDir, "playlists.json").exists())
        assertTrue(File(filesDir, "overrides.json").exists())
        assertTrue(File(filesDir, "logs/mpx-errors.log").exists())
        assertTrue(File(filesDir, "datastore/config_sys.preferences_pb").exists())
        val p = store.data.first()
        assertEquals(3, p[theme]); assertEquals(true, p[glow]); assertEquals("ROCK", p[eqPreset])
        assertEquals(null, p[dsHost]); assertEquals(null, p[dsToken])
    }

    @Test fun `idempotente - segunda execucao nao faz nada`() = runBlocking {
        seed170()
        LegacyDesktopCleanup.run(filesDir, store)
        val again = LegacyDesktopCleanup.run(filesDir, store)
        assertEquals(emptyList<String>(), again.removedFiles)
        assertEquals(0, again.removedKeys)
        assertFalse(again.changed)
        assertEquals(3, store.data.first()[theme])
    }

    @Test fun `instalacao limpa (1_6_1 ou nova) nao muda nada`() = runBlocking {
        store.edit { it[theme] = 1 }
        val r = LegacyDesktopCleanup.run(filesDir, store)
        assertFalse(r.changed)
        assertEquals(1, store.data.first()[theme])
    }

    @Test fun `remove tambem os tmp da escrita atomica`() = runBlocking {
        write("sync_index.json.tmp")
        write("datastore/ds_pairing.preferences_pb.tmp")
        val r = LegacyDesktopCleanup.run(filesDir, store)
        assertEquals(listOf("datastore/ds_pairing.preferences_pb.tmp", "sync_index.json.tmp"), r.removedFiles)
    }

    @Test fun `diretorio no lugar do arquivo nao quebra e nao e apagado`() = runBlocking {
        File(filesDir, "sync_index.json").mkdirs()
        val r = LegacyDesktopCleanup.run(filesDir, store)
        assertEquals(emptyList<String>(), r.removedFiles)
        assertTrue(File(filesDir, "sync_index.json").isDirectory)
    }

    @Test fun `datastore corrompido - erro reportado e arquivos ainda limpos`() = runBlocking {
        write("sync_index.json")
        val bad = File(filesDir, "datastore/bad.preferences_pb").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03)) }
        val badStore = PreferenceDataStoreFactory.create(scope = scope) { bad }
        val errors = mutableListOf<Throwable>()
        val r = LegacyDesktopCleanup.run(filesDir, badStore) { errors += it }
        assertEquals(listOf("sync_index.json"), r.removedFiles)
        assertEquals(0, r.removedKeys)
        assertEquals(1, errors.size)
    }

    @Test fun `predicado de chave legada`() {
        assertTrue(LegacyDesktopCleanup.isLegacyDesktopKey("ds_host"))
        assertTrue(LegacyDesktopCleanup.isLegacyDesktopKey("ds_token"))
        assertTrue(LegacyDesktopCleanup.isLegacyDesktopKey("ds_qualquer"))
        listOf("theme", "eq_preset", "fx_bass", "last_scan", "lrc_tree", "playlists_imported", "mono_covers")
            .forEach { assertFalse(it, LegacyDesktopCleanup.isLegacyDesktopKey(it)) }
    }
}
