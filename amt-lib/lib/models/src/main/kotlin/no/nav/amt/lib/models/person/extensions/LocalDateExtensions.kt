package no.nav.amt.lib.models.person.extensions

import java.time.LocalDate

fun LocalDate.isBetweenEndExclusive(start: LocalDate, end: LocalDate?): Boolean = this >= start && (end == null || this < end)
