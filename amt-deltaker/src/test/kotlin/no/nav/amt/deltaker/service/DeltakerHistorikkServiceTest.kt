package no.nav.amt.deltaker.service

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.extensions.toVurderingFraArrangorData
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.repository.ImportertFraArenaRepository
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.tiltaksansvarlig.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.tiltaksarrangor.endring.EndringFraArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.veileder.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringRepository
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.InnsokPaaFellesOppstart
import no.nav.amt.lib.models.deltaker.extensions.getInnsoktDato
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.testing.utils.TestData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerHistorikkServiceTest {
    private val navEnhetRepository = NavEnhetRepository()
    private val navAnsattRepository = NavAnsattRepository()
    private val deltakerEndringRepository = DeltakerEndringRepository()
    private val forslagRepository = ForslagRepository()
    private val endringFraArrangorRepository = EndringFraArrangorRepository()
    private val vurderingRepository = VurderingRepository()

    private val deltakerHistorikkService = DeltakerHistorikkService(
        deltakerEndringRepository,
        VedtakRepository(),
        forslagRepository,
        endringFraArrangorRepository,
        ImportertFraArenaRepository(),
        InnsokPaaFellesOppstartRepository(),
        EndringFraTiltakskoordinatorRepository(),
        vurderingRepository,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `getForDeltaker - ett vedtak flere endringer og forslag - returner liste riktig sortert`() {
        val navEnhet = TestData.lagNavEnhet()
        navEnhetRepository.upsert(navEnhet)

        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        navAnsattRepository.upsert(navAnsatt)

        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now().minusMonths(1),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            sistEndret = LocalDateTime.now().minusMonths(1),
        )
        val gammelEndring = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endret = LocalDateTime.now().minusDays(20),
        )
        val endringFraArrangor = no.nav.amt.deltaker.utils.data.TestData.lagEndringFraArrangor(
            deltakerId = deltaker.id,
            opprettet = LocalDateTime.now().minusDays(18),
        )
        val forslag = no.nav.amt.deltaker.utils.data.TestData.lagForslag(
            deltakerId = deltaker.id,
            status = Forslag.Status.Tilbakekalt(
                tilbakekaltAvArrangorAnsattId = UUID.randomUUID(),
                tilbakekalt = LocalDateTime.now().minusDays(15),
            ),
        )
        val forslagVenter = no.nav.amt.deltaker.utils.data.TestData
            .lagForslag(deltakerId = deltaker.id)
        val nyEndring = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endret = LocalDateTime.now().minusDays(13),
        )
        val nyVurdering = no.nav.amt.deltaker.utils.data.TestData.lagVurdering(
            deltakerId = deltaker.id,
            gyldigFra = LocalDateTime.now().minusDays(10),
        )

        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)
        deltakerEndringRepository.upsert(gammelEndring)
        endringFraArrangorRepository.insert(endringFraArrangor)
        deltakerEndringRepository.upsert(nyEndring)
        forslagRepository.upsert(forslag)
        forslagRepository.upsert(forslagVenter)
        vurderingRepository.upsert(nyVurdering)

        val historikk = deltakerHistorikkService.getForDeltaker(deltaker.id)

        historikk.size shouldBe 6
        DeltakerTestUtils.sammenlignHistorikk(
            historikk[0],
            DeltakerHistorikk.VurderingFraArrangor(nyVurdering.toVurderingFraArrangorData()),
        )
        DeltakerTestUtils.sammenlignHistorikk(historikk[1], DeltakerHistorikk.Endring(nyEndring))
        DeltakerTestUtils.sammenlignHistorikk(historikk[2], DeltakerHistorikk.Forslag(forslag))
        DeltakerTestUtils.sammenlignHistorikk(historikk[3], DeltakerHistorikk.EndringFraArrangor(endringFraArrangor))
        DeltakerTestUtils.sammenlignHistorikk(historikk[4], DeltakerHistorikk.Endring(gammelEndring))
        DeltakerTestUtils.sammenlignHistorikk(historikk[5], DeltakerHistorikk.Vedtak(vedtak))
    }

    @Test
    fun `getForDeltaker - ingen endringer - returner tom liste`() {
        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        TestRepository.insert(deltaker)

        deltakerHistorikkService.getForDeltaker(deltaker.id) shouldBe emptyList()
    }

    @Test
    fun `getInnsoktDato - ingen vedtak - returnerer null`() {
        val deltakerhistorikk =
            listOf<DeltakerHistorikk>(
                DeltakerHistorikk.Endring(
                    no.nav.amt.deltaker.utils.data.TestData
                        .lagDeltakerEndring(),
                ),
            )

        deltakerhistorikk.getInnsoktDato() shouldBe null
    }

    @Test
    fun `getInnsoktDato - to vedtak - returnerer tidligste opprettetdato`() {
        val deltakerhistorikk = listOf(
            DeltakerHistorikk.Endring(
                no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerEndring(),
            ),
            DeltakerHistorikk.Vedtak(
                no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                    opprettet = LocalDateTime.now().minusMonths(1),
                ),
            ),
            DeltakerHistorikk.Vedtak(
                no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                    opprettet = LocalDateTime.now().minusDays(4),
                ),
            ),
        )

        deltakerhistorikk.getInnsoktDato() shouldBeCloseTo LocalDateTime.now().minusMonths(1)
    }

    @Test
    fun `getInnsoktDato - importert arenadeltaker - returnerer riktig dato`() {
        val innsoktDato = LocalDate.now().minusMonths(1)
        val deltakerhistorikk = listOf(
            DeltakerHistorikk.Endring(
                no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerEndring(),
            ),
            DeltakerHistorikk.ImportertFraArena(
                importertFraArena = ImportertFraArena(
                    deltakerId = UUID.randomUUID(),
                    importertDato = LocalDateTime.now(),
                    deltakerVedImport = no.nav.amt.deltaker.utils.data.TestData
                        .lagDeltaker()
                        .toDeltakerVedImport(innsoktDato = innsoktDato),
                ),
            ),
        )

        deltakerhistorikk.getInnsoktDato() shouldBe innsoktDato.atStartOfDay()
    }

    @Test
    fun `getInnsoktDato - har innsok - returnerer riktig dato`() {
        val innsoktDato = LocalDate.now().minusMonths(1)
        val deltakerhistorikk = listOf(
            DeltakerHistorikk.Endring(
                no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerEndring(),
            ),
            DeltakerHistorikk.InnsokPaaFellesOppstart(
                InnsokPaaFellesOppstart(
                    id = UUID.randomUUID(),
                    deltakerId = UUID.randomUUID(),
                    innsokt = innsoktDato.atStartOfDay(),
                    innsoktAv = UUID.randomUUID(),
                    innsoktAvEnhet = UUID.randomUUID(),
                    deltakelsesinnholdVedInnsok = null,
                    utkastDelt = null,
                    utkastGodkjentAvNav = true,
                ),
            ),
        )

        deltakerhistorikk.getInnsoktDato() shouldBe innsoktDato.atStartOfDay()
    }
}
