package com.mpx.player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `UpdateRepository.isNewer` é private e a classe exige Context. Sem tocar na produção,
 * instanciamos sem construtor (Unsafe.allocateInstance) e chamamos o método por reflexão —
 * ele não usa estado da instância.
 */
class UpdateVersionTest {

    private val repo: Any = run {
        val unsafeCls = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeCls.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        unsafeCls.getMethod("allocateInstance", Class::class.java).invoke(unsafe, UpdateRepository::class.java)!!
    }
    private val method = UpdateRepository::class.java
        .getDeclaredMethod("isNewer", String::class.java, String::class.java)
        .apply { isAccessible = true }

    private fun newer(latest: String, current: String) = method.invoke(repo, latest, current) as Boolean

    @Test fun comparaNumericamenteNaoComoTexto() {
        assertTrue(newer("1.10.0", "1.9.0"))
        assertFalse(newer("1.9.0", "1.10.0"))
    }

    @Test fun zeroAEsquerdaEquivale() {
        assertFalse(newer("1.04", "1.4.0"))
        assertFalse(newer("1.4.0", "1.04"))
        assertTrue(newer("1.5.0", "1.04"))
    }

    @Test fun camposFaltantesValemZero() {
        assertFalse(newer("1.6", "1.6.0"))
        assertFalse(newer("1.6.0", "1.6"))
        assertTrue(newer("1.6.1", "1.6"))
        assertTrue(newer("2", "1.9.9"))
    }

    @Test fun iguaisNaoSaoNovidade() {
        assertFalse(newer("1.6.0", "1.6.0"))
    }

    @Test fun prefixoVNaoEhTratadoPorIsNewer() {
        // Caracterização: o KDoc cita "v1.4.0", mas "v1" vira campo vazio e é descartado
        // (=> [6,0] vs [1,6,0]). Hoje é inofensivo porque check() faz removePrefix("v") antes.
        assertTrue(newer("v1.6.0", "1.6.0"))
    }

    @Test fun sufixosComoQaNaoQuebram() {
        // "-qa" vira um campo extra sem dígitos: é descartado (mapNotNull).
        assertFalse(newer("1.6.0-qa", "1.6.0"))
        assertTrue(newer("1.6.1-qa", "1.6.0"))
    }

    @Test fun versaoVaziaNuncaEhMaisNova() {
        assertFalse(newer("", "1.0.0"))
        assertTrue(newer("1.0.0", ""))
    }
}
