package no.nav.amt.deltaker.deltaker.db

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.DeltakerTestUtils.sammenlignDeltakerEndring
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class DeltakerEndringRepositoryTest {
    private val deltakerEndringRepository = DeltakerEndringRepository()

    private val navEnhetRepository = NavEnhetRepository()
    private val navAnsattRepository = NavAnsattRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `getForDeltaker - to endringer for deltaker, navansatt og enhet finnes - returnerer endring med navn for ansatt og enhet`() {
        val navEnhet1 = lagNavEnhet()
        navEnhetRepository.upsert(navEnhet1)

        val navAnsatt1 = lagNavAnsatt(navEnhetId = navEnhet1.id)
        navAnsattRepository.upsert(navAnsatt1)

        val navEnhet2 = lagNavEnhet()
        navEnhetRepository.upsert(navEnhet2)

        val navAnsatt2 = lagNavAnsatt(navEnhetId = navEnhet2.id)
        navAnsattRepository.upsert(navAnsatt2)

        val deltaker = lagDeltaker()
        val deltakerEndring = lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt1.id,
            endretAvEnhet = navEnhet1.id,
        )
        val deltakerEndring2 = lagDeltakerEndring(
            deltakerId = deltaker.id,
            endring = DeltakerEndring.Endring.EndreInnhold("ledetekst", listOf(Innhold("tekst", "type", true, null))),
            endretAv = navAnsatt2.id,
            endretAvEnhet = navEnhet2.id,
        )
        TestRepository.insert(deltaker)

        deltakerEndringRepository.upsert(deltakerEndring)
        deltakerEndringRepository.upsert(deltakerEndring2)

        val endringFraDb = deltakerEndringRepository.getForDeltaker(deltaker.id)

        endringFraDb.size shouldBe 2
        sammenlignDeltakerEndring(
            endringFraDb.find { it.id == deltakerEndring.id }!!,
            deltakerEndring.copy(endretAv = navAnsatt1.id, endretAvEnhet = navEnhet1.id),
        )
        sammenlignDeltakerEndring(
            endringFraDb.find { it.id == deltakerEndring2.id }!!,
            deltakerEndring2.copy(endretAv = navAnsatt2.id, endretAvEnhet = navEnhet2.id),
        )
    }

    @Test
    fun `getForDeltaker - deltaker er feilregistrert - returnerer tom liste`() {
        val navEnhet1 = lagNavEnhet()
        navEnhetRepository.upsert(navEnhet1)

        val navAnsatt1 = lagNavAnsatt(navEnhetId = navEnhet1.id)
        navAnsattRepository.upsert(navAnsatt1)

        val navEnhet2 = lagNavEnhet()
        navEnhetRepository.upsert(navEnhet2)

        val navAnsatt2 = lagNavAnsatt(navEnhetId = navEnhet2.id)
        navAnsattRepository.upsert(navAnsatt2)

        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.FEILREGISTRERT),
        )
        val deltakerEndring = lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt1.id,
            endretAvEnhet = navEnhet1.id,
        )
        val deltakerEndring2 = lagDeltakerEndring(
            deltakerId = deltaker.id,
            endring = DeltakerEndring.Endring.EndreInnhold("ledetekst", listOf(Innhold("tekst", "type", true, null))),
            endretAv = navAnsatt2.id,
            endretAvEnhet = navEnhet2.id,
        )
        TestRepository.insert(deltaker)

        deltakerEndringRepository.upsert(deltakerEndring)
        deltakerEndringRepository.upsert(deltakerEndring2)

        val endringFraDb = deltakerEndringRepository.getForDeltaker(deltaker.id)

        endringFraDb.size shouldBe 0
    }

    @Test
    fun `getUbehandletDeltakelsesmengder - returnerer endringer som skal behandles i dag`() {
        val navEnhet = lagNavEnhet()
        navEnhetRepository.upsert(navEnhet)

        val navAnsatt = lagNavAnsatt(navEnhetId = navEnhet.id)
        navAnsattRepository.upsert(navAnsatt)

        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )
        TestRepository.insertAll(navEnhet, navAnsatt, deltaker)

        val behandlet = lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                deltakelsesprosent = 100F,
                dagerPerUke = null,
                gyldigFra = LocalDate.now().plusMonths(1),
                begrunnelse = null,
            ),
        )

        val skalBehandles = lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                deltakelsesprosent = 42F,
                dagerPerUke = null,
                gyldigFra = LocalDate.now(),
                begrunnelse = null,
            ),
        )

        val skalBehandlesSenere = lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                deltakelsesprosent = 100F,
                dagerPerUke = null,
                gyldigFra = LocalDate.now().plusMonths(1),
                begrunnelse = null,
            ),
        )

        deltakerEndringRepository.upsert(behandlet)
        deltakerEndringRepository.upsert(skalBehandles, null)
        deltakerEndringRepository.upsert(skalBehandlesSenere, null)

        val endringer = deltakerEndringRepository.getUbehandletDeltakelsesmengder()

        endringer.size shouldBe 1
        sammenlignDeltakerEndring(endringer.first(), skalBehandles)
    }
}
