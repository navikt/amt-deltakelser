package no.nav.tiltaksarrangor.utils

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
		in FORKORTELSER_MED_STORE_BOKSTAVER -> it.uppercase()
		in ORD_MED_SMA_BOKSTAVER -> it
		else -> it.replaceFirstChar(Char::uppercaseChar)
	}
}
