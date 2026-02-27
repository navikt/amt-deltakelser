package no.nav.amt.distribusjon.journalforing.pdf

private const val DOT_CHAR = '.'

fun String.trimOgFjernAvsluttendePunktum(): String = this.trim().trimEnd(DOT_CHAR)
