package no.nav.amt.deltaker.veileder.endring.extensions

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.veileder.endring.extensions.EndringTestUtils.mockDeltakelsesmengdeProvider
import no.nav.amt.internapi.deltaker.request.SluttdatoRequest
import no.nav.amt.internapi.deltaker.request.toEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import no.nav.amt.lib.testing.utils.TestData.randomEnhetsnummer
import no.nav.amt.lib.testing.utils.TestData.randomNavIdent
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EndreSluttdatoExtensionsTest {
    @Test
    fun `oppdaterDeltaker - endret sluttdato`() {
        val deltaker = TestData.lagDeltaker()
        val endringsrequest = SluttdatoRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            sluttdato = LocalDate.now().minusWeeks(1),
            begrunnelse = "begrunnelse",
        )

        val resultat = endringsrequest
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            sluttdato shouldBe endringsrequest.sluttdato
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
        }
    }

    @Test
    fun `oppdaterDeltaker - endret sluttdato frem i tid - endrer status og sluttdato`() {
        val deltaker = TestData.lagDeltaker()
        val endringsrequest = SluttdatoRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            sluttdato = LocalDate.now().plusWeeks(1),
            begrunnelse = "begrunnelse",
        )

        val resultat = endringsrequest
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = mockDeltakelsesmengdeProvider,
            ).shouldBeSuccess()

        assertSoftly(resultat.deltaker) {
            sluttdato shouldBe endringsrequest.sluttdato
            status.type shouldBe DeltakerStatus.Type.DELTAR
        }
    }

    @Test
    fun `oppdaterDeltaker - sluttdato endres forbi fremtidig mengde - fremtidig mengde ekskluderes fra periode`() {
        // Docs-scenario 03.01.2025: fremtidig mengde 01.02 faller utenfor ny sluttdato 15.01
        val startdato = LocalDate.now().minusMonths(1)
        val fremtidigMengeGyldigFra = LocalDate.now().plusDays(10)
        val nySluttdato = LocalDate.now().plusDays(5) // Kortere enn fremtidigMengde

        val gjeldendeMengde = Deltakelsesmengde(
            deltakelsesprosent = 40F,
            dagerPerUke = 2F,
            gyldigFra = startdato,
            opprettet = startdato.atStartOfDay(),
        )
        val fremtidigMengde = Deltakelsesmengde(
            deltakelsesprosent = 100F,
            dagerPerUke = null,
            gyldigFra = fremtidigMengeGyldigFra,
            opprettet = fremtidigMengeGyldigFra.atStartOfDay(),
        )
        val deltakelsesmengderMedFremtidig = Deltakelsesmengder(listOf(gjeldendeMengde, fremtidigMengde))

        val deltaker = TestData.lagDeltaker(
            startdato = startdato,
            sluttdato = LocalDate.now().plusMonths(3),
            deltakelsesprosent = gjeldendeMengde.deltakelsesprosent,
            dagerPerUke = gjeldendeMengde.dagerPerUke,
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )

        val endringsrequest = SluttdatoRequest(
            endretAv = randomNavIdent(),
            endretAvEnhet = randomEnhetsnummer(),
            forslagId = null,
            sluttdato = nySluttdato,
            begrunnelse = null,
        )

        val resultat = endringsrequest
            .toEndring()
            .anvendPaaDeltaker(
                deltaker = deltaker,
                getDeltakelsemengder = { _ -> deltakelsesmengderMedFremtidig },
            ).shouldBeSuccess()

        // Sluttdato oppdateres
        resultat.deltaker.sluttdato shouldBe nySluttdato

        // Fremtidig mengde etter ny sluttdato skal ikke vises i perioden
        val periodeEtterEndring = deltakelsesmengderMedFremtidig.periode(startdato, nySluttdato)
        periodeEtterEndring.none { it.gyldigFra > nySluttdato } shouldBe true
        periodeEtterEndring.size shouldBe 1
    }

    @Test
    fun `oppdaterDeltaker - sluttdato forlenges - fremtidig mengde innenfor ny sluttdato inkluderes i periode`() {
        // Docs-scenario 05.01.2025: sluttdato forlenges til 31.03 – mengde 01.02 blir gyldig igjen
        val startdato = LocalDate.now().minusMonths(1)
        val fremtidigMengdeGyldigFra = LocalDate.now().plusDays(10)
        val nySluttdato = LocalDate.now().plusMonths(3) // Lengre enn fremtidigMengde

        val gjeldendeMengde = Deltakelsesmengde(
            deltakelsesprosent = 40F,
            dagerPerUke = 2F,
            gyldigFra = startdato,
            opprettet = startdato.atStartOfDay(),
        )
        val fremtidigMengde = Deltakelsesmengde(
            deltakelsesprosent = 100F,
            dagerPerUke = null,
            gyldigFra = fremtidigMengdeGyldigFra,
            opprettet = fremtidigMengdeGyldigFra.atStartOfDay(),
        )
        val deltakelsesmengder = Deltakelsesmengder(listOf(gjeldendeMengde, fremtidigMengde))

        // Fremtidig mengde inkluderes nå fordi sluttdato er etter dens gyldigFra
        val periodeEtterForlengelse = deltakelsesmengder.periode(startdato, nySluttdato)
        periodeEtterForlengelse.size shouldBe 2
        periodeEtterForlengelse.last().gyldigFra shouldBe fremtidigMengdeGyldigFra
    }
}
