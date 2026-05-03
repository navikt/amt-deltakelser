package no.nav.amt.lib.testing

import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime

infix fun ZonedDateTime.shouldBeEqualTo(expected: ZonedDateTime?) {
    expected.shouldNotBeNull().shouldBeWithin(Duration.ofSeconds(1), this)
}

infix fun ZonedDateTime.shouldBeCloseTo(expected: ZonedDateTime?) {
    expected.shouldNotBeNull().shouldBeWithin(Duration.ofSeconds(10), this)
}

infix fun LocalDateTime.shouldBeEqualTo(expected: LocalDateTime?) {
    expected.shouldNotBeNull().shouldBeWithin(Duration.ofSeconds(1), this)
}

infix fun LocalDateTime?.shouldBeCloseTo(expected: LocalDateTime?) {
    if (this == null) {
        expected.shouldBeNull()
    } else {
        expected.shouldNotBeNull().shouldBeWithin(Duration.ofSeconds(10), this)
    }
}
