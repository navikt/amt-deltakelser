package no.nav.amt.deltaker.bff.utils

import java.time.Duration
import java.time.temporal.ChronoUnit

const val FERIETILLEGG = 5L

fun years(n: Long): Duration = Duration.of(n * 365, ChronoUnit.DAYS)

fun months(n: Long): Duration = Duration.of(n * 30, ChronoUnit.DAYS)

fun weeks(n: Long): Duration = Duration.of(n * 7, ChronoUnit.DAYS)
