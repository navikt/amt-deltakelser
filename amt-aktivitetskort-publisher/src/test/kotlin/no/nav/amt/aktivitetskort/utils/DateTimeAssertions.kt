package no.nav.amt.aktivitetskort.utils

import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.nulls.shouldNotBeNull
import java.time.Duration
import java.time.LocalDateTime

infix fun LocalDateTime.shouldBeCloseTo(expected: LocalDateTime?) {
    expected.shouldNotBeNull()
    expected.shouldBeWithin(Duration.ofSeconds(10), this)
}
