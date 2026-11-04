package com.mpx.player.shared

import android.content.Context

/**
 * No Android, embrulha o [Context] real do app.
 *
 * Wrapper (e não `typealias` para `Context`): um `actual typealias` para `Context`
 * não compila — `Context` é `abstract` e o `expect class` é `final` (modalidades
 * divergem). O wrapper mantém o `commonMain` livre de Android do mesmo jeito.
 */
actual class PlatformContext(val context: Context)
