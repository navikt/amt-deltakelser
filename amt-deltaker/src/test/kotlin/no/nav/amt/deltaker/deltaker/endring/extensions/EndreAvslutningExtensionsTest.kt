package no.nav.amt.deltaker.deltaker.endring.extensions

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.endring.extensions.EndringTestUtils.mockDeltakelsesmengdeProvider
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.randomEnhetsnummer
import no.nav.amt.deltaker.utils.data.TestData.randomNavIdent
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndreAvslutningRequest
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EndreAvslutningExtensionsTest {
    @Test
    fun `oppdaterDeltaker - endre avslutning til fullfort`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.AVBRUTT),
            sluttdato = LocalDate.now().minusDays(3),
            deltakerliste = TestData.lagDeltakerliste(
                pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
                oppstart = Oppstartstype.FELLES,
            ),
        )
        val forslag = TestData.lagForslag(
            deltakerId = deltaker.id,
            endring = Forslag.EndreAvslutning(aarsak = EndringAarsak.FattJobb, harDeltatt = true, harFullfort = true),
        )
        val endringsrequest = EndreAvslutningRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            harFullfort = true,
            sluttdato = LocalDate.now().minusDays(1),
            forslagId = forslag.id,
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.FULLFORT
            status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
        }
    }

    @Test
    fun `oppdaterDeltaker - endre avslutning til avbrutt`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.FULLFORT),
            sluttdato = LocalDate.now().minusDays(3),
            deltakerliste = TestData.lagDeltakerliste(
                pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
                oppstart = Oppstartstype.FELLES,
            ),
        )
        val forslag = TestData.lagForslag(
            deltakerId = deltaker.id,
            endring = Forslag.EndreAvslutning(EndringAarsak.FattJobb, null, false),
        )
        val endringsrequest = EndreAvslutningRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            harFullfort = false,
            sluttdato = LocalDate.now().minusDays(1),
            forslagId = forslag.id,
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.AVBRUTT
            status.aarsak
                .shouldNotBeNull()
                .type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
        }
    }

    @Test
    fun `oppdaterDeltaker - endre avslutning ingen endring - gir erVellykket false`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.FULLFORT),
            sluttdato = LocalDate.now().minusDays(3),
        )
        val forslag = TestData.lagForslag(
            deltakerId = deltaker.id,
            endring = Forslag.EndreAvslutning(EndringAarsak.FattJobb, null, true),
        )
        val endringsrequest = EndreAvslutningRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            aarsak = null,
            begrunnelse = "begrunnelse",
            harFullfort = true,
            sluttdato = deltaker.sluttdato,
            forslagId = forslag.id,
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            )

        resultat.shouldBeFailure()
    }
}
