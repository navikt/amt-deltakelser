package no.nav.amt.aktivitetskort

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ApplicationSetupTest : IntegrationTest() {
    @Test
    fun `Application starts and answers`() {
        sendRequest(
            "GET",
            "/internal/health/readiness",
        ).also { it.code shouldBe 200 }
    }
}
