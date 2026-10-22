package no.nav.amt.deltaker.bff.veileder.api.response

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.OkonomiGodkjentForHistorikk
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DeltakerHistorikkResponseTest {
    @Test
    fun `inkluderer begrunnelse i historikken for godkjent prisendring`() {
        val begrunnelse = "Prisendringen er godkjent"
        val navEnhet = lagNavEnhet()
        val navAnsatt = lagNavAnsatt(navEnhetId = navEnhet.id)
        val historikk = DeltakerHistorikk.EnkeltplassOkonomiGodkjent(
            OkonomiGodkjentForHistorikk(
                sistEndret = LocalDateTime.now(),
                sistEndretAvNavAnsattId = navAnsatt.id,
                sistEndretAvNavEnhetId = navEnhet.id,
                erForsteGodkjenning = false,
                prisinformasjon = PrisinformasjonDto.IngenKostnader(
                    tilleggsopplysninger = "",
                    begrunnelse = begrunnelse,
                ),
            ),
        )

        val response = DeltakerHistorikkResponse
            .fromModels(
                models = listOf(historikk),
                arrangornavn = "Arrangør AS",
                oppstartstype = null,
                pameldingstype = null,
                enheter = mapOf(navEnhet.id to navEnhet),
                ansatte = mapOf(navAnsatt.id to navAnsatt),
            ).single()
            .shouldBeInstanceOf<EnkeltplassOkonomiGodkjentResponse>()

        response.begrunnelse shouldBe begrunnelse
    }
}
