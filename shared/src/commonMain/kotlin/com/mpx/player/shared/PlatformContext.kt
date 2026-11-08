package com.mpx.player.shared

/**
 * Contexto de plataforma opaco, repassado às fábricas que precisam dele.
 *
 * - Android: embrulha o `android.content.Context` real do app, sem o
 *   `commonMain` depender de tipos Android.
 * - iOS: classe vazia; o parâmetro existe só para a assinatura comum e é
 *   **ignorado** (AVFoundation não precisa de Context).
 */
expect class PlatformContext
