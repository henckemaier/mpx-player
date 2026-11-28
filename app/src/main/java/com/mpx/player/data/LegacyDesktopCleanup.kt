package com.mpx.player.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Migração 1.7.0 -> 1.7.1: o recurso de sincronização com o PC (desktop-sync) foi REMOVIDO. No start,
 * apaga o que a 1.7.0 (e as pré-releases dela) deixou salvo:
 *  - DataStore "ds_pairing" (host + TOKEN do agente) -> arquivo `datastore/ds_pairing.preferences_pb`;
 *  - índice do sync offline `sync_index.json` (+ os `.tmp` da escrita atômica);
 *  - chaves legadas `ds_*` (ds_host/ds_token) no DataStore principal "config_sys".
 *
 * NÃO toca nas músicas copiadas pelo sync offline (Music/MPX no MediaStore): são arquivos do usuário
 * e seguem como faixas locais comuns. Idempotente (rodar de novo não faz nada) e tolerante a falha:
 * cada passo é isolado; erro vai para [onError] e o app segue.
 */
object LegacyDesktopCleanup {

    /** Arquivos (relativos ao filesDir) que só existiam por causa do desktop-sync. */
    val LEGACY_FILES: List<String> = listOf(
        "datastore/ds_pairing.preferences_pb",
        "datastore/ds_pairing.preferences_pb.tmp",
        "sync_index.json",
        "sync_index.json.tmp",
    )

    /** Chave do "config_sys" que pertencia ao desktop (nenhuma chave atual começa com "ds_"). */
    fun isLegacyDesktopKey(name: String): Boolean = name.startsWith("ds_")

    /** Apaga os [LEGACY_FILES] presentes; devolve os caminhos relativos removidos. */
    fun deleteLegacyFiles(filesDir: File, onError: (Throwable) -> Unit = {}): List<String> =
        LEGACY_FILES.filter { rel ->
            runCatching {
                val f = File(filesDir, rel)
                f.isFile && f.delete()
            }.onFailure(onError).getOrDefault(false)
        }

    /** Remove as chaves `ds_*` do [store]; só escreve se houver alguma. Devolve quantas removeu. */
    suspend fun purgeLegacyKeys(store: DataStore<Preferences>): Int {
        val legacy = store.data.first().asMap().keys.filter { isLegacyDesktopKey(it.name) }
        if (legacy.isEmpty()) return 0
        store.edit { p -> legacy.forEach { p.remove(it) } }
        return legacy.size
    }

    data class Result(val removedFiles: List<String>, val removedKeys: Int) {
        val changed: Boolean get() = removedFiles.isNotEmpty() || removedKeys > 0
    }

    /** Roda os dois passos; nunca lança (exceto cancelamento da coroutine). */
    suspend fun run(filesDir: File, store: DataStore<Preferences>, onError: (Throwable) -> Unit = {}): Result {
        val files = deleteLegacyFiles(filesDir, onError)
        val keys = try {
            purgeLegacyKeys(store)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            onError(e)
            0
        }
        return Result(files, keys)
    }
}
