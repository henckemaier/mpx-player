package com.mpx.player.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Trava a convenção do formatador puro [fmt1] (substitui o `String.format("%.1f", x)` JVM-only no
 * rótulo de PREAMP do EqScreen). Arredonda meio-pra-cima; sem "-0.0". Roda em
 * `:shared:testDebugUnitTest` E `iosSimulatorArm64Test`.
 */
class Fmt1Test {

    @Test fun zero() = assertEquals("0.0", fmt1(0.0))

    @Test fun roundsHalfUp() = assertEquals("1.3", fmt1(1.25)) // 12.5 -> 13

    @Test fun negativeWhole() = assertEquals("-3.0", fmt1(-3.0))

    @Test fun positiveWhole() = assertEquals("12.0", fmt1(12.0))

    @Test fun halfStepPreampValues() {
        // Valores reais do PREAMP (preampToDb = (preamp-12)*0.5f): múltiplos exatos de 0.5.
        assertEquals("2.5", fmt1(2.5))
        assertEquals("-6.0", fmt1(-6.0))
        assertEquals("6.0", fmt1(6.0))
    }

    @Test fun noNegativeZero() = assertEquals("0.0", fmt1(-0.04)) // arredonda a 0 → sem sinal
}
