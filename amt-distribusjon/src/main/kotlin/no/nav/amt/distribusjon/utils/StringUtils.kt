package no.nav.amt.distribusjon.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val FORKORTELSER_MED_STORE_BOKSTAVER = listOf(
    "as",
    "a/s",
)

val ORD_MED_SMA_BOKSTAVER = listOf(
    "i",
    "og",
)

fun toTitleCase(tekst: String): String = tekst.lowercase().split(Regex("(?<=\\s|-|')")).joinToString("") {
    when (it.trim()) {
        in FORKORTELSER_MED_STORE_BOKSTAVER -> {
            it.uppercase()
        }

        in ORD_MED_SMA_BOKSTAVER -> {
            it
        }

        else -> {
            it.replaceFirstChar(Char::uppercaseChar)
        }
    }
}

fun formatDate(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    return date.format(formatter)
}

fun formatDateWithMonthName(date: LocalDate): String {
    val locale = Locale
        .Builder()
        .setLanguageTag("no")
        .setLanguageTag("no")
        .setRegion("NO")
        .build()
    val formatter = DateTimeFormatter.ofPattern("d. MMMM yyyy", locale)
    return date.format(formatter)
}
