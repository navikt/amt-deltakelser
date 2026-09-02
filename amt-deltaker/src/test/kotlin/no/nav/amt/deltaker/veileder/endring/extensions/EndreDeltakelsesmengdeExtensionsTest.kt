package no.nav.amt.deltaker.veileder.endring.extensions

import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.veileder.endring.extensions.EndringTestUtils.mockDeltakelsesmengdeProvider
import no.nav.amt.internapi.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.internapi.deltaker.request.toEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.utils.TestData.randomEnhetsnummer
import no.nav.amt.lib.testing.utils.TestData.randomNavIdent
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EndreDeltakelsesmengdeExtensionsTest {
    @Test
    fun `oppdaterDeltaker - gyldigFra er lik startdato - returnerer success`() {
        val startdato = LocalDate.now().plusDays(5)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = startdato,
            sluttdato = LocalDate.now().plusMonths(3),
        )
        val request = DeltakelsesmengdeRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            deltakelsesprosent = 50,
            dagerPerUke = null,
            begrunnelse = null,
            gyldigFra = startdato,
            pavirkerPris = false,
        )

        request
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()
    }

    @Test
    fun `oppdaterDeltaker - gyldigFra er etter startdato - returnerer success`() {
        val startdato = LocalDate.now().minusMonths(1)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            startdato = startdato,
            sluttdato = LocalDate.now().plusMonths(3),
        )
        val request = DeltakelsesmengdeRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            deltakelsesprosent = 50,
            dagerPerUke = null,
            begrunnelse = null,
            gyldigFra = LocalDate.now(),
            pavirkerPris = false,
        )

        request
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()
    }

    @Test
    fun `oppdaterDeltaker - startdato er null - gyldigFra valideres ikke mot startdato - returnerer success`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = null,
            sluttdato = null,
        )
        val request = DeltakelsesmengdeRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            deltakelsesprosent = 50,
            dagerPerUke = null,
            begrunnelse = null,
            gyldigFra = LocalDate.now().minusMonths(2),
            pavirkerPris = false,
        )

        request
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()
    }

    @Test
    fun `oppdaterDeltaker - gyldigFra er lik sluttdato - returnerer success`() {
        val sluttdato = LocalDate.now().plusMonths(1)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = sluttdato,
        )
        val request = DeltakelsesmengdeRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            deltakelsesprosent = 50,
            dagerPerUke = null,
            begrunnelse = null,
            gyldigFra = sluttdato,
            pavirkerPris = false,
        )

        request
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()
    }

    @Test
    fun `oppdaterDeltaker - gyldigFra i dag, ingen startdato eller sluttdato - endrer deltakelsesprosent`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            startdato = null,
            sluttdato = null,
            deltakelsesprosent = 100F,
        )
        val request = DeltakelsesmengdeRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            deltakelsesprosent = 50,
            dagerPerUke = 3,
            begrunnelse = null,
            gyldigFra = LocalDate.now(),
            pavirkerPris = false,
        )

        val resultat = request
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        resultat.deltaker.deltakelsesprosent shouldBe 50F
        resultat.deltaker.dagerPerUke shouldBe 3F
    }

    @Test
    fun `oppdaterDeltaker - fremtidig gyldigFra - returnerer erFremtidigEndring true`() {
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            startdato = LocalDate.now().minusMonths(1),
            sluttdato = LocalDate.now().plusMonths(3),
        )
        val request = DeltakelsesmengdeRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            deltakelsesprosent = 50,
            dagerPerUke = null,
            begrunnelse = null,
            gyldigFra = LocalDate.now().plusDays(10),
            pavirkerPris = false,
        )

        val resultat = request
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        resultat.erFremtidigEndring shouldBe true
    }

    @Test
    fun `oppdaterDeltaker - gyldigFra lik startdato i framtid - endrer deltakelsesprosent umiddelbart`() {
        val today = LocalDate.now()
        val startdato = today.plusDays(5)
        val deltaker = TestData.lagDeltaker(
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = startdato,
            sluttdato = today.plusMonths(3),
            deltakelsesprosent = 100F,
        )
        val request = DeltakelsesmengdeRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            deltakelsesprosent = 50,
            dagerPerUke = 3,
            begrunnelse = null,
            gyldigFra = startdato,
            pavirkerPris = false,
        )

        val resultat = request
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        resultat.deltaker.deltakelsesprosent shouldBe 50F
        resultat.deltaker.dagerPerUke shouldBe 3F
        resultat.erFremtidigEndring shouldBe false
    }
}
