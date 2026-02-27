package no.nav.amt.lib.models.person.dto

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class NavEnhetDtoTest {
    @Test
    fun `Skal mappe NavEnhetDto til NavEnhet`() {
        val dto = NavEnhetDto(
            id = UUID.randomUUID(),
            enhetId = "~enhetId~",
            navn = "~navn~",
        )

        val enhet = dto.toModel()

        assertSoftly(enhet) {
            id shouldBe dto.id
            enhetsnummer shouldBe dto.enhetId
            navn shouldBe dto.navn
        }
    }
}
