package no.nav.amt.internapi.deltaker.response

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakelsesmengdeResponseTest {
    @Test
    fun `mapping bevarer manglende prosent med og uten dager`() {
        listOf(3F, null).forEach { dager ->
            // Arrange
            val mengde = Deltakelsesmengde(
                deltakelsesprosent = null,
                dagerPerUke = dager,
                gyldigFra = LocalDate.of(2026, 12, 1),
                opprettet = LocalDateTime.of(2026, 11, 1, 12, 0),
            )

            // Act
            val response = DeltakelsesmengdeResponse.fromDeltakelsesmengde(mengde)
            val deserialisertResponse = objectMapper.readValue<DeltakelsesmengdeResponse>(objectMapper.writeValueAsString(response))

            // Assert
            response shouldBe DeltakelsesmengdeResponse(null, dager, mengde.gyldigFra)
            deserialisertResponse shouldBe response
        }
    }
}
