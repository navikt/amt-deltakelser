package no.nav.amt.deltaker.deltaker

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.arrangor.ArrangorRepository
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepositoryTest.Companion.assertDeltakereAreEqual
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusMedDeltakerId
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerEksternV1Producer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducer
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV1Producer
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.hendelse.HendelseProducer
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.kafka.utils.assertProduced
import no.nav.amt.deltaker.kafka.utils.assertProducedDeltakerEksternV1
import no.nav.amt.deltaker.kafka.utils.assertProducedDeltakerV1
import no.nav.amt.deltaker.kafka.utils.assertProducedFeilregistrert
import no.nav.amt.deltaker.kafka.utils.assertProducedHendelse
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorCtx
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.utils.data.TestData.lagArrangor
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagForslag
import no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.utils.mockAmtArrangorClient
import no.nav.amt.deltaker.utils.mockPersonServiceClient
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvsluttDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.StartdatoRequest
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.TestOutboxEnvironment
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class DeltakerServiceTest {
    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun setup() {
        every { unleashToggle.erKometMasterForTiltakstype(any<Tiltakskode>()) } returns true
        every { unleashToggle.skalProdusereTilDeltakerEksternTopic() } returns true
    }

    @Nested
    inner class Upsert {
        val opprinneligDeltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )

        @BeforeEach
        fun setup() {
            TestRepository.insert(opprinneligDeltaker)
        }

        @Test
        fun `ny status - inserter ny status og deaktiverer gammel`() = runTest {
            val oppdatertDeltaker = opprinneligDeltaker.copy(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                ),
            )

            deltakerService.transactionalDeltakerUpsert(oppdatertDeltaker, false)

            assertDeltakereAreEqual(deltakerRepository.get(opprinneligDeltaker.id).getOrThrow(), oppdatertDeltaker)

            assertSoftly(DeltakerStatusRepository.get(opprinneligDeltaker.status.id)) {
                gyldigTil shouldNotBe null
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(DeltakerStatusRepository.get(oppdatertDeltaker.status.id)) {
                gyldigTil shouldBe null
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }
        }

        @Test
        fun `ny status gyldig i fremtid - inserter ny status, deaktiverer ikke gammel`() = runTest {
            val gyldigFra = LocalDateTime.now().plusDays(3)

            val oppdatertDeltaker = opprinneligDeltaker.copy(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                    gyldigFra = gyldigFra,
                ),
            )

            deltakerService.transactionalDeltakerUpsert(oppdatertDeltaker, false)
            assertDeltakereAreEqual(deltakerRepository.get(opprinneligDeltaker.id).getOrThrow(), opprinneligDeltaker)

            assertSoftly(DeltakerStatusRepository.get(opprinneligDeltaker.status.id)) {
                gyldigTil shouldBe null
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(DeltakerStatusRepository.get(oppdatertDeltaker.status.id)) {
                gyldigTil shouldBe null
                gyldigFra shouldBeCloseTo gyldigFra
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }
        }

        @Test
        fun `har fremtidig status, mottar ny status - inserter ny status, deaktiverer fremtidig status`() = runTest {
            val fremtidigGyldigFra = LocalDateTime.now().plusDays(3)

            val oppdatertDeltakerFremtidigHarSluttet = opprinneligDeltaker.copy(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                    gyldigFra = fremtidigGyldigFra,
                ),
                sluttdato = LocalDate.now().plusDays(3),
            )
            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltakerFremtidigHarSluttet,
                erDeltakerSluttdatoEndret = true,
            )

            val oppdatertDeltakerForlenget = opprinneligDeltaker.copy(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.DELTAR,
                    gyldigFra = LocalDateTime.now(),
                ),
                sluttdato = LocalDate.now().plusWeeks(8),
            )

            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltakerForlenget,
                erDeltakerSluttdatoEndret = true,
            )

            assertDeltakereAreEqual(deltakerRepository.get(opprinneligDeltaker.id).getOrThrow(), oppdatertDeltakerForlenget)

            // forrige status deaktivert
            assertSoftly(DeltakerStatusRepository.get(opprinneligDeltaker.status.id)) {
                gyldigTil.shouldNotBeNull()
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            // nåv@rende status aktiv
            assertSoftly(DeltakerStatusRepository.get(oppdatertDeltakerForlenget.status.id)) {
                gyldigTil.shouldBeNull()
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            // fremtidig status deaktivert
            assertSoftly(DeltakerStatusRepository.get(oppdatertDeltakerFremtidigHarSluttet.status.id)) {
                gyldigTil.shouldNotBeNull()
                fremtidigGyldigFra shouldBeCloseTo fremtidigGyldigFra
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }
        }

        @Test
        fun `har fremtidig status, ny fremtidig status - insert ny fremtidig status, sletter forrige fremtidig status`() = runTest {
            val gyldigFra = LocalDateTime.now().plusDays(3)
            val oppdatertDeltakerHarSluttet = opprinneligDeltaker.copy(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                    gyldigFra = gyldigFra,
                ),
                sluttdato = LocalDate.now().plusDays(3),
            )
            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltakerHarSluttet,
                erDeltakerSluttdatoEndret = true,
            )

            val oppdatertDeltakerHarSluttetNyArsak = opprinneligDeltaker.copy(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.UTDANNING,
                    gyldigFra = gyldigFra,
                ),
                sluttdato = LocalDate.now().plusDays(3),
            )
            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltakerHarSluttetNyArsak,
                erDeltakerSluttdatoEndret = true,
            )

            assertDeltakereAreEqual(
                deltakerRepository.get(opprinneligDeltaker.id).getOrThrow(),
                opprinneligDeltaker.copy(sluttdato = oppdatertDeltakerHarSluttetNyArsak.sluttdato),
            )

            assertThrows<NoSuchElementException> {
                DeltakerStatusRepository.get(oppdatertDeltakerHarSluttet.status.id)
            }

            assertSoftly(DeltakerStatusRepository.get(opprinneligDeltaker.status.id)) {
                gyldigTil shouldBe null
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(DeltakerStatusRepository.get(oppdatertDeltakerHarSluttetNyArsak.status.id)) {
                gyldigTil shouldBe null
                gyldigFra shouldBeCloseTo gyldigFra
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                aarsak.shouldNotBeNull().type shouldBe DeltakerStatus.Aarsak.Type.UTDANNING
            }
        }

        @Test
        fun `har sluttet til deltar, angitt neste status - oppdaterer status, insert neste fremtidige status`() = runTest {
            val opprinneligDeltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
                sluttdato = LocalDate.now().minusDays(2),
            )
            TestRepository.insert(opprinneligDeltaker)

            val nySluttdato = LocalDateTime.now().plusDays(3)
            val oppdatertDeltakerDeltar = opprinneligDeltaker.copy(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.DELTAR,
                    gyldigFra = LocalDateTime.now(),
                ),
                sluttdato = nySluttdato.toLocalDate(),
            )

            val nesteStatus = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.UTDANNING,
                gyldigFra = nySluttdato,
            )

            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltakerDeltar,
                erDeltakerSluttdatoEndret = true,
                nesteStatus = nesteStatus,
            )

            assertDeltakereAreEqual(
                deltakerRepository.get(opprinneligDeltaker.id).getOrThrow(),
                oppdatertDeltakerDeltar,
            )

            assertSoftly(DeltakerStatusRepository.get(opprinneligDeltaker.status.id)) {
                gyldigTil shouldBeCloseTo LocalDateTime.now()
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }

            assertSoftly(DeltakerStatusRepository.get(oppdatertDeltakerDeltar.status.id)) {
                gyldigTil.shouldBeNull()
                gyldigFra shouldBeCloseTo LocalDateTime.now()
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(DeltakerStatusRepository.get(nesteStatus.id)) {
                gyldigTil.shouldBeNull()
                gyldigFra shouldBeCloseTo nySluttdato
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                aarsak.shouldNotBeNull().type shouldBe DeltakerStatus.Aarsak.Type.UTDANNING
            }
        }
    }

    @Nested
    inner class SkalHaStatusDeltar {
        @Test
        fun `venter pa oppstart, startdato passer - returnerer deltaker`() = runTest {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
                startdato = null,
                sluttdato = null,
            )
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                status = lagDeltakerStatus(statusType = DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().minusDays(1),
                sluttdato = LocalDate.now().plusWeeks(2),
            )
            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltaker,
                erDeltakerSluttdatoEndret = true,
            )

            val deltakereSomSkalHaStatusDeltar = deltakerRepository.skalHaStatusDeltar()

            deltakereSomSkalHaStatusDeltar.size shouldBe 1
            deltakereSomSkalHaStatusDeltar.first().id shouldBe deltaker.id
        }

        @Test
        fun `venter pa oppstart, mangler startdato - returnerer ikke deltaker`() = runTest {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
                startdato = null,
                sluttdato = null,
            )
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = null,
                sluttdato = null,
            )
            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltaker,
                erDeltakerSluttdatoEndret = true,
            )

            val deltakereSomSkalHaStatusDeltar = deltakerRepository.skalHaStatusDeltar()

            deltakereSomSkalHaStatusDeltar.size shouldBe 0
        }
    }

    @Nested
    inner class GetAvsluttendeDeltakerStatuserForOppdatering {
        @Test
        fun `returnerer tom liste nar ingen deltaker har aktiv DELTAR-status`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    gyldigTil = null,
                ),
            )
            TestRepository.insert(deltaker)

            val statuser = DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(setOf(deltaker.id))
            statuser.shouldBeEmpty()
        }

        @Test
        fun `fremtidig HAR_SLUTTET-status skal ikke inkluderes`() = runTest {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker)

            val fremtidigStatus = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().plusDays(5),
            )

            deltakerService.transactionalDeltakerUpsert(deltaker.copy(status = fremtidigStatus), false)

            val statuser = DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(setOf(deltaker.id))
            statuser.shouldBeEmpty()
        }

        @Test
        fun `returnerer kun deltakerstatus for deltakere med aktiv DELTAR og gyldig avsluttende status`() = runTest {
            val deltaker1 = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            val deltaker2 = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)

            val status1 = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusDays(1),
            )

            deltakerService.transactionalDeltakerUpsert(
                deltaker = deltaker1,
                erDeltakerSluttdatoEndret = false,
                nesteStatus = status1,
            )

            val statuser = DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(setOf(deltaker1.id, deltaker2.id))
            statuser.size shouldBe 1
            statuser.first().deltakerId shouldBe deltaker1.id
        }

        @Test
        fun `henter avsluttende deltakerstatus for deltaker som har aktiv DELTAR-status og kommende HAR_SLUTTET-status`() = runTest {
            val opprinneligDeltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
                sluttdato = LocalDate.now().minusDays(2),
            )
            TestRepository.insert(opprinneligDeltaker)

            val oppdatertDeltakerDeltar = opprinneligDeltaker.copy(
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.DELTAR,
                    gyldigFra = LocalDateTime.now(),
                ),
                sluttdato = LocalDate.now().plusDays(3),
            )

            val nesteStatus = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.UTDANNING,
                gyldigFra = LocalDateTime.now().minusDays(1),
            )

            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltakerDeltar,
                erDeltakerSluttdatoEndret = opprinneligDeltaker.sluttdato != oppdatertDeltakerDeltar.sluttdato,
                nesteStatus = nesteStatus,
            )

            val statuser: List<DeltakerStatusMedDeltakerId> =
                DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(setOf(opprinneligDeltaker.id))
            statuser.size shouldBe 1

            assertSoftly(statuser.first()) {
                deltakerId shouldBe opprinneligDeltaker.id

                assertSoftly(deltakerStatus) {
                    type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                    aarsak.shouldNotBeNull().type shouldBe DeltakerStatus.Aarsak.Type.UTDANNING
                    gyldigFra.toLocalDate() shouldBe LocalDate.now().minusDays(1)
                    gyldigTil shouldBe null
                }
            }
        }
    }

    @Nested
    inner class UpsertDeltakerTests {
        @Test
        fun `upsertDeltaker - deltaker endrer status fra kladd til utkast - oppdaterer og publiserer til kafka`() = runTest {
            val sistEndretAvEnhet = lagNavEnhet()
            navEnhetRepository.upsert(sistEndretAvEnhet)

            val sistEndretAv = lagNavAnsatt(navEnhetId = sistEndretAvEnhet.id)
            navAnsattRepository.upsert(sistEndretAv)

            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            )
            TestRepository.insert(deltaker)

            val deltakerMedOppdatertStatus = deltaker.copy(
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltakerMedOppdatertStatus,
                opprettetAv = sistEndretAv,
                opprettetAvEnhet = sistEndretAvEnhet,
                fattet = null,
            )
            TestRepository.insert(vedtak)

            val oppdatertDeltaker = deltakerMedOppdatertStatus.copy(
                vedtaksinformasjon = vedtak.tilVedtaksInformasjon(),
            )

            val deltakerFraDb = deltakerService.upsertAndProduceDeltaker(
                deltaker = oppdatertDeltaker,
                erDeltakerSluttdatoEndret = deltaker.sluttdato != oppdatertDeltaker.sluttdato,
            )
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
            deltakerFraDb.vedtaksinformasjon?.opprettetAv shouldBe vedtak.opprettetAv

            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
            assertProducedDeltakerEksternV1(deltaker.id)
        }

        @Test
        fun `upsertDeltaker - oppretter kladd - oppdaterer i db`() = runTest {
            val arrangor = lagArrangor()
            val deltakerliste = lagDeltakerliste(arrangor = arrangor)
            TestRepository.insert(deltakerliste)
            val opprettetAv = lagNavAnsatt()
            val opprettetAvEnhet = lagNavEnhet()
            val navBruker = lagNavBruker(navVeilederId = opprettetAv.id, navEnhetId = opprettetAvEnhet.id)
            TestRepository.insert(navBruker)

            val deltaker = lagDeltaker(
                navBruker = navBruker,
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            )

            val deltakerFraDb = deltakerService.upsertAndProduceDeltaker(
                deltaker = deltaker,
                erDeltakerSluttdatoEndret = false,
            )
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.KLADD
            deltakerFraDb.vedtaksinformasjon shouldBe null
        }
    }

    @Nested
    inner class UpsertEndretDeltakerTests {
        val endretAvEnhet = lagNavEnhet()
        val endretAv = lagNavAnsatt(navEnhetId = endretAvEnhet.id)

        @BeforeEach
        fun setUp() {
            navEnhetRepository.upsert(endretAvEnhet)
            navAnsattRepository.upsert(endretAv)
        }

        @Test
        fun `upsertEndretDeltaker - ingen endring - upserter ikke, men godkjenner forslag`() = runTest {
            val deltaker = lagDeltaker(
                sluttdato = LocalDate.now().minusDays(2),
                sistEndret = LocalDateTime.now().minusDays(2),
            )
            val endretAv = lagNavAnsatt()
            val endretAvEnhet = lagNavEnhet()
            val forslag = lagForslag(deltakerId = deltaker.id)

            TestRepository.insertAll(deltaker, endretAv, endretAvEnhet, forslag)

            val endringsrequest = AvsluttDeltakelseRequest(
                endretAv = endretAv.navIdent,
                endretAvEnhet = endretAvEnhet.enhetsnummer,
                sluttdato = deltaker.sluttdato.shouldNotBeNull(),
                aarsak = null,
                begrunnelse = null,
                forslagId = forslag.id,
                harFullfort = null,
            )

            deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)

            deltakerRepository.get(deltaker.id).getOrThrow().sistEndret shouldBeCloseTo deltaker.sistEndret
            deltakerEndringRepository.getForDeltaker(deltaker.id).shouldBeEmpty()

            assertSoftly(forslagRepository.get(forslag.id).shouldBeSuccess()) {
                it.status.shouldBeInstanceOf<Forslag.Status.Godkjent>()
            }
        }

        @Test
        fun `upsertEndretDeltaker - avslutt i fremtiden - setter fremtidig HAR_SLUTTET`() = runTest {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusMonths(1),
            )
            TestRepository.insert(deltaker)

            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = endretAv,
                opprettetAvEnhet = endretAvEnhet,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insert(vedtak)

            val endringsrequest = AvsluttDeltakelseRequest(
                endretAv = endretAv.navIdent,
                endretAvEnhet = endretAvEnhet.enhetsnummer,
                sluttdato = LocalDate.now().plusWeeks(1),
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = null,
                forslagId = null,
                harFullfort = null,
            )

            val deltakerrespons = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)

            deltakerrespons.status.type shouldBe DeltakerStatus.Type.DELTAR
            deltakerrespons.sluttdato shouldBe endringsrequest.sluttdato

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()
            oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato

            assertSoftly(DeltakerStatusRepository.get(deltaker.status.id)) {
                gyldigTil shouldBe null
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(DeltakerStatusRepository.getFremtidige(oppdatertDeltaker.id).first()) {
                gyldigTil shouldBe null
                gyldigFra.toLocalDate() shouldBe endringsrequest.sluttdato.plusDays(1)
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                aarsak.shouldNotBeNull().type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
            }
        }

        @Test
        fun `upsertEndretDeltaker - avslutt kursdeltaker i fremtiden - setter fremtidig FULLFORT`() = runTest {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusMonths(1),
                deltakerliste = lagDeltakerliste(
                    tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING),
                ),
            )
            TestRepository.insert(deltaker)

            TestRepository.insertAll(deltaker, endretAv, endretAvEnhet)
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = endretAv,
                opprettetAvEnhet = endretAvEnhet,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insert(vedtak)

            val endringsrequest = AvsluttDeltakelseRequest(
                endretAv = endretAv.navIdent,
                endretAvEnhet = endretAvEnhet.enhetsnummer,
                sluttdato = LocalDate.now().plusWeeks(1),
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = null,
                forslagId = null,
                harFullfort = null,
            )

            val deltakerRespons = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)
            deltakerRespons.status.type shouldBe DeltakerStatus.Type.DELTAR
            deltakerRespons.sluttdato shouldBe endringsrequest.sluttdato

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()
            oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
            oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato

            assertSoftly(DeltakerStatusRepository.get(deltaker.status.id)) {
                gyldigTil shouldBe null
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(DeltakerStatusRepository.getFremtidige(oppdatertDeltaker.id).first()) {
                gyldigTil shouldBe null
                gyldigFra.toLocalDate() shouldBe endringsrequest.sluttdato.plusDays(1)
                type shouldBe DeltakerStatus.Type.FULLFORT
                aarsak.shouldNotBeNull().type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
            }
        }

        @Test
        fun `upsertEndretDeltaker - avslutt i fremtiden, blir forlenget - deaktiverer fremtidig HAR_SLUTTET`() = runTest {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusDays(2),
            )
            TestRepository.insert(deltaker)

            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = endretAv,
                opprettetAvEnhet = endretAvEnhet,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insert(vedtak)
            val fremtidigHarSluttetStatus = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().plusDays(2),
            )
            DeltakerStatusRepository.lagreStatus(deltaker.id, fremtidigHarSluttetStatus)

            val endringsrequest = ForlengDeltakelseRequest(
                endretAv = endretAv.navIdent,
                endretAvEnhet = endretAvEnhet.enhetsnummer,
                sluttdato = LocalDate.now().plusMonths(1),
                begrunnelse = "~begrunnelse~",
                forslagId = null,
            )

            val deltakerRespons = deltakerService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = endringsrequest,
            )

            deltakerRespons.status.type shouldBe DeltakerStatus.Type.DELTAR
            deltakerRespons.sluttdato shouldBe endringsrequest.sluttdato

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

            oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
            oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato

            assertSoftly(DeltakerStatusRepository.get(deltaker.status.id)) {
                gyldigTil.shouldBeNull()
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(DeltakerStatusRepository.get(fremtidigHarSluttetStatus.id)) {
                gyldigTil.shouldNotBeNull()
                gyldigFra.toLocalDate() shouldBe LocalDate.now().plusDays(2)
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }
        }

        @Test
        fun `upsertEndretDeltaker - har sluttet, skal delta, avslutt i fremtiden - blir DELTAR, fremtidig HAR_SLUTTET`() = runTest {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
                sluttdato = LocalDate.now().minusWeeks(1),
            )
            TestRepository.insert(deltaker)

            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = endretAv,
                opprettetAvEnhet = endretAvEnhet,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insert(vedtak)

            val endringsrequest = AvsluttDeltakelseRequest(
                endretAv = endretAv.navIdent,
                endretAvEnhet = endretAvEnhet.enhetsnummer,
                sluttdato = LocalDate.now().plusWeeks(1),
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = null,
                forslagId = null,
                harFullfort = null,
            )

            val deltakerrespons = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)

            deltakerrespons.status.type shouldBe DeltakerStatus.Type.DELTAR
            deltakerrespons.sluttdato shouldBe endringsrequest.sluttdato

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

            oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
            oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato

            assertSoftly(DeltakerStatusRepository.get(deltaker.status.id)) {
                gyldigTil shouldBeCloseTo LocalDateTime.now()
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }

            assertSoftly(DeltakerStatusRepository.get(oppdatertDeltaker.status.id)) {
                gyldigTil.shouldBeNull()
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(DeltakerStatusRepository.getFremtidige(oppdatertDeltaker.id).first()) {
                gyldigTil shouldBe null
                gyldigFra.toLocalDate() shouldBe endringsrequest.sluttdato.plusDays(1)
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                aarsak.shouldNotBeNull().type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
            }
        }

        @Test
        fun `upsertEndretDeltaker - endret deltakelsesmengde - upserter endring`() = runTest {
            val deltaker = lagDeltaker()
            val vedtak = lagVedtak(deltakerId = deltaker.id, opprettetAv = endretAv, opprettetAvEnhet = endretAvEnhet)

            TestRepository.insertAll(deltaker, vedtak)

            val endringsrequest = DeltakelsesmengdeRequest(
                endretAv = endretAv.navIdent,
                endretAvEnhet = endretAvEnhet.enhetsnummer,
                deltakelsesprosent = 50,
                dagerPerUke = null,
                forslagId = null,
                begrunnelse = "begrunnelse",
                gyldigFra = LocalDate.now(),
            )

            val resultat = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)

            resultat.deltakelsesprosent shouldBe endringsrequest.deltakelsesprosent?.toFloat()
            resultat.dagerPerUke shouldBe null

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()
            oppdatertDeltaker.deltakelsesprosent shouldBe endringsrequest.deltakelsesprosent?.toFloat()
            oppdatertDeltaker.dagerPerUke shouldBe null

            val endring = deltakerEndringRepository.getForDeltaker(deltaker.id).first()
            endring.endretAv shouldBe endretAv.id
            endring.endretAvEnhet shouldBe endretAvEnhet.id

            (endring.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde)
                .deltakelsesprosent shouldBe endringsrequest.deltakelsesprosent
            (endring.endring as DeltakerEndring.Endring.EndreDeltakelsesmengde)
                .dagerPerUke shouldBe endringsrequest.dagerPerUke

            assertProducedHendelse(deltaker.id, HendelseType.EndreDeltakelsesmengde::class)
            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
            assertProducedDeltakerEksternV1(deltaker.id)
        }

        @Test
        fun `upsertEndretDeltaker - fremtidig deltakelsesmengde - upserter endring, endrer ikke deltaker`() = runTest {
            val deltaker = lagDeltaker()
            val vedtak = lagVedtak(deltakerId = deltaker.id, opprettetAv = endretAv, opprettetAvEnhet = endretAvEnhet)

            TestRepository.insertAll(deltaker, vedtak)

            val endringsrequest = DeltakelsesmengdeRequest(
                endretAv = endretAv.navIdent,
                endretAvEnhet = endretAvEnhet.enhetsnummer,
                deltakelsesprosent = 50,
                dagerPerUke = null,
                forslagId = null,
                begrunnelse = "begrunnelse",
                gyldigFra = LocalDate.now().plusDays(1),
            )

            val resultat = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)
            resultat.deltakelsesprosent shouldBe deltaker.deltakelsesprosent
            resultat.dagerPerUke shouldBe deltaker.dagerPerUke
        }

        @Test
        fun `upsertEndretDeltaker - endret datoer - upserter endring`() = runTest {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusDays(1),
            )
            val vedtak = lagVedtak(deltakerId = deltaker.id, opprettetAv = endretAv, opprettetAvEnhet = endretAvEnhet)

            TestRepository.insertAll(deltaker, vedtak)

            val endringsrequest = StartdatoRequest(
                endretAv = endretAv.navIdent,
                endretAvEnhet = endretAvEnhet.enhetsnummer,
                startdato = LocalDate.now().minusWeeks(1),
                sluttdato = LocalDate.now().plusWeeks(4),
                begrunnelse = null,
                forslagId = null,
            )

            val resultat = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)
            resultat.startdato shouldBe endringsrequest.startdato
            resultat.sluttdato shouldBe endringsrequest.sluttdato
            resultat.status.type shouldBe DeltakerStatus.Type.DELTAR

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()
            oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
            oppdatertDeltaker.startdato shouldBe endringsrequest.startdato
            oppdatertDeltaker.sluttdato shouldBe endringsrequest.sluttdato

            val endring = deltakerEndringRepository.getForDeltaker(deltaker.id).first()
            endring.endretAv shouldBe endretAv.id
            endring.endretAvEnhet shouldBe endretAvEnhet.id

            (endring.endring as DeltakerEndring.Endring.EndreStartdato)
                .startdato shouldBe endringsrequest.startdato
            (endring.endring as DeltakerEndring.Endring.EndreStartdato)
                .sluttdato shouldBe endringsrequest.sluttdato

            assertProducedHendelse(deltaker.id, HendelseType.EndreStartdato::class)
            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
        }

        @Test
        fun `upsertEndretDeltaker - endret startdato - upserter ny dato og status`() = runTest {
            val deltakersSluttdato = LocalDate.now().plusWeeks(3)
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().plusDays(3),
                sluttdato = deltakersSluttdato,
            )

            val vedtak = lagVedtak(deltakerId = deltaker.id, opprettetAv = endretAv, opprettetAvEnhet = endretAvEnhet)
            TestRepository.insertAll(deltaker, vedtak)

            val endringsrequest = StartdatoRequest(
                endretAv = endretAv.navIdent,
                endretAvEnhet = endretAvEnhet.enhetsnummer,
                startdato = LocalDate.now().minusWeeks(2),
                sluttdato = deltakersSluttdato,
                begrunnelse = null,
                forslagId = null,
            )

            val resultat = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)
            resultat.startdato shouldBe endringsrequest.startdato
            resultat.sluttdato shouldBe deltakersSluttdato
            resultat.status.type shouldBe DeltakerStatus.Type.DELTAR

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()
            oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.DELTAR
            oppdatertDeltaker.startdato shouldBe endringsrequest.startdato
            oppdatertDeltaker.sluttdato shouldBe deltakersSluttdato

            val endring = deltakerEndringRepository.getForDeltaker(deltaker.id).first()
            endring.endretAv shouldBe endretAv.id
            endring.endretAvEnhet shouldBe endretAvEnhet.id

            (endring.endring as DeltakerEndring.Endring.EndreStartdato)
                .startdato shouldBe endringsrequest.startdato
            (endring.endring as DeltakerEndring.Endring.EndreStartdato)
                .sluttdato shouldBe endringsrequest.sluttdato

            assertProducedHendelse(deltaker.id, HendelseType.EndreStartdato::class)
            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
            assertProducedDeltakerEksternV1(deltaker.id)
        }

        @Test
        fun `upsertEndretDeltaker - reaktiver - kladd slettes`() = runTest {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
                startdato = LocalDate.now().plusDays(3),
            )
            val kladd = deltaker.copy(
                id = UUID.randomUUID(),
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            )

            val vedtak = lagVedtak(deltakerId = deltaker.id, opprettetAv = endretAv, opprettetAvEnhet = endretAvEnhet)
            TestRepository.insertAll(deltaker, kladd, vedtak)

            val endringsrequest = ReaktiverDeltakelseRequest(
                endretAv = endretAv.navIdent,
                endretAvEnhet = endretAvEnhet.enhetsnummer,
                begrunnelse = "~begrunnelse~",
            )

            val resultat = deltakerService.upsertEndretDeltaker(deltaker.id, endringsrequest)
            assertSoftly(resultat.status) {
                type shouldBe DeltakerStatus.Type.SOKT_INN
            }

            deltakerRepository.get(kladd.id).shouldBeFailure()
        }
    }

    @Nested
    inner class UpsertEndretDeltakereTests {
        @Test
        fun `upsertEndretDeltakere - sett på venteliste - upserter endring`() = runTest {
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
            )
            val deltaker = lagDeltaker(deltakerliste = deltakerliste)
            val deltaker2 = lagDeltaker(deltakerliste = deltakerliste)
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val endretAv = lagNavAnsatt()
            val endretAvEnhet = lagNavEnhet(enhetsnummer = "0326")
            val innsokt = lagInnsoktPaaKurs(deltakerId = deltaker.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
            val innsokt2 = lagInnsoktPaaKurs(deltakerId = deltaker2.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)

            TestRepository.insertAll(endretAv, endretAvEnhet, deltaker, deltaker2, innsokt, innsokt2)

            val endredeDeltakere = deltakerService.oppdaterDeltakere(
                deltakerIder,
                EndringFraTiltakskoordinator.SettPaaVenteliste,
                endretAv.navIdent,
            )

            endredeDeltakere.size shouldBe 2
            endredeDeltakere.first { it.deltaker.id == deltaker.id }.deltaker shouldBeComparableWith deltaker.copy(
                status = deltaker.status.copy(type = DeltakerStatus.Type.VENTELISTE),
                startdato = null,
                sluttdato = null,
            )

            endredeDeltakere
                .first {
                    it.deltaker.id == deltaker2.id
                }.deltaker shouldBeComparableWith deltaker2.copy(
                status = deltaker2.status.copy(type = DeltakerStatus.Type.VENTELISTE),
                startdato = null,
                sluttdato = null,
            )

            val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
            historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            assertProducedHendelse(deltaker.id, HendelseType.SettPaaVenteliste::class)
            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
            assertProducedDeltakerEksternV1(deltaker.id)
            assertProduced(deltaker2.id)
            assertProducedDeltakerV1(deltaker2.id)
            assertProducedDeltakerEksternV1(deltaker2.id)
        }

        @Test
        fun `upsertEndretDeltakere - tildel plass feiler på upsert - ruller tilbake endringer på samme deltaker`() = runTest {
            val endretAv = lagNavAnsatt()
            val endretAvEnhet = lagNavEnhet(enhetsnummer = "0326")
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
            )
            val deltaker1Id = UUID.randomUUID()
            val vedtak = lagVedtak(deltakerId = deltaker1Id, opprettetAvEnhet = endretAvEnhet, opprettetAv = endretAv)
            val deltaker = lagDeltaker(
                id = deltaker1Id,
                deltakerliste = deltakerliste,
                vedtaksinformasjon = vedtak.tilVedtaksInformasjon(),
            )

            val deltaker2 = lagDeltaker(deltakerliste = deltakerliste)
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val innsokt = lagInnsoktPaaKurs(deltakerId = deltaker.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
            val innsokt2 = lagInnsoktPaaKurs(deltakerId = deltaker2.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
            TestRepository.insertAll(endretAv, endretAvEnhet, deltaker, deltaker2, innsokt, innsokt2, vedtak)

            val endredeDeltakereResults = deltakerService.oppdaterDeltakere(
                deltakerIder,
                EndringFraTiltakskoordinator.TildelPlass,
                endretAv.navIdent,
            )

            endredeDeltakereResults.size shouldBe 2
            endredeDeltakereResults
                .first {
                    it.deltaker.id == deltaker.id
                }.deltaker shouldBeComparableWith deltaker.copy(
                status = deltaker.status.copy(type = DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = null,
                sluttdato = null,
                vedtaksinformasjon = vedtak
                    .copy(
                        fattet = LocalDateTime.now(),
                        fattetAvNav = true,
                        sistEndret = LocalDateTime.now(),
                        sistEndretAvEnhet = vedtak.opprettetAvEnhet,
                    ).tilVedtaksInformasjon(),
            )

            val ikkeEndretDeltakerResult = endredeDeltakereResults.first {
                it.deltaker.id == deltaker2.id
            }

            ikkeEndretDeltakerResult.deltaker shouldBeComparableWith deltaker2

            ikkeEndretDeltakerResult.isSuccess shouldBe false
            ikkeEndretDeltakerResult.exception shouldBe
                IllegalStateException("Deltaker ${deltaker2.id} mangler et vedtak som kan fattes")

            val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
            historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 0

            assertProducedHendelse(deltaker.id, HendelseType.TildelPlass::class)
            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
            assertProducedDeltakerEksternV1(deltaker.id)
        }

        @Test
        fun `upsertEndretDeltakere - tildel plass - upserter endring, bruker deltakerliste sin start og sluttdato`() = runTest {
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
                startDato = LocalDate.now().plusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val endretAv = lagNavAnsatt()
            val endretAvEnhet = lagNavEnhet(enhetsnummer = "0326")
            val deltaker = lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
            val deltaker2 = lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                deltakerId = deltaker.id,
                opprettetAv = endretAv,
                opprettetAvEnhet = endretAvEnhet,
                sistEndretAv = endretAv,
                sistEndretAvEnhet = endretAvEnhet,
            )
            val vedtak2 = lagVedtak(
                deltakerVedVedtak = deltaker2,
                deltakerId = deltaker2.id,
                opprettetAv = endretAv,
                opprettetAvEnhet = endretAvEnhet,
                sistEndretAv = endretAv,
                sistEndretAvEnhet = endretAvEnhet,
            )
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val innsokt = lagInnsoktPaaKurs(deltakerId = deltaker.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
            val innsokt2 = lagInnsoktPaaKurs(deltakerId = deltaker2.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
            TestRepository.insertAll(
                endretAv,
                endretAvEnhet,
                deltaker,
                deltaker2,
                innsokt,
                innsokt2,
                vedtak,
                vedtak2,
            )

            val endredeDeltakere = deltakerService.oppdaterDeltakere(
                deltakerIder,
                EndringFraTiltakskoordinator.TildelPlass,
                endretAv.navIdent,
            )
            endredeDeltakere.size shouldBe 2
            val testdeltaker = endredeDeltakere
                .first {
                    it.deltaker.id == deltaker.id
                }.deltaker
            testdeltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            testdeltaker.startdato shouldBe deltakerliste.startDato
            testdeltaker.sluttdato shouldBe deltakerliste.sluttDato

            val testdeltaker2 = endredeDeltakere
                .first {
                    it.deltaker.id == deltaker2.id
                }.deltaker
            testdeltaker2.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            testdeltaker2.startdato shouldBe deltakerliste.startDato
            testdeltaker2.sluttdato shouldBe deltakerliste.sluttDato

            val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
            historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            assertProducedHendelse(deltaker.id, HendelseType.TildelPlass::class)
            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
            assertProducedDeltakerEksternV1(deltaker.id)
            assertProduced(deltaker2.id)
            assertProducedDeltakerV1(deltaker2.id)
            assertProducedDeltakerEksternV1(deltaker2.id)
        }

        @Test
        fun `upsertEndretDeltakere - tildel plass - upserter endring, dato passert får start og sluttdato null`() = runTest {
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
                startDato = LocalDate.now().minusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val deltaker = lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
            val deltaker2 = lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val endretAv = lagNavAnsatt()
            val endretAvEnhet = lagNavEnhet(enhetsnummer = "0326")
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                deltakerId = deltaker.id,
                opprettetAv = endretAv,
                opprettetAvEnhet = endretAvEnhet,
                sistEndretAv = endretAv,
                sistEndretAvEnhet = endretAvEnhet,
            )
            val vedtak2 = lagVedtak(
                deltakerVedVedtak = deltaker2,
                deltakerId = deltaker2.id,
                opprettetAv = endretAv,
                opprettetAvEnhet = endretAvEnhet,
                sistEndretAv = endretAv,
                sistEndretAvEnhet = endretAvEnhet,
            )
            val innsokt = lagInnsoktPaaKurs(deltakerId = deltaker.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
            val innsokt2 = lagInnsoktPaaKurs(deltakerId = deltaker2.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
            TestRepository.insertAll(
                endretAv,
                endretAvEnhet,
                deltaker,
                deltaker2,
                innsokt,
                innsokt2,
                vedtak,
                vedtak2,
            )

            val endredeDeltakere = deltakerService.oppdaterDeltakere(
                deltakerIder,
                EndringFraTiltakskoordinator.TildelPlass,
                endretAv.navIdent,
            )
            endredeDeltakere.size shouldBe 2
            val testdeltaker = endredeDeltakere.first {
                it.deltaker.id == deltaker.id
            }
            testdeltaker.deltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            testdeltaker.deltaker.startdato shouldBe null
            testdeltaker.deltaker.sluttdato shouldBe null

            val testdeltaker2 = endredeDeltakere.first {
                it.deltaker.id == deltaker2.id
            }
            testdeltaker2.deltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            testdeltaker2.deltaker.startdato shouldBe null
            testdeltaker2.deltaker.sluttdato shouldBe null

            val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
            historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            assertProducedHendelse(deltaker.id, HendelseType.TildelPlass::class)
            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
            assertProducedDeltakerEksternV1(deltaker.id)
            assertProduced(deltaker2.id)
            assertProducedDeltakerV1(deltaker2.id)
            assertProducedDeltakerEksternV1(deltaker2.id)
        }

        @Test
        fun `upsertEndretDeltakere - tildel plass feiler på siste deltaker - ruller tilbake en deltaker`() = runTest {
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
                startDato = LocalDate.now().plusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val endretAv = lagNavAnsatt()
            val endretAvEnhet = lagNavEnhet(enhetsnummer = "0326")
            val deltakerInsert = lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
            val deltaker2Insert = lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltakerInsert,
                deltakerId = deltakerInsert.id,
                opprettetAv = endretAv,
                opprettetAvEnhet = endretAvEnhet,
                sistEndretAv = endretAv,
                sistEndretAvEnhet = endretAvEnhet,
            )

            val deltakerIder = setOf(deltakerInsert.id, deltaker2Insert.id)
            val innsokt = lagInnsoktPaaKurs(deltakerId = deltakerInsert.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
            val innsokt2 = lagInnsoktPaaKurs(
                deltakerId = deltaker2Insert.id,
                innsoktAv = endretAv.id,
                innsoktAvEnhet = endretAvEnhet.id,
            )
            TestRepository.insertAll(
                endretAv,
                endretAvEnhet,
                deltakerInsert,
                deltaker2Insert,
                innsokt,
                innsokt2,
                vedtak,
            )

            val deltakereResult = deltakerService.oppdaterDeltakere(
                deltakerIder,
                EndringFraTiltakskoordinator.TildelPlass,
                endretAv.navIdent,
            )

            deltakereResult.size shouldBe 2
            deltakereResult.filter { it.isSuccess }.size shouldBe 1

            assertSoftly(deltakereResult.first { it.deltaker.id == deltakerInsert.id }.deltaker) {
                status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                startdato shouldBe deltakerliste.startDato
                sluttdato shouldBe deltakerliste.sluttDato
            }

            assertSoftly(deltakereResult.first { it.deltaker.id == deltaker2Insert.id }.deltaker) {
                status.type shouldBe deltaker2Insert.status.type
                startdato shouldBe deltaker2Insert.startdato
                sluttdato shouldBe deltaker2Insert.sluttdato
            }

            assertProduced(deltakerInsert.id)
            assertProducedDeltakerV1(deltakerInsert.id)
            assertProducedDeltakerEksternV1(deltakerInsert.id)
            assertProducedHendelse(deltakerInsert.id, HendelseType.TildelPlass::class)
        }

        @Test
        fun `upsertEndretDeltakere - del med arrangør - inserter endring og returnerer endret deltaker`() = runTest {
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(
                    tiltakskode =
                        Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
                ),
            )
            val endretAv = lagNavAnsatt()
            val endretAvEnhet = lagNavEnhet()
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )
            val deltaker2 = lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )

            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val innsokt = lagInnsoktPaaKurs(deltakerId = deltaker.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
            val innsokt2 = lagInnsoktPaaKurs(deltakerId = deltaker2.id, innsoktAv = endretAv.id, innsoktAvEnhet = endretAvEnhet.id)
            TestRepository.insertAll(
                endretAv,
                endretAvEnhet,
                deltaker,
                deltaker2,
                innsokt,
                innsokt2,
            )

            val endredeDeltakere = deltakerService.oppdaterDeltakere(
                deltakerIder,
                EndringFraTiltakskoordinator.DelMedArrangor,
                endretAv.navIdent,
            )
            endredeDeltakere.size shouldBe 2
            val testdeltaker = endredeDeltakere
                .first {
                    it.deltaker.id == deltaker.id
                }.deltaker
            testdeltaker.status.type shouldBe DeltakerStatus.Type.SOKT_INN
            testdeltaker.erManueltDeltMedArrangor shouldBe true
            val testdeltaker2 = endredeDeltakere
                .first {
                    it.deltaker.id == deltaker2.id
                }.deltaker
            testdeltaker2.status.type shouldBe DeltakerStatus.Type.SOKT_INN
            testdeltaker2.erManueltDeltMedArrangor shouldBe true

            val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
            historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
            assertProducedDeltakerEksternV1(deltaker.id)
            assertProduced(deltaker2.id)
            assertProducedDeltakerV1(deltaker2.id)
            assertProducedDeltakerEksternV1(deltaker2.id)
        }
    }

    @Nested
    inner class TransactionalDeltakerUpsertTests {
        @Test
        fun `ny deltaker - returnerer deltaker`() = runTest {
            val expectedDeltaker = lagDeltaker()
            TestRepository.insertAll(expectedDeltaker.deltakerliste, expectedDeltaker.navBruker)

            val result = deltakerService.transactionalDeltakerUpsert(expectedDeltaker, false)
            result.isSuccess.shouldBeTrue()

            val deltakerFromDb = deltakerRepository.get(expectedDeltaker.id).getOrThrow()
            assertDeltakereAreEqual(deltakerFromDb, expectedDeltaker)
        }

        @Test
        fun `ny deltaker - ruller tilbake alle endringer`() = runTest {
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
                startDato = LocalDate.now().plusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val expectedDeltaker = lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
            TestRepository.insertAll(expectedDeltaker.deltakerliste, expectedDeltaker.navBruker)

            val upsertResult = deltakerService.transactionalDeltakerUpsert(expectedDeltaker, false) {
                throw RuntimeException("Feiler")
            }

            upsertResult.isFailure shouldBe true
            val throwable = upsertResult.exceptionOrNull()
            throwable.shouldNotBeNull()
            throwable.message shouldBe "Feiler"

            val getResult = deltakerRepository.get(expectedDeltaker.id)
            getResult.isFailure shouldBe true
        }

        @Test
        fun `ny status, siste insert feiler - ruller tilbake alle endringer`() = runTest {
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
                startDato = LocalDate.now().plusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val endretAv = lagNavAnsatt()
            val endretAvEnhet = lagNavEnhet(enhetsnummer = "0326")
            val deltaker = lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                deltakerId = deltaker.id,
                opprettetAv = endretAv,
                opprettetAvEnhet = endretAvEnhet,
                sistEndretAv = endretAv,
                sistEndretAvEnhet = endretAvEnhet,
            )
            TestRepository.insertAll(
                endretAv,
                endretAvEnhet,
                deltaker,
                vedtak,
            )

            val upsertResult = deltakerService.transactionalDeltakerUpsert(
                deltaker = deltaker.copy(status = nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)),
                erDeltakerSluttdatoEndret = false,
            ) {
                throw RuntimeException("Feiler")
            }

            val throwable = upsertResult.exceptionOrNull()
            throwable.shouldNotBeNull()
            throwable.message shouldBe "Feiler"

            val deltakerFromDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFromDb.status.type shouldBe deltaker.status.type

            val insertedEndring = endringFraTiltakskoordinatorRepository.getForDeltaker(deltaker.id)
            insertedEndring shouldBe emptyList()
        }
    }

    @Test
    fun `giAvslag - deltaker får riktig status og historikk`() = runTest {
        with(EndringFraTiltakskoordinatorCtx()) {
            medInnsok()

            val avslag = EndringFraTiltakskoordinator.Avslag(
                aarsak = EndringFraTiltakskoordinator.Avslag.Aarsak(
                    type = EndringFraTiltakskoordinator.Avslag.Aarsak.Type.KURS_FULLT,
                    beskrivelse = null,
                ),
                begrunnelse = "Fordi...",
            )
            val deltaker = deltakerService.giAvslag(
                deltaker.id,
                avslag,
                navAnsatt.navIdent,
            )

            val endringer = endringFraTiltakskoordinatorRepository.getForDeltaker(deltaker.id)
            endringer.size shouldBe 1
            (endringer.first().endring is EndringFraTiltakskoordinator.Avslag) shouldBe true

            deltaker.status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
            deltaker.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.KURS_FULLT
            deltaker.startdato shouldBe null
            deltaker.sluttdato shouldBe null

            assertProducedHendelse(deltaker.id, HendelseType.Avslag::class)
            assertProduced(deltaker.id)
            assertProducedDeltakerV1(deltaker.id)
            assertProducedDeltakerEksternV1(deltaker.id)
        }
    }

    @Test
    fun `produserDeltakereForPerson - deltaker finnes - publiserer til kafka`() = runTest {
        val sistEndretAvNavEnhet = lagNavEnhet()
        navEnhetRepository.upsert(sistEndretAvNavEnhet)

        val sistEndretAvNavAnsatt = lagNavAnsatt(navEnhetId = sistEndretAvNavEnhet.id)
        navAnsattRepository.upsert(sistEndretAvNavAnsatt)

        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        TestRepository.insert(deltaker)
        val vedtak = lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(vedtak)

        Database.transaction {
            deltakerService.produserDeltakereForPerson(deltaker.navBruker.personident)
        }

        assertProduced(deltaker.id)
        assertProducedDeltakerV1(deltaker.id)
        assertProducedDeltakerEksternV1(deltaker.id)
    }

    @Test
    fun `oppdaterSistBesokt - produserer hendelse`() = runTest {
        val deltaker = lagDeltaker()
        val sistBesokt = ZonedDateTime.now()

        TestRepository.insert(deltaker)

        deltakerService.oppdaterSistBesokt(deltaker.id, sistBesokt)

        assertProducedHendelse(deltaker.id, HendelseType.DeltakerSistBesokt::class)
    }

    @Test
    fun `feilregistrerDeltaker - deltaker feilregistreres og oppdatert deltaker produseres`() = runTest {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
        val vedtak = lagVedtak(deltakerVedVedtak = deltaker)
        val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
        val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)
        val deltakerEndring = lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = ansatt.id,
            endretAvEnhet = enhet.id,
        )
        TestRepository.insertAll(deltaker, ansatt, enhet, vedtak, deltakerEndring)

        deltakerService.feilregistrerDeltaker(deltaker.id)

        assertSoftly(deltakerRepository.get(deltaker.id).getOrThrow()) {
            status.type shouldBe DeltakerStatus.Type.FEILREGISTRERT
            startdato shouldBe null
            sluttdato shouldBe null
            dagerPerUke shouldBe null
            deltakelsesprosent shouldBe null
            bakgrunnsinformasjon shouldBe null
            deltakelsesinnhold shouldBe null
        }

        assertProducedFeilregistrert(deltaker.id)
        assertProducedDeltakerV1(deltaker.id)
        assertProducedDeltakerEksternV1(deltaker.id)
    }

    @Nested
    inner class AvgrensSluttdatoerTilTests {
        @Test
        fun `avgrensSluttdatoerTil - deltaker har senere sluttdato enn deltakerliste - deltakers sluttdato endres`() = runTest {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = deltakerliste.sluttDato!!.plusMonths(1),
            )
            val vedtak = lagVedtak(deltakerVedVedtak = deltaker)
            val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
            val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)

            TestRepository.insertAll(deltakerliste, ansatt, enhet, deltaker, vedtak)

            deltakerService.avgrensSluttdatoerTil(deltakerliste)

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

            oppdatertDeltaker.sluttdato shouldBe deltakerliste.sluttDato
        }

        @Test
        fun `avgrensSluttdatoerTil - deltaker har tidligere sluttdato enn deltakerliste - deltakers sluttdato endres ikke`() = runTest {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = deltakerliste.sluttDato!!.minusDays(1),
            )
            val vedtak = lagVedtak(deltakerVedVedtak = deltaker)
            val ansatt = lagNavAnsatt(id = vedtak.opprettetAv)
            val enhet = lagNavEnhet(id = vedtak.opprettetAvEnhet)

            TestRepository.insertAll(deltakerliste, ansatt, enhet, deltaker, vedtak)

            deltakerService.avgrensSluttdatoerTil(deltakerliste)

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()

            oppdatertDeltaker.sluttdato shouldNotBe deltakerliste.sluttDato
        }
    }

    private val amtPersonClientMock = mockPersonServiceClient()

    private val navEnhetRepository = NavEnhetRepository()
    private val navEnhetService = NavEnhetService(navEnhetRepository, amtPersonClientMock)

    private val navAnsattRepository = NavAnsattRepository()
    private val navAnsattService = NavAnsattService(navAnsattRepository, amtPersonClientMock, navEnhetService)

    private val deltakerRepository = DeltakerRepository()
    private val deltakerEndringRepository = DeltakerEndringRepository()
    private val vedtakRepository = VedtakRepository()
    private val forslagRepository = ForslagRepository()
    private val endringFraArrangorRepository = EndringFraArrangorRepository()
    private val arrangorService = ArrangorService(ArrangorRepository(), mockAmtArrangorClient())
    private val importertFraArenaRepository = ImportertFraArenaRepository()
    private val vurderingRepository = VurderingRepository()
    private val vurderingService = VurderingService(vurderingRepository)

    private val deltakerHistorikkService =
        DeltakerHistorikkService(
            deltakerEndringRepository,
            vedtakRepository,
            forslagRepository,
            endringFraArrangorRepository,
            importertFraArenaRepository,
            InnsokPaaFellesOppstartRepository(),
            EndringFraTiltakskoordinatorRepository(),
            vurderingRepository,
        )

    private val unleashToggle = mockk<CommonUnleashToggle>()

    private val hendelseService = HendelseService(
        HendelseProducer(TestOutboxEnvironment.outboxService),
        navAnsattRepository,
        navAnsattService,
        navEnhetRepository,
        navEnhetService,
        arrangorService,
        deltakerHistorikkService,
        vurderingService,
        unleashToggle = unleashToggle,
    )

    private val deltakerKafkaPayloadBuilder = DeltakerKafkaPayloadBuilder(
        navAnsattRepository = navAnsattRepository,
        navEnhetRepository = navEnhetRepository,
        deltakerHistorikkService,
        vurderingRepository,
    )

    private val deltakerProducer = DeltakerProducer(TestOutboxEnvironment.outboxService, TestOutboxEnvironment.kafkaProducer)
    private val deltakerV1Producer = DeltakerV1Producer(TestOutboxEnvironment.outboxService, TestOutboxEnvironment.kafkaProducer)
    private val deltakerEksternV1Producer =
        DeltakerEksternV1Producer(TestOutboxEnvironment.outboxService, TestOutboxEnvironment.kafkaProducer)
    private val deltakerProducerService = DeltakerProducerService(
        deltakerKafkaPayloadBuilder,
        deltakerProducer,
        deltakerV1Producer,
        deltakerEksternV1Producer,
        unleashToggle,
    )

    private val forslagService = ForslagService(
        forslagRepository,
        ArrangorMeldingProducer(TestOutboxEnvironment.outboxService),
        deltakerRepository,
        deltakerProducerService,
    )
    private val vedtakService = VedtakService(vedtakRepository)
    private val deltakerEndringService =
        DeltakerEndringService(
            deltakerEndringRepository,
            navAnsattRepository,
            navEnhetRepository,
            hendelseService,
            forslagService,
            deltakerHistorikkService,
        )

    private val endringFraTiltakskoordinatorRepository = EndringFraTiltakskoordinatorRepository()

    private val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        deltakerProducerService = deltakerProducerService,
        deltakerEndringRepository = deltakerEndringRepository,
        deltakerEndringService = deltakerEndringService,
        vedtakRepository = vedtakRepository,
        vedtakService = vedtakService,
        hendelseService = hendelseService,
        endringFraArrangorRepository = endringFraArrangorRepository,
        importertFraArenaRepository = importertFraArenaRepository,
        deltakerHistorikkService = deltakerHistorikkService,
        endringFraTiltakskoordinatorRepository = endringFraTiltakskoordinatorRepository,
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
        forslagRepository = forslagRepository,
    )
}

