package no.nav.amt.lib.utils

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ToPGObjectTest {
    @Test
    fun `toPGObject - value er null - skal skrive null ikke en string`() {
        val value: String? = null
        val result = toPGObject(value)
        result.isNull shouldBe true
        result.value shouldBe null
    }
}
