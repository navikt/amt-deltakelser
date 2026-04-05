package no.nav.amt.lib.utils

private const val DOT_CHAR = '.'

private val FORKORTELSER_MED_STORE_BOKSTAVER = setOf(
    "as",
    "a/s",
)

private val ORD_MED_SMA_BOKSTAVER = setOf(
    "i",
    "og",
)

fun String.toTitleCase(): String = this.lowercase().split(Regex("(?<=[\\s\\-'])")).joinToString("") {
    when (it.trim()) {
        in FORKORTELSER_MED_STORE_BOKSTAVER -> it.uppercase()
        in ORD_MED_SMA_BOKSTAVER -> it
        else -> it.replaceFirstChar(Char::uppercaseChar)
    }
}

fun String.trimOgFjernAvsluttendePunktum(): String = this.trim().trimEnd(DOT_CHAR)
