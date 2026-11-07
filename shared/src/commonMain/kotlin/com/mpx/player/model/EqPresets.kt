package com.mpx.player.model

object EqPresets {
    val FLAT = List(10) { 6 }
    val ROCK = listOf(9, 8, 7, 5, 4, 5, 7, 8, 9, 9)
    val BASS = listOf(11, 10, 9, 7, 6, 6, 6, 6, 6, 6)
    val VOCAL = listOf(4, 5, 6, 7, 9, 10, 9, 7, 6, 5)

    val names = listOf("FLAT", "ROCK", "BASS+", "VOCAL", "CUSTOM")
    val freqLabels = listOf("32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    val freqsHz = listOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    fun levelsFor(name: String, custom: List<Int>): List<Int> = when (name) {
        "FLAT" -> FLAT
        "ROCK" -> ROCK
        "BASS+" -> BASS
        "VOCAL" -> VOCAL
        else -> custom
    }

    fun levelToDb(level: Int): Int = (level - 6) * 2
    fun preampToDb(preamp: Int): Float = (preamp - 12) * 0.5f
}
