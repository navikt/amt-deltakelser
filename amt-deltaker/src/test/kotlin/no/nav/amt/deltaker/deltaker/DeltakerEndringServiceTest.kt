package no.nav.amt.deltaker.deltaker

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerTestUtils.sammenlignDeltakerEndring
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.VellykketEndring
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.kafka.utils.assertProducedForslag
import no.nav.amt.deltaker.kafka.utils.assertProducedHendelse
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagForslag
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.deltaker.utils.mockPersonServiceClient
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.FjernOppstartsdatoRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.IkkeAktuellRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.InnholdRequest
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.TestOutboxEnvironment
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerEndringServiceTest {
    private val amtPersonClient = mockPersonServiceClient()
    private val navEnhetRepository = NavEnhetRepository()
    private val navEnhetService = NavEnhetService(navEnhetRepository, amtPersonClient)
    private val navAnsattRepository = NavAnsattRepository()
    private val navAnsattService = NavAnsattService(NavAnsattRepository(), amtPersonClient, navEnhetService)
    private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
    private val forslagRepository = ForslagRepository()
    private val deltakerEndringRepository = DeltakerEndringRepository()
    private val vurderingRepository = VurderingRepository()
    private val vurderingService = VurderingService(vurderingRepository)
    private val unleashToggle = mockk<CommonUnleashToggle>(relaxed = true)

    private val deltakerHistorikkService = DeltakerHistorikkService(
        deltakerEndringRepository,
        VedtakRepository(),
        forslagRepository,
        EndringFraArrangorRepository(),
        ImportertFraArenaRepository(),
        InnsokPaaFellesOppstartRepository(),
        EndringFraTiltakskoordinatorRepository(),
        vurderingRepository,
    )
    private val hendelseService = HendelseService(
        HendelseProducer(TestOutboxEnvironment.outboxService),
        navAnsattRepository,
        navAnsattService,
        navEnhetRepository,
        navEnhetService,
        arrangorService,
        deltakerHistorikkService,
        vurderingService,
        unleashToggle,
    )
    private val forslagService = ForslagService(
        forslagRepository,
        ArrangorMeldingProducer(TestOutboxEnvironment.outboxService),
        DeltakerRepository(),
        mockk(),
    )

    private val deltakerEndringService = DeltakerEndringService(
        deltakerEndringRepository = deltakerEndringRepository,
        navAnsattRepository = navAnsattRepository,
        navEnhetRepository = navEnhetRepository,
        hendelseService = hendelseService,
        forslagService = forslagService,
        deltakerHistorikkService = deltakerHistorikkService,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `upsertEndring - endret bakgrunnsinformasjon - upserter endring og returnerer deltaker`() = runTest {
        val deltaker = lagDeltaker()
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
        val endringResultat = VellykketEndring(
            deltaker = deltaker,
        )
        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = BakgrunnsinformasjonRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            bakgrunnsinformasjon = "Nye opplysninger",
        )

        Database.transaction {
            deltakerEndringService.upsertEndring(
                endringResultat = endringResultat,
                endringRequest = endringsrequest,
            )
        }

        val endring = deltakerEndringRepository.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreBakgrunnsinformasjon)
            .bakgrunnsinformasjon shouldBe endringsrequest.bakgrunnsinformasjon

        assertProducedHendelse(deltaker.id, HendelseType.EndreBakgrunnsinformasjon::class)
    }

    @Test
    fun `upsertEndring - endret innhold - upserter og returnerer endring`() = runTest {
        val deltaker = lagDeltaker()
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
        val utfall = VellykketEndring(deltaker)

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = InnholdRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            deltakelsesinnhold = Deltakelsesinnhold("tekst", listOf(Innhold("Tekst", "kode", true, null))),
        )

        lateinit var resultat: DeltakerEndring.Endring.EndreInnhold

        Database.transaction {
            resultat = deltakerEndringService
                .upsertEndring(
                    endringResultat = utfall,
                    endringRequest = endringsrequest,
                ).endring
                as DeltakerEndring.Endring.EndreInnhold
        }

        resultat.innhold shouldBe endringsrequest.deltakelsesinnhold.innhold
        resultat.ledetekst shouldBe endringsrequest.deltakelsesinnhold.ledetekst

        val endring = deltakerEndringRepository.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.EndreInnhold).innhold shouldBe endringsrequest.deltakelsesinnhold.innhold
        (endring.endring as DeltakerEndring.Endring.EndreInnhold).ledetekst shouldBe endringsrequest.deltakelsesinnhold.ledetekst
        assertProducedHendelse(deltaker.id, HendelseType.EndreInnhold::class)
    }

    @Test
    fun `upsertEndring - forleng deltakelse - upserter endring og returnerer deltaker`() = runTest {
        val deltaker = lagDeltaker()
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
        val forslag = lagForslag(deltakerId = deltaker.id)

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet, forslag)

        val endringsrequest = ForlengDeltakelseRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            sluttdato = LocalDate.now().plusMonths(1),
            begrunnelse = "begrunnelse",
            forslagId = forslag.id,
        )
        val utfall = VellykketEndring(deltaker)

        Database.transaction {
            deltakerEndringService.upsertEndring(
                endringResultat = utfall,
                endringRequest = endringsrequest,
            )
        }

        val endring = deltakerEndringRepository.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.ForlengDeltakelse)
            .sluttdato shouldBe endringsrequest.sluttdato
        (endring.endring as DeltakerEndring.Endring.ForlengDeltakelse)
            .begrunnelse shouldBe endringsrequest.begrunnelse

        val forslagFraDb = forslagRepository.get(forslag.id).getOrThrow()
        (forslagFraDb.status as Forslag.Status.Godkjent).godkjentAv shouldBe Forslag.NavAnsatt(endretAv.id, endretAvEnhet.id)

        assertProducedHendelse(deltaker.id, HendelseType.ForlengDeltakelse::class)
        assertProducedForslag(
            forslag.copy(
                status = Forslag.Status.Godkjent(
                    godkjentAv = Forslag.NavAnsatt(
                        id = endretAv.id,
                        enhetId = endretAvEnhet.id,
                    ),
                    godkjent = LocalDateTime.now(),
                ),
            ),
        )
    }

    @Test
    fun `upsertEndring - ikke aktuell - upserter endring og returnerer deltaker`() = runTest {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
        val forslag = lagForslag(deltakerId = deltaker.id, endring = Forslag.IkkeAktuell(EndringAarsak.FattJobb))
        val utfall = VellykketEndring(deltaker)
        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet, forslag)

        val endringsrequest = IkkeAktuellRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            forslagId = forslag.id,
        )

        Database.transaction {
            deltakerEndringService.upsertEndring(
                endringResultat = utfall,
                endringRequest = endringsrequest,
            )
        }

        val endring = deltakerEndringRepository.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.IkkeAktuell)
            .aarsak shouldBe endringsrequest.aarsak
        (endring.endring as DeltakerEndring.Endring.IkkeAktuell)
            .begrunnelse shouldBe endringsrequest.begrunnelse

        val forslagFraDb = forslagRepository.get(forslag.id).getOrThrow()
        (forslagFraDb.status as Forslag.Status.Godkjent).godkjentAv shouldBe Forslag.NavAnsatt(endretAv.id, endretAvEnhet.id)

        assertProducedHendelse(deltaker.id, HendelseType.IkkeAktuell::class)
        assertProducedForslag(
            forslag.copy(
                status = Forslag.Status.Godkjent(
                    godkjentAv = Forslag.NavAnsatt(
                        id = endretAv.id,
                        enhetId = endretAvEnhet.id,
                    ),
                    godkjent = LocalDateTime.now(),
                ),
            ),
        )
    }

    @Test
    fun `upsertEndring - fjern oppstartsdato - upserter endring og returnerer deltaker`() = runTest {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().plusDays(3),
            sluttdato = LocalDate.now().plusWeeks(4),
        )
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
        val utfall = VellykketEndring(deltaker)

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val endringsrequest = FjernOppstartsdatoRequest(
            endretAv = endretAv.navIdent,
            endretAvEnhet = endretAvEnhet.enhetsnummer,
            forslagId = null,
            begrunnelse = "begrunnelse",
        )

        Database.transaction {
            deltakerEndringService.upsertEndring(
                endringResultat = utfall,
                endringRequest = endringsrequest,
            )
        }

        val endring = deltakerEndringRepository.getForDeltaker(deltaker.id).first()
        endring.endretAv shouldBe endretAv.id
        endring.endretAvEnhet shouldBe endretAvEnhet.id

        (endring.endring as DeltakerEndring.Endring.FjernOppstartsdato)
            .begrunnelse shouldBe endringsrequest.begrunnelse

        assertProducedHendelse(deltaker.id, HendelseType.FjernOppstartsdato::class)
    }

    @Test
    fun `behandleLagretEndring - ubehandlet gyldig endring - oppdaterer deltaker og upserter endring med behandlet`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)

        val deltakelsesprosent = 50F
        val dagerPerUke = 3F
        val id = UUID.randomUUID()

        val ubehandletEndring = upsertEndring(
            lagDeltakerEndring(
                id = id,
                deltakerId = deltaker.id,
                endretAv = endretAv.id,
                endretAvEnhet = endretAvEnhet.id,
                endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = deltakelsesprosent,
                    dagerPerUke = dagerPerUke,
                    gyldigFra = LocalDate.now(),
                    begrunnelse = "begrunnelse",
                ),
                endret = LocalDateTime.now().minusDays(1),
            ),
        )

        val resultat = deltakerEndringService
            .behandleLagretDeltakelsesmengde(ubehandletEndring, deltaker)
            .shouldBeSuccess()

        val oppdatertDeltaker = resultat.deltaker
        oppdatertDeltaker.deltakelsesprosent shouldBe deltakelsesprosent
        oppdatertDeltaker.dagerPerUke shouldBe dagerPerUke

        val ubehandlete = deltakerEndringRepository.getUbehandletDeltakelsesmengder()
        ubehandlete.size shouldBe 0
    }

    @Test
    fun `behandleLagretEndring - ubehandlet ugyldig endring - oppdaterer ikke deltaker og upserter endring med behandlet`() = runTest {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now().minusHours(1),
        )

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet, vedtak)

        val ugyldigEndring = upsertEndring(
            lagDeltakerEndring(
                deltakerId = deltaker.id,
                endretAv = endretAv.id,
                endretAvEnhet = endretAvEnhet.id,
                endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = 90F,
                    dagerPerUke = null,
                    gyldigFra = LocalDate.now(),
                    begrunnelse = "begrunnelse",
                ),
                endret = LocalDateTime.now().minusSeconds(2),
            ),
        )

        val gyldigEndring = upsertEndring(
            lagDeltakerEndring(
                deltakerId = deltaker.id,
                endretAv = endretAv.id,
                endretAvEnhet = endretAvEnhet.id,
                endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = 80F,
                    dagerPerUke = null,
                    gyldigFra = LocalDate.now(),
                    begrunnelse = "begrunnelse",
                ),
                endret = LocalDateTime.now().minusSeconds(1),
            ),
        )

        Database.transaction {
            deltakerEndringService
                .behandleLagretDeltakelsesmengde(ugyldigEndring, deltaker)
                .shouldBeFailure()
        }

        val ubehandlete = deltakerEndringRepository.getUbehandletDeltakelsesmengder()

        ubehandlete.size shouldBe 1
        sammenlignDeltakerEndring(ubehandlete.first(), gyldigEndring)
    }

    @Test
    fun `behandleLagretEndring - endringen er utfort pga endret startdato - oppdaterer ikke deltaker og upserter endring med behandlet`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val endretAv = lagNavAnsatt()
        val endretAvEnhet = lagNavEnhet()
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = endretAv,
            opprettetAvEnhet = endretAvEnhet,
            fattet = LocalDateTime.now().minusWeeks(2),
        )

        val startdato = LocalDate.now().plusWeeks(1)

        val startdatoEndring = lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = endretAv.id,
            endretAvEnhet = endretAvEnhet.id,
            endring = DeltakerEndring.Endring.EndreStartdato(
                startdato = startdato,
                sluttdato = null,
                begrunnelse = null,
            ),
            endret = LocalDateTime.now().minusMinutes(2),
        )

        TestRepository.insertAll(deltaker, endretAv, endretAvEnhet, vedtak, startdatoEndring)

        val fremtidigDeltakelsesprosent = 90F
        val fremtidigDagerPerUke = null

        val fremtidigEndring = upsertEndring(
            lagDeltakerEndring(
                deltakerId = deltaker.id,
                endretAv = endretAv.id,
                endretAvEnhet = endretAvEnhet.id,
                endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = fremtidigDeltakelsesprosent,
                    dagerPerUke = fremtidigDagerPerUke,
                    gyldigFra = startdato,
                    begrunnelse = "begrunnelse",
                ),
                endret = LocalDateTime.now().minusDays(2),
            ),
        )

        val resultat = deltakerEndringService.behandleLagretDeltakelsesmengde(
            fremtidigEndring,
            deltaker.copy(
                deltakelsesprosent = fremtidigDeltakelsesprosent,
                dagerPerUke = fremtidigDagerPerUke,
            ), // deltaker skal være oppdatert pga startdatoendringen...
        )

        resultat.shouldBeFailure()

        val ubehandlete = deltakerEndringRepository.getUbehandletDeltakelsesmengder()
        ubehandlete.size shouldBe 0

        val deltakelsesmengder = deltakerHistorikkService.getForDeltaker(deltaker.id).toDeltakelsesmengder()
        assertSoftly(deltakelsesmengder) {
            size shouldBe 1
            gjeldende shouldBe fremtidigEndring.toDeltakelsesmengde()
            nesteGjeldende shouldBe null
        }
    }

    private fun upsertEndring(endring: DeltakerEndring): DeltakerEndring {
        deltakerEndringRepository.upsert(
            deltakerEndring = endring,
            behandletTidspunkt = null,
        )
        return deltakerEndringRepository.get(endring.id).getOrThrow()
    }
}
