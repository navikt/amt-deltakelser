package no.nav.amt.deltaker.enkeltplass

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingDecoratedRequest
import no.nav.amt.internapi.enkeltplass.EnkeltplassPameldingRequest
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Anskaffelse
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EnkeltplassPameldingMedDatoerTest {
    @Test
    fun `konverterer validerte datoer til ikke-nullbare verdier`() {
        val startdato = LocalDate.of(2026, 1, 1)
        val sluttdato = LocalDate.of(2026, 1, 2)

        val pamelding = EnkeltplassPameldingMedDatoer(
            EnkeltplassPameldingDecoratedRequest(
                endretAv = "Z123456",
                endretAvEnhet = "1234",
                wrappedRequest = EnkeltplassPameldingRequest(
                    beskrivelse = "Beskrivelse",
                    arrangorUnderenhet = "987654321",
                    startdato = startdato,
                    sluttdato = sluttdato,
                    prisinformasjon = Anskaffelse(pris = 1000),
                ),
            ),
        )

        pamelding.startdato shouldBe startdato
        pamelding.sluttdato shouldBe sluttdato
    }

    @Test
    fun `kaster exception når startdato mangler`() {
        shouldThrow<IllegalArgumentException> {
            EnkeltplassPameldingMedDatoer(request(startdato = null, sluttdato = LocalDate.of(2026, 1, 2)))
        }
    }

    @Test
    fun `kaster exception når sluttdato mangler`() {
        shouldThrow<IllegalArgumentException> {
            EnkeltplassPameldingMedDatoer(request(startdato = LocalDate.of(2026, 1, 1), sluttdato = null))
        }
    }

    @Test
    fun `kaster exception når sluttdato er før startdato`() {
        shouldThrow<IllegalArgumentException> {
            EnkeltplassPameldingMedDatoer(
                request(
                    startdato = LocalDate.of(2026, 1, 2),
                    sluttdato = LocalDate.of(2026, 1, 1),
                ),
            )
        }
    }

    private fun request(
        startdato: LocalDate?,
        sluttdato: LocalDate?,
    ) = EnkeltplassPameldingDecoratedRequest(
        endretAv = "Z123456",
        endretAvEnhet = "1234",
        wrappedRequest = EnkeltplassPameldingRequest(
            beskrivelse = "Beskrivelse",
            arrangorUnderenhet = "987654321",
            startdato = startdato,
            sluttdato = sluttdato,
            prisinformasjon = Anskaffelse(pris = 1000),
        ),
    )
}
