package com.mpx.desktop

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Payload do QR de pareamento: `mpx://pair?v=1&h=<host:port>&t=<token>`. PURO (String in/out) —
 * o app (sub-3) tem um [com.mpx.player.data.parsePairPayload] que lê exatamente este formato.
 * host/port/token do agente são IP site-local, porta fixa e UUID hex (sem chars a escapar).
 */
fun buildPairPayload(host: String, port: Int, token: String): String =
    "mpx://pair?v=1&h=$host:$port&t=$token"

/** Matriz QR DETERMINÍSTICA do payload (mesmo input -> mesma matriz). MARGIN=2 (quiet-zone). */
fun pairQrMatrix(payload: String): BitMatrix =
    QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2))

/**
 * Renderiza a matriz QR scannable no console: fundo CLARO / módulos ESCUROS via ANSI (bg branco
 * vs. bg preto), 2 colunas por módulo (aspecto ~quadrado). Lido pela câmera/leitor do sistema do
 * celular -> abre o deep-link mpx:// no app.
 */
fun qrToAnsi(matrix: BitMatrix): String {
    val dark = "\u001b[40m  \u001b[0m"   // módulo escuro = fundo preto
    val light = "\u001b[107m  \u001b[0m" // claro = fundo branco
    val sb = StringBuilder()
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) sb.append(if (matrix.get(x, y)) dark else light)
        sb.append('\n')
    }
    return sb.toString()
}
