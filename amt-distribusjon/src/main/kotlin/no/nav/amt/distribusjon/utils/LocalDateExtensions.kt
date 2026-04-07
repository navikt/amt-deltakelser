package no.nav.amt.distribusjon.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val locale = Locale
    .Builder()
    .setLanguageTag("no")
    .setRegion("NO")
    .build()

private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private val formatterWithLocale = DateTimeFormatter.ofPattern("d. MMMM yyyy", locale)

fun LocalDate.formatDate(): String = this.format(formatter)

fun LocalDate.formatDateWithMonthName(): String = this.format(formatterWithLocale)
