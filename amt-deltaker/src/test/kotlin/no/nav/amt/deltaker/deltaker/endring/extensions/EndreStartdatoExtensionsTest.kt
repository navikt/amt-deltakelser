package no.nav.amt.deltaker.deltaker.endring.extensions

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.endring.extensions.EndringTestUtils.mockDeltakelsesmengdeProvider
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.randomEnhetsnummer
import no.nav.amt.deltaker.utils.data.TestData.randomNavIdent
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.StartdatoRequest
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EndreStartdatoExtensionsTest {
    @Test
    fun `oppdaterDeltaker - endret start- og sluttdato i fortid, venter pa oppstart - deltaker blir har sluttet`() {
        val deltaker =
            TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val endringsrequest = StartdatoRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            startdato = LocalDate.now().minusWeeks(10),
            sluttdato = LocalDate.now().minusDays(4),
            begrunnelse = null,
            forslagId = null,
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            startdato shouldBe endringsrequest.startdato
            sluttdato shouldBe endringsrequest.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltaker - endret sluttdato i fortid, startdato mangler, venter pa oppstart - blir ikke aktuell`() {
        val deltaker =
            TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val endringsrequest = StartdatoRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            startdato = null,
            sluttdato = LocalDate.now().minusDays(4),
            begrunnelse = null,
            forslagId = null,
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
            startdato shouldBe null
            sluttdato shouldBe null
        }
    }

    @Test
    fun `oppdaterDeltaker - endret start- og sluttdato i fortid, deltar - deltaker blir har sluttet`() {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val endringsrequest = StartdatoRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            startdato = LocalDate.now().minusWeeks(10),
            sluttdato = LocalDate.now().minusDays(4),
            begrunnelse = null,
            forslagId = null,
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            startdato shouldBe endringsrequest.startdato
            sluttdato shouldBe endringsrequest.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltaker - endret start- og sluttdato i fremtid, fullfort - deltaker blir venter pa oppstart`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.FULLFORT),
            startdato = LocalDate.now().minusWeeks(10),
            sluttdato = LocalDate.now().minusDays(4),
        )
        val endringsrequest = StartdatoRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            startdato = LocalDate.now().plusDays(10),
            sluttdato = LocalDate.now().plusWeeks(4),
            begrunnelse = null,
            forslagId = null,
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            startdato shouldBe endringsrequest.startdato
            sluttdato shouldBe endringsrequest.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltaker - endret start- og sluttdato i fremtid, deltar - deltaker blir venter pa oppstart`() {
        val deltaker = TestData.lagDeltaker(status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val endringsrequest = StartdatoRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            startdato = LocalDate.now().plusDays(10),
            sluttdato = LocalDate.now().plusWeeks(4),
            begrunnelse = null,
            forslagId = null,
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            startdato shouldBe endringsrequest.startdato
            sluttdato shouldBe endringsrequest.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltaker - endret start- og sluttdato i fremtid, har sluttet - deltaker blir deltar`() {
        val deltaker = TestData.lagDeltaker(
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = LocalDate.now().minusDays(1),
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
        )

        val endringsrequest = StartdatoRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            startdato = LocalDate.now().minusDays(10),
            sluttdato = LocalDate.now().plusWeeks(4),
            begrunnelse = null,
            forslagId = null,
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            status.type shouldBe DeltakerStatus.Type.DELTAR
            startdato shouldBe endringsrequest.startdato
            sluttdato shouldBe endringsrequest.sluttdato
        }
    }

    @Test
    fun `oppdaterDeltaker - endre oppstart når avbrutt endrer ikke status til fullført`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.AVBRUTT),
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = LocalDate.now().minusDays(1),
            deltakerliste = TestData.lagDeltakerliste(
                pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING,
                oppstart = Oppstartstype.FELLES,
            ),
        )

        val endringsrequest = StartdatoRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            startdato = LocalDate.now().minusMonths(2),
            sluttdato = null,
            begrunnelse = null,
            forslagId = null,
        )

        val resultat = endringsrequest
            .toEndring()
            .oppdaterDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            startdato shouldBe endringsrequest.startdato
            status.type shouldBe DeltakerStatus.Type.AVBRUTT
        }
    }
}
