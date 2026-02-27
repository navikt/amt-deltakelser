package no.nav.amt.lib.testing

import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime

infix fun ZonedDateTime.shouldBeEqualTo(expected: ZonedDateTime?) {
    expected shouldNotBe null
    expected!!.shouldBeWithin(Duration.ofSeconds(1), this)
}

infix fun ZonedDateTime.shouldBeCloseTo(expected: ZonedDateTime?) {
    expected shouldNotBe null
    expected!!.shouldBeWithin(Duration.ofSeconds(10), this)
}

infix fun LocalDateTime.shouldBeEqualTo(expected: LocalDateTime?) {
    expected shouldNotBe null
    expected!!.shouldBeWithin(Duration.ofSeconds(1), this)
}

infix fun LocalDateTime?.shouldBeCloseTo(expected: LocalDateTime?) {
    if (this == null) {
        expected shouldBe null
    } else {
        expected!!.shouldBeWithin(Duration.ofSeconds(10), this)
    }
}
