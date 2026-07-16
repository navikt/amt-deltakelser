package no.nav.amt.deltaker.bff.veileder.api.response

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.deltaker.bff.utils.TestData.lagForslag
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.ForslagDecorator
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ForslagResponseTest {
    private val navEnhet = lagNavEnhet()
    private val navAnsatt = lagNavAnsatt(navEnhetId = navEnhet.id)

    private val forslag = lagForslag(
        status = Forslag.Status.Avvist(
            avvistAv = Forslag.NavAnsatt(
                id = navAnsatt.id,
                enhetId = navEnhet.id,
            ),
            avvist = LocalDateTime.now(),
            begrunnelseFraNav = "Begrunnelse fra Nav",
        ),
    )

    @Nested
    inner class FromForslagDecoratorTests {
        @Test
        fun `skal mappe ForslagDecorator til ForslagResponse`() {
            // Arrange
            val dekorertForslag = ForslagDecorator.AvvistStatusDecorator(
                forslag,
                avvistAvEnhetNavn = navEnhet.navn,
                avvistAvAnsattNavn = navAnsatt.navn,
            )

            // Act
            val response = ForslagResponse.fromForslagDecorator(
                dekorertForslag = dekorertForslag,
                arrangornavn = "Arrangør AS",
            )

            // Assert
            assertForslagResponse(response, forslag)
        }
    }

    @Nested
    inner class FromForslagTests {
        @Test
        fun `skal mappe Forslag til ForslagResponse`() {
            // Act
            val response = ForslagResponse.fromForslag(
                forslag = forslag,
                arrangornavn = "Arrangør AS",
                enheter = mapOf(navEnhet.id to navEnhet),
                ansatte = mapOf(navAnsatt.id to navAnsatt),
            )

            // Assert
            assertForslagResponse(response, forslag)
        }

        @Test
        fun `skal mappe Forslag til ForslagResponse med UUID for Nav-enhet og Nav-ansatt`() {
            // Act
            val response = ForslagResponse.fromForslag(
                forslag = forslag,
                arrangornavn = "Arrangør AS",
                enheter = emptyMap(),
                ansatte = emptyMap(),
            )

            // Assert
            response.status.shouldBeInstanceOf<ForslagResponseStatus.Avvist>()

            assertSoftly(response.status) {
                avvistAv shouldBe navAnsatt.id.toString()
                avvistAvEnhet shouldBe navEnhet.id.toString()
            }
        }
    }

    private fun assertForslagResponse(
        response: ForslagResponse,
        forslag: Forslag,
    ) {
        assertSoftly(response) {
            id shouldBe forslag.id
            opprettet shouldBe forslag.opprettet
            begrunnelse shouldBe forslag.begrunnelse
            arrangorNavn shouldBe "Arrangør AS"
            endring shouldBe ForslagEndringResponse.fromModel(forslag.endring)

            status.shouldBeInstanceOf<ForslagResponseStatus.Avvist>()

            assertSoftly(status) {
                avvistAv shouldBe navAnsatt.navn
                avvistAvEnhet shouldBe navEnhet.navn
                avvist shouldBe status.avvist
                begrunnelseFraNav shouldBe status.begrunnelseFraNav
            }
        }
    }
}
