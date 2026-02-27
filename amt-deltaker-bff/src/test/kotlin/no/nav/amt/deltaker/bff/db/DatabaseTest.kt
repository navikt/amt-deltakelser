package no.nav.amt.deltaker.bff.db

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DatabaseTest {
    @Test
    fun `toPGObject - value er null - skal skrive null ikke en string`() {
        val value: String? = null
        val result = toPGObject(value)
        result.isNull shouldBe true
        result.value shouldBe null
    }
}
