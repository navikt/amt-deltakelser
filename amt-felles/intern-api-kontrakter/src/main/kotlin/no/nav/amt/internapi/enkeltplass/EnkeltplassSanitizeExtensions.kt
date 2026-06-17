package no.nav.amt.internapi.enkeltplass

internal const val MAX_LENGTH_BESKRIVELSE = 250
internal const val MAX_LENGTH_ARRANGOR_UNDERENHET = 9

fun String.sanitizeBeskrivelse(): String = this.trim().take(MAX_LENGTH_BESKRIVELSE)

fun String.sanitizeArrangorUnderenhet(): String = this.trim().take(MAX_LENGTH_ARRANGOR_UNDERENHET)