infix fun Deltaker.shouldBeComparableWith(expected: Deltaker?) {
    val statusOpprettetDay = this.status.opprettet
        .toLocalDate()
        .atStartOfDay()
    val gyldigFra = this.status.gyldigFra
        .toLocalDate()
        .atStartOfDay()
    val sistEndret = this.sistEndret.toLocalDate().atStartOfDay()

    fun LocalDateTime.atStartOfDay() = this.toLocalDate().atStartOfDay()

    val now = LocalDateTime.now()
    this.copy(
        sistEndret = sistEndret,
        status = status.copy(id = expected!!.status.id, opprettet = statusOpprettetDay, gyldigFra = gyldigFra),
        opprettet = now,
        vedtaksinformasjon = vedtaksinformasjon?.copy(
            fattet = this.vedtaksinformasjon.fattet?.atStartOfDay(),
            sistEndret = this.vedtaksinformasjon.sistEndret.atStartOfDay()!!,
        ),
    ) shouldBe expected.copy(
        sistEndret = expected.sistEndret.atStartOfDay(),
        status = expected.status.copy(
            id = expected.status.id,
            opprettet = expected.status.opprettet.atStartOfDay(),
            gyldigFra = expected.status.gyldigFra.atStartOfDay(),
        ),
        opprettet = now,
        vedtaksinformasjon = vedtaksinformasjon?.copy(
            fattet = expected.vedtaksinformasjon?.fattet?.atStartOfDay(),
            sistEndret = expected.vedtaksinformasjon?.sistEndret?.atStartOfDay()!!,
        ),
    )
}
