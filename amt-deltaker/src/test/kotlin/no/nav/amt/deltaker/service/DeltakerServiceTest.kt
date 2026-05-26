package no.nav.amt.deltaker.service

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
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.kafka.payload.DeltakerEksternV1Dto
import no.nav.amt.deltaker.kafka.payload.DeltakerV1Dto
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.repository.DeltakerRepositoryTest
import no.nav.amt.deltaker.repository.DeltakerStatusRepository
import no.nav.amt.deltaker.repository.dbo.DeltakerStatusMedDeltakerId
import no.nav.amt.deltaker.tiltaksansvarlig.endring.EndringFraTiltakskoordinatorCtx
import no.nav.amt.deltaker.utils.DeltakerUtils
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.assertProduced
import no.nav.amt.deltaker.utils.assertProducedHendelse
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.internapi.deltaker.request.AvsluttDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.internapi.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.internapi.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.StartdatoRequest
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.testing.utils.TestData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class DeltakerServiceTest : IntegrationTestWithDbBase() {
    private val navEnhetInTest = TestData.lagNavEnhet(enhetsnummer = "0326")
    private val navAnsattInTest = TestData.lagNavAnsatt(navEnhetId = navEnhetInTest.id)

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(navEnhetInTest)
        navAnsattRepository.upsert(navAnsattInTest)
    }

    @Nested
    inner class Upsert {
        val opprinneligDeltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            status = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )

        @BeforeEach
        fun setup() {
            TestRepository.insert(opprinneligDeltaker)
        }

        @Test
        fun `ny status - inserter ny status og deaktiverer gammel`() = runTest {
            // Arrange
            val oppdatertDeltaker = opprinneligDeltaker.copy(
                status = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                ),
            )

            // Act
            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltaker,
                erDeltakerSluttdatoEndret = false,
            )

            // Assert
            DeltakerRepositoryTest.assertDeltakereAreEqual(
                deltakerRepository.get(opprinneligDeltaker.id).shouldBeSuccess(),
                oppdatertDeltaker,
            )

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
            // Arrange
            val gyldigFra = LocalDateTime.now().plusDays(3)

            val oppdatertDeltaker = opprinneligDeltaker.copy(
                status = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                    gyldigFra = gyldigFra,
                ),
            )

            // Act
            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltaker,
                erDeltakerSluttdatoEndret = false,
            )

            // Assert
            DeltakerRepositoryTest.assertDeltakereAreEqual(
                deltakerRepository.get(opprinneligDeltaker.id).shouldBeSuccess(),
                opprinneligDeltaker,
            )

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
        fun `har fremtidig status, mottar ny status med likt innhold - beholder eksisterende, deaktiverer fremtidig`() = runTest {
            // Arrange
            val fremtidigGyldigFra = LocalDateTime.now().plusDays(3)

            val oppdatertDeltakerFremtidigHarSluttet = opprinneligDeltaker.copy(
                status = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                    gyldigFra = fremtidigGyldigFra,
                ),
                sluttdato = LocalDate.now().plusDays(3),
            )

            val oppdatertDeltakerForlenget = opprinneligDeltaker.copy(
                status = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.DELTAR,
                    gyldigFra = LocalDateTime.now(),
                ),
                sluttdato = LocalDate.now().plusWeeks(8),
            )

            // Act
            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltakerFremtidigHarSluttet,
                erDeltakerSluttdatoEndret = true,
            )

            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltakerForlenget,
                erDeltakerSluttdatoEndret = true,
            )

            // Assert
            DeltakerRepositoryTest.assertDeltakereAreEqual(
                first = deltakerRepository.get(opprinneligDeltaker.id).shouldBeSuccess(),
                // status er uendret pga dedup — bruk opprinnelig status, men oppdatert sluttdato
                second = oppdatertDeltakerForlenget.copy(status = opprinneligDeltaker.status),
            )

            // opprinnelig DELTAR-status er fortsatt aktiv (innkommende status hadde likt innhold,
            // så ingen ny rad ble insertet)
            assertSoftly(DeltakerStatusRepository.get(opprinneligDeltaker.status.id)) {
                gyldigTil.shouldBeNull()
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            // fremtidig HAR_SLUTTET er deaktivert
            assertSoftly(DeltakerStatusRepository.get(oppdatertDeltakerFremtidigHarSluttet.status.id)) {
                gyldigTil.shouldNotBeNull()
                gyldigFra.toLocalDate() shouldBe fremtidigGyldigFra.toLocalDate()
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }
        }

        @Test
        fun `har fremtidig status, ny fremtidig status - insert ny fremtidig status, sletter forrige fremtidig status`() = runTest {
            // Arrange
            val gyldigFra = LocalDateTime.now().plusDays(3)
            val oppdatertDeltakerHarSluttet = opprinneligDeltaker.copy(
                status = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                    gyldigFra = gyldigFra,
                ),
                sluttdato = LocalDate.now().plusDays(3),
            )

            val oppdatertDeltakerHarSluttetNyArsak = opprinneligDeltaker.copy(
                status = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.UTDANNING,
                    gyldigFra = gyldigFra,
                ),
                sluttdato = LocalDate.now().plusDays(3),
            )

            // Act
            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltakerHarSluttet,
                erDeltakerSluttdatoEndret = true,
            )

            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltakerHarSluttetNyArsak,
                erDeltakerSluttdatoEndret = true,
            )

            // Assert
            DeltakerRepositoryTest.assertDeltakereAreEqual(
                first = deltakerRepository.get(opprinneligDeltaker.id).shouldBeSuccess(),
                second = opprinneligDeltaker.copy(sluttdato = oppdatertDeltakerHarSluttetNyArsak.sluttdato),
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
            // Arrange
            val opprinneligDeltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
                sluttdato = LocalDate.now().minusDays(2),
            )
            TestRepository.insert(opprinneligDeltaker)

            val nySluttdato = LocalDateTime.now().plusDays(3)
            val oppdatertDeltakerDeltar = opprinneligDeltaker.copy(
                status = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.DELTAR,
                    gyldigFra = LocalDateTime.now(),
                ),
                sluttdato = nySluttdato.toLocalDate(),
            )

            val nesteStatus = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.UTDANNING,
                gyldigFra = nySluttdato,
            )

            // Act
            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltakerDeltar,
                erDeltakerSluttdatoEndret = true,
                nesteStatus = nesteStatus,
            )

            // Assert
            DeltakerRepositoryTest.assertDeltakereAreEqual(
                deltakerRepository.get(opprinneligDeltaker.id).shouldBeSuccess(),
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
            // Arrange
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
                startdato = null,
                sluttdato = null,
            )
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(statusType = DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().minusDays(1),
                sluttdato = LocalDate.now().plusWeeks(2),
            )

            // Act
            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltaker,
                erDeltakerSluttdatoEndret = true,
            )

            // Assert
            val deltakereSomSkalHaStatusDeltar = deltakerRepository.skalHaStatusDeltar()

            deltakereSomSkalHaStatusDeltar.size shouldBe 1
            deltakereSomSkalHaStatusDeltar.first().id shouldBe deltaker.id
        }

        @Test
        fun `venter pa oppstart, mangler startdato - returnerer ikke deltaker`() = runTest {
            // Arrange
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
                startdato = null,
                sluttdato = null,
            )
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = null,
                sluttdato = null,
            )

            // Act
            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltaker,
                erDeltakerSluttdatoEndret = true,
            )

            // Assert
            val deltakereSomSkalHaStatusDeltar = deltakerRepository.skalHaStatusDeltar()
            deltakereSomSkalHaStatusDeltar.size shouldBe 0
        }
    }

    @Nested
    inner class GetAvsluttendeDeltakerStatuserForOppdatering {
        @Test
        fun `fremtidig HAR_SLUTTET-status skal ikke inkluderes`() = runTest {
            // Arrange
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker)

            val fremtidigStatus = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().plusDays(5),
            )

            // Act
            deltakerService.transactionalDeltakerUpsert(
                deltaker = deltaker.copy(status = fremtidigStatus),
                erDeltakerSluttdatoEndret = false,
            )

            // Assert
            val statuser = DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(
                setOf(deltaker.id),
            )
            statuser.shouldBeEmpty()
        }

        @Test
        fun `returnerer kun deltakerstatus for deltakere med aktiv DELTAR og gyldig avsluttende status`() = runTest {
            // Arrange
            val deltaker1 = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker1)

            val deltaker2 = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )
            TestRepository.insert(deltaker2)

            val status1 = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusDays(1),
            )

            // Act
            deltakerService.transactionalDeltakerUpsert(
                deltaker = deltaker1,
                erDeltakerSluttdatoEndret = false,
                nesteStatus = status1,
            )

            // Assert
            val statuser = DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(
                setOf(deltaker1.id, deltaker2.id),
            )
            statuser.size shouldBe 1
            statuser.first().deltakerId shouldBe deltaker1.id
        }

        @Test
        fun `henter avsluttende deltakerstatus for deltaker som har aktiv DELTAR-status og kommende HAR_SLUTTET-status`() = runTest {
            // Arrange
            val opprinneligDeltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
                sluttdato = LocalDate.now().minusDays(2),
            )
            TestRepository.insert(opprinneligDeltaker)

            val oppdatertDeltakerDeltar = opprinneligDeltaker.copy(
                status = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.DELTAR,
                    gyldigFra = LocalDateTime.now(),
                ),
                sluttdato = LocalDate.now().plusDays(3),
            )

            val nesteStatus = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.UTDANNING,
                gyldigFra = LocalDateTime.now().minusDays(1),
            )

            // Act
            deltakerService.transactionalDeltakerUpsert(
                deltaker = oppdatertDeltakerDeltar,
                erDeltakerSluttdatoEndret = opprinneligDeltaker.sluttdato != oppdatertDeltakerDeltar.sluttdato,
                nesteStatus = nesteStatus,
            )

            // Assert
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
            // Arrange
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            )
            TestRepository.insert(deltaker)

            val deltakerMedOppdatertStatus = deltaker.copy(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltakerMedOppdatertStatus,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = null,
            )
            TestRepository.insert(vedtak)

            val oppdatertDeltaker = deltakerMedOppdatertStatus.copy(
                vedtaksinformasjon = vedtak.tilVedtaksInformasjon(),
            )

            // Act
            val deltakerFraDb = deltakerService.upsertAndProduceDeltaker(
                deltaker = oppdatertDeltaker,
                erDeltakerSluttdatoEndret = deltaker.sluttdato != oppdatertDeltaker.sluttdato,
            )

            // Assert
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
            deltakerFraDb.vedtaksinformasjon?.opprettetAv shouldBe vedtak.opprettetAv

            outboxService.assertProduced<DeltakerKafkaPayload>(
                deltakerFraDb.id,
                Environment.DELTAKER_V2_TOPIC,
            )
            outboxService.assertProduced<DeltakerV1Dto>(deltakerFraDb.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltakerFraDb.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }

        @Test
        fun `upsertDeltaker - oppretter kladd - oppdaterer i db`() = runTest {
            // Arrange
            val arrangor = TestData.lagArrangor()
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltakerliste(arrangor = arrangor)
            TestRepository.insert(deltakerliste)

            val navBruker = TestData.lagNavBruker(
                navVeilederId = navAnsattInTest.id,
                navEnhetId = navEnhetInTest.id,
            )
            TestRepository.insert(navBruker)

            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                navBruker = navBruker,
                deltakerliste = deltakerliste,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            )

            // Act
            val deltakerFraDb = deltakerService.upsertAndProduceDeltaker(
                deltaker = deltaker,
                erDeltakerSluttdatoEndret = false,
            )

            // Assert
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.KLADD
            deltakerFraDb.vedtaksinformasjon shouldBe null
        }
    }

    @Nested
    inner class UpsertEndretDeltakerTests {
        @Test
        fun `upsertEndretDeltaker - ingen endring - upserter ikke, men godkjenner forslag`() = runTest {
            // Arrange
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                sluttdato = LocalDate.now().minusDays(2),
                sistEndret = LocalDateTime.now().minusDays(2),
            )
            TestRepository.insert(deltaker)

            val forslag = no.nav.amt.deltaker.utils.data.TestData
                .lagForslag(deltakerId = deltaker.id)
            forslagRepository.upsert(forslag)

            val endringsrequest = AvsluttDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                sluttdato = deltaker.sluttdato.shouldNotBeNull(),
                aarsak = null,
                begrunnelse = null,
                forslagId = forslag.id,
                harFullfort = null,
            )

            // Act
            deltakerService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = endringsrequest,
            )

            // Assert
            deltakerRepository.get(deltaker.id).shouldBeSuccess().sistEndret shouldBeCloseTo deltaker.sistEndret
            deltakerEndringRepository.getForDeltaker(deltaker.id).shouldBeEmpty()

            assertSoftly(forslagRepository.get(forslag.id).shouldBeSuccess()) {
                it.status.shouldBeInstanceOf<Forslag.Status.Godkjent>()
            }
        }

        @Test
        fun `upsertEndretDeltaker - avslutt i fremtiden - setter fremtidig HAR_SLUTTET`() = runTest {
            // Arrange
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusMonths(1),
            )
            TestRepository.insert(deltaker)

            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insert(vedtak)

            val endringsrequest = AvsluttDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                sluttdato = LocalDate.now().plusWeeks(1),
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = null,
                forslagId = null,
                harFullfort = null,
            )

            // Act
            val deltakerrespons = deltakerService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = endringsrequest,
            )

            // Assert
            deltakerrespons.status.type shouldBe DeltakerStatus.Type.DELTAR
            deltakerrespons.sluttdato shouldBe endringsrequest.sluttdato

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()
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
            // Arrange
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusMonths(1),
                deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                    tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                        tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
                    ),
                ),
            )
            TestRepository.insert(deltaker)

            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insert(vedtak)

            val endringsrequest = AvsluttDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                sluttdato = LocalDate.now().plusWeeks(1),
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = null,
                forslagId = null,
                harFullfort = null,
            )

            // Act
            val deltakerRespons = deltakerService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = endringsrequest,
            )

            // Assert
            deltakerRespons.status.type shouldBe DeltakerStatus.Type.DELTAR
            deltakerRespons.sluttdato shouldBe endringsrequest.sluttdato

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            assertSoftly(oppdatertDeltaker) {
                status.type shouldBe DeltakerStatus.Type.DELTAR
                sluttdato shouldBe endringsrequest.sluttdato
            }

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
            // Arrange
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusDays(2),
            )
            TestRepository.insert(deltaker)

            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insert(vedtak)

            val fremtidigHarSluttetStatus = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().plusDays(2),
            )
            DeltakerStatusRepository.lagreStatus(
                deltakerId = deltaker.id,
                deltakerStatus = fremtidigHarSluttetStatus,
            )

            val endringsrequest = ForlengDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                sluttdato = LocalDate.now().plusMonths(1),
                begrunnelse = "~begrunnelse~",
                forslagId = null,
            )

            // Act
            val deltakerRespons = deltakerService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = endringsrequest,
            )

            // Assert
            deltakerRespons.status.type shouldBe DeltakerStatus.Type.DELTAR
            deltakerRespons.sluttdato shouldBe endringsrequest.sluttdato

            assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
                status.type shouldBe DeltakerStatus.Type.DELTAR
                sluttdato shouldBe endringsrequest.sluttdato
            }

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
            // Arrange
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
                sluttdato = LocalDate.now().minusWeeks(1),
            )
            TestRepository.insert(deltaker)

            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insert(vedtak)

            val endringsrequest = AvsluttDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                sluttdato = LocalDate.now().plusWeeks(1),
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = null,
                forslagId = null,
                harFullfort = null,
            )

            // Act
            val deltakerrespons = deltakerService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = endringsrequest,
            )

            // Assert
            deltakerrespons.status.type shouldBe DeltakerStatus.Type.DELTAR
            deltakerrespons.sluttdato shouldBe endringsrequest.sluttdato

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            assertSoftly(oppdatertDeltaker) {
                status.type shouldBe DeltakerStatus.Type.DELTAR
                sluttdato shouldBe endringsrequest.sluttdato
            }

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
            // Arrange
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                startdato = LocalDate.now().minusMonths(3),
                sluttdato = LocalDate.now().plusMonths(3),
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
            )
            TestRepository.insertAll(deltaker, vedtak)

            val endringsrequest = DeltakelsesmengdeRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                deltakelsesprosent = 50,
                dagerPerUke = null,
                forslagId = null,
                begrunnelse = "begrunnelse",
                gyldigFra = LocalDate.now(),
            )

            // Act
            val resultat = deltakerService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = endringsrequest,
            )

            // Assert
            resultat.deltakelsesprosent shouldBe endringsrequest.deltakelsesprosent?.toFloat()
            resultat.dagerPerUke shouldBe null

            assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
                deltakelsesprosent shouldBe endringsrequest.deltakelsesprosent?.toFloat()
                dagerPerUke shouldBe null
            }

            val endring = deltakerEndringRepository.getForDeltaker(deltaker.id).first()
            assertSoftly(endring) {
                endretAv shouldBe navAnsattInTest.id
                endretAvEnhet shouldBe navEnhetInTest.id

                assertSoftly(it.endring.shouldBeInstanceOf<DeltakerEndring.Endring.EndreDeltakelsesmengde>()) {
                    deltakelsesprosent shouldBe endringsrequest.deltakelsesprosent
                    dagerPerUke shouldBe endringsrequest.dagerPerUke
                }
            }

            outboxService.assertProducedHendelse<HendelseType.EndreDeltakelsesmengde>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }

        @Test
        fun `upsertEndretDeltaker - fremtidig deltakelsesmengde - upserter endring, endrer ikke deltaker`() = runTest {
            // Arrange
            val deltaker = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltaker()
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
            )
            TestRepository.insertAll(deltaker, vedtak)

            val endringsrequest = DeltakelsesmengdeRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                deltakelsesprosent = 50,
                dagerPerUke = null,
                forslagId = null,
                begrunnelse = "begrunnelse",
                gyldigFra = LocalDate.now().plusDays(1),
            )

            // Act
            val resultat = deltakerService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = endringsrequest,
            )

            // Assert
            resultat.deltakelsesprosent shouldBe deltaker.deltakelsesprosent
            resultat.dagerPerUke shouldBe deltaker.dagerPerUke
        }

        @Test
        fun `upsertEndretDeltaker - endret datoer - upserter endring`() = runTest {
            // Arrange
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusDays(1),
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
            )
            TestRepository.insertAll(deltaker, vedtak)

            val endringsrequest = StartdatoRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                startdato = LocalDate.now().minusWeeks(1),
                sluttdato = LocalDate.now().plusWeeks(4),
                begrunnelse = null,
                forslagId = null,
            )

            // Act
            val resultat = deltakerService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = endringsrequest,
            )

            // Assert
            assertSoftly(resultat) {
                startdato shouldBe endringsrequest.startdato
                sluttdato shouldBe endringsrequest.sluttdato
                status.type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
                status.type shouldBe DeltakerStatus.Type.DELTAR
                startdato shouldBe endringsrequest.startdato
                sluttdato shouldBe endringsrequest.sluttdato
            }

            assertSoftly(deltakerEndringRepository.getForDeltaker(deltaker.id).first()) {
                endretAv shouldBe navAnsattInTest.id
                endretAvEnhet shouldBe navEnhetInTest.id

                assertSoftly(it.endring.shouldBeInstanceOf<DeltakerEndring.Endring.EndreStartdato>()) {
                    startdato shouldBe endringsrequest.startdato
                    sluttdato shouldBe endringsrequest.sluttdato
                }
            }

            outboxService.assertProducedHendelse<HendelseType.EndreStartdato>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
        }

        @Test
        fun `upsertEndretDeltaker - endret startdato - upserter ny dato og status`() = runTest {
            // Arrange
            val deltakersSluttdato = LocalDate.now().plusWeeks(3)
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().plusDays(3),
                sluttdato = deltakersSluttdato,
            )

            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
            )
            TestRepository.insertAll(deltaker, vedtak)

            val endringsrequest = StartdatoRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                startdato = LocalDate.now().minusWeeks(2),
                sluttdato = deltakersSluttdato,
                begrunnelse = null,
                forslagId = null,
            )

            // Act
            val resultat = deltakerService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = endringsrequest,
            )

            // Assert
            assertSoftly(resultat) {
                startdato shouldBe endringsrequest.startdato
                sluttdato shouldBe deltakersSluttdato
                status.type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
                status.type shouldBe DeltakerStatus.Type.DELTAR
                startdato shouldBe endringsrequest.startdato
                sluttdato shouldBe deltakersSluttdato
            }

            assertSoftly(deltakerEndringRepository.getForDeltaker(deltaker.id).first()) {
                endretAv shouldBe navAnsattInTest.id
                endretAvEnhet shouldBe navEnhetInTest.id

                assertSoftly(it.endring.shouldBeInstanceOf<DeltakerEndring.Endring.EndreStartdato>()) {
                    startdato shouldBe endringsrequest.startdato
                    sluttdato shouldBe deltakersSluttdato
                }
            }

            outboxService.assertProducedHendelse<HendelseType.EndreStartdato>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }

        @Test
        fun `upsertEndretDeltaker - reaktiver - kladd slettes`() = runTest {
            // Arrange
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
                startdato = LocalDate.now().plusDays(3),
            )
            val kladd = deltaker.copy(
                id = UUID.randomUUID(),
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            )

            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
            )
            TestRepository.insertAll(deltaker, kladd, vedtak)

            val endringsrequest = ReaktiverDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                begrunnelse = "~begrunnelse~",
            )

            // Act
            val resultat = deltakerService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = endringsrequest,
            )

            // Assert
            resultat.status.type shouldBe DeltakerStatus.Type.SOKT_INN
            deltakerRepository.get(kladd.id).shouldBeFailure()
        }

        @Test
        fun `upsertEndretDeltaker - aktiv oppfolgingsperiode, endring som krever oppfolging - utfører endring`() = runTest {
            // Arrange — bruker med aktiv oppfølgingsperiode (default fra lagNavBruker)
            val navBruker = TestData.lagNavBruker(
                oppfolgingsperioder = listOf(
                    TestData.lagOppfolgingsperiode(
                        startdato = LocalDateTime.now().minusMonths(2),
                        sluttdato = null,
                    ),
                ),
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                navBruker = navBruker,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                bakgrunnsinformasjon = "Gammel informasjon",
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)

            // BakgrunnsinformasjonRequest krever aktiv oppfølging
            val endringsrequest = BakgrunnsinformasjonRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                bakgrunnsinformasjon = "Ny informasjon",
            )

            // Act
            val resultat = deltakerService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = endringsrequest,
            )

            // Assert
            resultat.bakgrunnsinformasjon shouldBe "Ny informasjon"
            deltakerRepository.get(deltaker.id).shouldBeSuccess().bakgrunnsinformasjon shouldBe "Ny informasjon"
            deltakerEndringRepository
                .getForDeltaker(deltaker.id)
                .first()
                .endring
                .shouldBeInstanceOf<DeltakerEndring.Endring.EndreBakgrunnsinformasjon>()
        }

        @Test
        fun `upsertEndretDeltaker - ingen aktiv oppfolgingsperiode, endring som krever oppfolging - kaster exception`() = runTest {
            // Arrange — bruker med utløpt oppfølgingsperiode
            val navBruker = TestData.lagNavBruker(
                oppfolgingsperioder = listOf(
                    TestData.lagOppfolgingsperiode(
                        startdato = LocalDateTime.now().minusMonths(6),
                        sluttdato = LocalDateTime.now().minusDays(2),
                    ),
                ),
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                navBruker = navBruker,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                bakgrunnsinformasjon = "Gammel informasjon",
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)

            val endringsrequest = BakgrunnsinformasjonRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                bakgrunnsinformasjon = "Ny informasjon",
            )

            // Act & Assert
            val exception = assertThrows<IllegalArgumentException> {
                deltakerService.upsertEndretDeltaker(
                    deltakerId = deltaker.id,
                    endringRequest = endringsrequest,
                )
            }
            exception.message shouldBe
                "Kan ikke utføre endring BakgrunnsinformasjonRequest på deltaker ${deltaker.id} uten aktiv oppfølgingsperiode"

            // deltaker uendret i databasen
            deltakerRepository.get(deltaker.id).shouldBeSuccess().bakgrunnsinformasjon shouldBe "Gammel informasjon"
            deltakerEndringRepository.getForDeltaker(deltaker.id).shouldBeEmpty()
        }

        @Test
        fun `upsertEndretDeltaker - ingen aktiv oppfolgingsperiode, endring som kan iverksettes uten - utfører endring`() = runTest {
            // Arrange — bruker uten aktiv oppfølgingsperiode, men endring (AvsluttDeltakelseRequest)
            // er tillatt uten oppfølging
            val navBruker = TestData.lagNavBruker(
                oppfolgingsperioder = listOf(
                    TestData.lagOppfolgingsperiode(
                        startdato = LocalDateTime.now().minusMonths(6),
                        sluttdato = LocalDateTime.now().minusDays(2),
                    ),
                ),
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                navBruker = navBruker,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusMonths(1),
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)

            val endringsrequest = AvsluttDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                sluttdato = LocalDate.now().plusWeeks(1),
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = null,
                forslagId = null,
                harFullfort = null,
            )

            // Act
            val resultat = deltakerService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = endringsrequest,
            )

            // Assert
            resultat.sluttdato shouldBe endringsrequest.sluttdato
            deltakerRepository.get(deltaker.id).shouldBeSuccess().sluttdato shouldBe endringsrequest.sluttdato
            deltakerEndringRepository
                .getForDeltaker(deltaker.id)
                .first()
                .endring
                .shouldBeInstanceOf<DeltakerEndring.Endring.AvsluttDeltakelse>()
        }
    }

    @Nested
    inner class UpsertEndretDeltakereTests {
        @Test
        fun `upsertEndretDeltakere - sett på venteliste - upserter endring`() = runTest {
            // Arrange
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltaker(deltakerliste = deltakerliste)
            val deltaker2 = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltaker(deltakerliste = deltakerliste)
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val innsokt = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker2.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )

            TestRepository.insertAll(deltaker, deltaker2, innsokt, innsokt2)

            // Act
            val endredeDeltakere = tiltaksansvarligService.oppdaterDeltakere(
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.SettPaaVenteliste,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
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

            outboxService.assertProducedHendelse<HendelseType.SettPaaVenteliste>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )

            // deltaker2
            outboxService.assertProduced<DeltakerKafkaPayload>(
                expectedKey = deltaker2.id,
                expectedTopic = Environment.DELTAKER_V2_TOPIC,
            )
            outboxService.assertProduced<DeltakerV1Dto>(
                expectedKey = deltaker2.id,
                expectedTopic = Environment.DELTAKER_V1_TOPIC,
            )
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                expectedKey = deltaker2.id,
                expectedTopic = Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }

        @Test
        fun `upsertEndretDeltakere - tildel plass feiler på upsert - ruller tilbake endringer på samme deltaker`() = runTest {
            // Arrange
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
            )
            val deltaker1Id = UUID.randomUUID()
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerId = deltaker1Id,
                opprettetAvEnhet = navEnhetInTest,
                opprettetAv = navAnsattInTest,
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                id = deltaker1Id,
                deltakerliste = deltakerliste,
                vedtaksinformasjon = vedtak.tilVedtaksInformasjon(),
            )

            val deltaker2 = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltaker(deltakerliste = deltakerliste)
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val innsokt = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker2.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(deltaker, deltaker2, innsokt, innsokt2, vedtak)

            // Act
            val endredeDeltakereResults = tiltaksansvarligService.oppdaterDeltakere(
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.TildelPlass,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
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

            outboxService.assertProducedHendelse<HendelseType.TildelPlass>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }

        @Test
        fun `upsertEndretDeltakere - tildel plass - upserter endring, bruker deltakerliste sin start og sluttdato`() = runTest {
            // Arrange
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
                startDato = LocalDate.now().plusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val deltaker2 = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            val vedtak2 = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker2,
                deltakerId = deltaker2.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val innsokt = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker2.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(
                deltaker,
                deltaker2,
                innsokt,
                innsokt2,
                vedtak,
                vedtak2,
            )

            // Act
            val endredeDeltakere = tiltaksansvarligService.oppdaterDeltakere(
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.TildelPlass,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            endredeDeltakere.size shouldBe 2

            assertSoftly(endredeDeltakere.first { it.deltaker.id == deltaker.id }.deltaker) {
                status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                startdato shouldBe deltakerliste.startDato
                sluttdato shouldBe deltakerliste.sluttDato
            }

            assertSoftly(endredeDeltakere.first { it.deltaker.id == deltaker2.id }.deltaker) {
                status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                startdato shouldBe deltakerliste.startDato
                sluttdato shouldBe deltakerliste.sluttDato
            }

            val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
            historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            outboxService.assertProducedHendelse<HendelseType.TildelPlass>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )

            outboxService.assertProduced<DeltakerKafkaPayload>(
                deltaker2.id,
                Environment.DELTAKER_V2_TOPIC,
            )
            outboxService.assertProduced<DeltakerV1Dto>(deltaker2.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker2.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }

        @Test
        fun `upsertEndretDeltakere - tildel plass - upserter endring, dato passert får start og sluttdato null`() = runTest {
            // Arrange
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
                startDato = LocalDate.now().minusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val deltaker2 = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            val vedtak2 = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker2,
                deltakerId = deltaker2.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            val innsokt = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker2.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(
                deltaker,
                deltaker2,
                innsokt,
                innsokt2,
                vedtak,
                vedtak2,
            )

            // Act
            val endredeDeltakere = tiltaksansvarligService.oppdaterDeltakere(
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.TildelPlass,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            endredeDeltakere.size shouldBe 2

            assertSoftly(endredeDeltakere.first { it.deltaker.id == deltaker.id }.deltaker) {
                status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                startdato shouldBe null
                sluttdato shouldBe null
            }

            assertSoftly(endredeDeltakere.first { it.deltaker.id == deltaker2.id }.deltaker) {
                status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                startdato shouldBe null
                sluttdato shouldBe null
            }

            val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
            historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            outboxService.assertProducedHendelse<HendelseType.TildelPlass>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )

            outboxService.assertProduced<DeltakerKafkaPayload>(
                deltaker2.id,
                Environment.DELTAKER_V2_TOPIC,
            )
            outboxService.assertProduced<DeltakerV1Dto>(deltaker2.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker2.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }

        @Test
        fun `upsertEndretDeltakere - tildel plass feiler på siste deltaker - ruller tilbake en deltaker`() = runTest {
            // Arrange
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
                startDato = LocalDate.now().plusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val deltakerInsert = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val deltaker2Insert = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltakerInsert,
                deltakerId = deltakerInsert.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )

            val deltakerIder = setOf(deltakerInsert.id, deltaker2Insert.id)
            val innsokt = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltakerInsert.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker2Insert.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(
                deltakerInsert,
                deltaker2Insert,
                innsokt,
                innsokt2,
                vedtak,
            )

            // Act
            val deltakereResult = tiltaksansvarligService.oppdaterDeltakere(
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.TildelPlass,
                endretAvIdent = navAnsattInTest.navIdent,
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

            outboxService.assertProducedHendelse<HendelseType.TildelPlass>(deltakerInsert.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(
                deltakerInsert.id,
                Environment.DELTAKER_V2_TOPIC,
            )
            outboxService.assertProduced<DeltakerV1Dto>(deltakerInsert.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltakerInsert.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }

        @Test
        fun `upsertEndretDeltakere - del med arrangør - inserter endring og returnerer endret deltaker`() = runTest {
            // Arrange
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode =
                        Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
                ),
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )
            val deltaker2 = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
                status = no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )

            val deltakerIder = setOf(deltaker.id, deltaker2.id)
            val innsokt = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            val innsokt2 = no.nav.amt.deltaker.utils.data.TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker2.id,
                innsoktAv = navAnsattInTest.id,
                innsoktAvEnhet = navEnhetInTest.id,
            )
            TestRepository.insertAll(
                deltaker,
                deltaker2,
                innsokt,
                innsokt2,
            )

            // Act
            val endredeDeltakere = tiltaksansvarligService.oppdaterDeltakere(
                deltakerIder = deltakerIder,
                endringsType = EndringFraTiltakskoordinator.DelMedArrangor,
                endretAvIdent = navAnsattInTest.navIdent,
            )

            // Assert
            endredeDeltakere.size shouldBe 2

            assertSoftly(endredeDeltakere.first { it.deltaker.id == deltaker.id }.deltaker) {
                status.type shouldBe DeltakerStatus.Type.SOKT_INN
                erManueltDeltMedArrangor shouldBe true
            }

            assertSoftly(endredeDeltakere.first { it.deltaker.id == deltaker2.id }.deltaker) {
                status.type shouldBe DeltakerStatus.Type.SOKT_INN
                erManueltDeltMedArrangor shouldBe true
            }

            val historikk1 = deltakerHistorikkService.getForDeltaker(deltaker.id)
            historikk1.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            val historikk2 = deltakerHistorikkService.getForDeltaker(deltaker2.id)
            historikk2.filterIsInstance<DeltakerHistorikk.EndringFraTiltakskoordinator>().size shouldBe 1

            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )

            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker2.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker2.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker2.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }
    }

    @Nested
    inner class TransactionalDeltakerUpsertTests {
        @Test
        fun `ny deltaker - returnerer deltaker`() = runTest {
            // Arrange
            val expectedDeltaker = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltaker()
            TestRepository.insertAll(expectedDeltaker.deltakerliste, expectedDeltaker.navBruker)

            // Act
            val result = deltakerService.transactionalDeltakerUpsert(
                deltaker = expectedDeltaker,
                erDeltakerSluttdatoEndret = false,
            )

            // Assert
            result.isSuccess.shouldBeTrue()

            val deltakerFromDb = deltakerRepository.get(expectedDeltaker.id).shouldBeSuccess()
            DeltakerRepositoryTest.assertDeltakereAreEqual(deltakerFromDb, expectedDeltaker)
        }

        @Test
        fun `ny deltaker - ruller tilbake alle endringer`() = runTest {
            // Arrange
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
                startDato = LocalDate.now().plusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val expectedDeltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            TestRepository.insertAll(expectedDeltaker.deltakerliste, expectedDeltaker.navBruker)

            // Act
            val upsertResult = deltakerService.transactionalDeltakerUpsert(
                deltaker = expectedDeltaker,
                erDeltakerSluttdatoEndret = false,
            ) {
                throw RuntimeException("Feiler")
            }

            // Assert
            upsertResult.isFailure shouldBe true
            val throwable = upsertResult.exceptionOrNull()
            throwable.shouldNotBeNull()
            throwable.message shouldBe "Feiler"

            deltakerRepository.get(expectedDeltaker.id).shouldBeFailure()
        }

        @Test
        fun `ny status, siste insert feiler - ruller tilbake alle endringer`() = runTest {
            // Arrange
            val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData
                    .lagTiltakstype(Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
                startDato = LocalDate.now().plusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            TestRepository.insertAll(
                deltaker,
                vedtak,
            )

            // Act
            val upsertResult = deltakerService.transactionalDeltakerUpsert(
                deltaker = deltaker.copy(status = DeltakerUtils.nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)),
                erDeltakerSluttdatoEndret = false,
            ) {
                throw RuntimeException("Feiler")
            }

            // Assert
            assertSoftly(upsertResult.exceptionOrNull().shouldNotBeNull()) {
                message shouldBe "Feiler"
            }

            val deltakerFromDb = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            deltakerFromDb.status.type shouldBe deltaker.status.type

            val insertedEndring = endringFraTiltakskoordinatorRepository.getForDeltaker(deltaker.id)
            insertedEndring shouldBe emptyList()
        }
    }

    @Test
    fun `giAvslag - deltaker får riktig status og historikk`() = runTest {
        with(EndringFraTiltakskoordinatorCtx()) {
            // Arrange
            medInnsok()

            val avslag = EndringFraTiltakskoordinator.Avslag(
                aarsak = EndringFraTiltakskoordinator.Avslag.Aarsak(
                    type = EndringFraTiltakskoordinator.Avslag.Aarsak.Type.KURS_FULLT,
                    beskrivelse = null,
                ),
                begrunnelse = "Fordi...",
            )

            // Act
            val deltaker = tiltaksansvarligService.giAvslag(
                deltakerId = deltaker.id,
                avslag = avslag,
                endretAv = navAnsatt.navIdent,
            )

            // Assert*
            val endringer = endringFraTiltakskoordinatorRepository.getForDeltaker(deltaker.id)
            endringer.size shouldBe 1
            (endringer.first().endring is EndringFraTiltakskoordinator.Avslag) shouldBe true

            assertSoftly(deltaker) {
                status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
                status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.KURS_FULLT
                startdato shouldBe null
                sluttdato shouldBe null
            }

            outboxService.assertProducedHendelse<HendelseType.Avslag>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(
                deltaker.id,
                Environment.DELTAKER_EKSTERN_V1_TOPIC,
            )
        }
    }

    @Test
    fun `produserDeltakereForPerson - deltaker finnes - publiserer til kafka`() = runTest {
        // Arrange
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            status = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )
        TestRepository.insert(deltaker)

        val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsattInTest,
            opprettetAvEnhet = navEnhetInTest,
            fattet = LocalDateTime.now(),
        )
        TestRepository.insert(vedtak)

        // Act
        deltakerService.produserDeltakereForPerson(personident = deltaker.navBruker.personident)

        // Assert
        outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
        outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
        outboxService.assertProduced<DeltakerEksternV1Dto>(deltaker.id, Environment.DELTAKER_EKSTERN_V1_TOPIC)
    }

    @Test
    fun `oppdaterSistBesokt - produserer hendelse`() {
        // Arrange
        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        TestRepository.insert(deltaker)

        // Act
        deltakerService.oppdaterSistBesokt(
            deltakerId = deltaker.id,
            sistBesokt = ZonedDateTime.now(),
        )

        outboxService.assertProducedHendelse<HendelseType.DeltakerSistBesokt>(deltaker.id)
    }

    @Test
    fun `feilregistrerDeltaker - deltaker feilregistreres og oppdatert deltaker produseres`() = runTest {
        // Arrange
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            status = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )
        val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsattInTest,
            opprettetAvEnhet = navEnhetInTest,
            sistEndretAv = navAnsattInTest,
            sistEndretAvEnhet = navEnhetInTest,
        )
        val deltakerEndring = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsattInTest.id,
            endretAvEnhet = navEnhetInTest.id,
        )
        TestRepository.insertAll(deltaker, vedtak, deltakerEndring)

        // Act
        deltakerService.feilregistrerDeltaker(deltakerId = deltaker.id)

        // Assert
        assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
            status.type shouldBe DeltakerStatus.Type.FEILREGISTRERT
            startdato shouldBe null
            sluttdato shouldBe null
            dagerPerUke shouldBe null
            deltakelsesprosent shouldBe null
            bakgrunnsinformasjon shouldBe null
            deltakelsesinnhold shouldBe null
        }

        outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
        outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
        outboxService.assertProduced<DeltakerEksternV1Dto>(deltaker.id, Environment.DELTAKER_EKSTERN_V1_TOPIC)
    }

    companion object {
        infix fun Deltaker.shouldBeComparableWith(expected: Deltaker) {
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
                status = status.copy(id = expected.status.id, opprettet = statusOpprettetDay, gyldigFra = gyldigFra),
                opprettet = now,
                vedtaksinformasjon = vedtaksinformasjon?.copy(
                    fattet = this.vedtaksinformasjon.fattet?.atStartOfDay(),
                    sistEndret = this.vedtaksinformasjon.sistEndret.atStartOfDay(),
                ),
            ) shouldBe expected.copy(
                sistEndret = expected.sistEndret.atStartOfDay(),
                status = expected.status.copy(
                    id = expected.status.id,
                    opprettet = expected.status.opprettet.atStartOfDay(),
                    gyldigFra = expected.status.gyldigFra.atStartOfDay(),
                ),
                opprettet = now,
                vedtaksinformasjon = expected.vedtaksinformasjon?.let { ev ->
                    vedtaksinformasjon?.copy(
                        fattet = ev.fattet?.atStartOfDay(),
                        sistEndret = ev.sistEndret.atStartOfDay(),
                    )
                },
            )
        }
    }
}
