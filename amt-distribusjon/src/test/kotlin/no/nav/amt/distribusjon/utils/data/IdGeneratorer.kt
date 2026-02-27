package no.nav.amt.distribusjon.utils.data

fun randomOrgnr() = (900_000_000..999_999_998).random().toString()

fun randomIdent() = (10_00_00_00_000..31_12_00_99_999).random().toString()

fun randomNavIdent() = ('A'..'Z').random().toString() + (100_000..999_999).random().toString()

fun randomEnhetsnummer() = (1_000..9_999_999).random().toString()
