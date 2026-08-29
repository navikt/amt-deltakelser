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
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.kafka.payload.DeltakerEksternV1Dto
import no.nav.amt.deltaker.kafka.payload.DeltakerV1Dto
import no.nav.amt.deltaker.repository.DeltakerRepositoryTest
import no.nav.amt.deltaker.repository.DeltakerStatusRepository
import no.nav.amt.deltaker.repository.dbo.DeltakerStatusMedDeltakerId
import no.nav.amt.deltaker.utils.DeltakerUtils
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.assertProduced
import no.nav.amt.deltaker.utils.assertProducedHendelse
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagEndringFraArrangor
import no.nav.amt.deltaker.utils.data.TestData.lagForslag
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.testing.utils.TestData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

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
        val opprinneligDeltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )

        @BeforeEach
        fun setup() {
            TestRepository.insert(opprinneligDeltaker)
        }

        @Test
        fun `ny status - inserter ny status og deaktiverer gammel`() = runTest {
            // Arrange
            val oppdatertDeltaker = opprinneligDeltaker.copy(
                status = lagDeltakerStatus(
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

            assertSoftly(TestRepository.getDeltakerStatus(opprinneligDeltaker.status.id)) {
                gyldigTil shouldNotBe null
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(TestRepository.getDeltakerStatus(oppdatertDeltaker.status.id)) {
                gyldigTil shouldBe null
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }
        }

        @Test
        fun `ny status gyldig i fremtid - inserter ny status, deaktiverer ikke gammel`() = runTest {
            // Arrange
            val gyldigFra = LocalDateTime.now().plusDays(3)

            val oppdatertDeltaker = opprinneligDeltaker.copy(
                status = lagDeltakerStatus(
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

            assertSoftly(TestRepository.getDeltakerStatus(opprinneligDeltaker.status.id)) {
                gyldigTil shouldBe null
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(TestRepository.getDeltakerStatus(oppdatertDeltaker.status.id)) {
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
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                    gyldigFra = fremtidigGyldigFra,
                ),
                sluttdato = LocalDate.now().plusDays(3),
            )

            val oppdatertDeltakerForlenget = opprinneligDeltaker.copy(
                status = lagDeltakerStatus(
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
            assertSoftly(TestRepository.getDeltakerStatus(opprinneligDeltaker.status.id)) {
                gyldigTil.shouldBeNull()
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            // fremtidig HAR_SLUTTET er deaktivert
            assertSoftly(TestRepository.getDeltakerStatus(oppdatertDeltakerFremtidigHarSluttet.status.id)) {
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
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
                    gyldigFra = gyldigFra,
                ),
                sluttdato = LocalDate.now().plusDays(3),
            )

            val oppdatertDeltakerHarSluttetNyArsak = opprinneligDeltaker.copy(
                status = lagDeltakerStatus(
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
                TestRepository.getDeltakerStatus(oppdatertDeltakerHarSluttet.status.id)
            }

            assertSoftly(TestRepository.getDeltakerStatus(opprinneligDeltaker.status.id)) {
                gyldigTil shouldBe null
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(TestRepository.getDeltakerStatus(oppdatertDeltakerHarSluttetNyArsak.status.id)) {
                gyldigTil shouldBe null
                gyldigFra shouldBeCloseTo gyldigFra
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                aarsak.shouldNotBeNull().type shouldBe DeltakerStatus.Aarsak.Type.UTDANNING
            }
        }

        @Test
        fun `har sluttet til deltar, angitt neste status - oppdaterer status, insert neste fremtidige status`() = runTest {
            // Arrange
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

            assertSoftly(TestRepository.getDeltakerStatus(opprinneligDeltaker.status.id)) {
                gyldigTil shouldBeCloseTo LocalDateTime.now()
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }

            assertSoftly(TestRepository.getDeltakerStatus(oppdatertDeltakerDeltar.status.id)) {
                gyldigTil.shouldBeNull()
                gyldigFra shouldBeCloseTo LocalDateTime.now()
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(TestRepository.getDeltakerStatus(nesteStatus.id)) {
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
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker)

            val fremtidigStatus = lagDeltakerStatus(
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
            val deltaker1 = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker1)

            val deltaker2 = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )
            TestRepository.insert(deltaker2)

            val status1 = lagDeltakerStatus(
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
            val deltakerliste = lagDeltakerliste(arrangor = arrangor)
            TestRepository.insert(deltakerliste)

            val navBruker = TestData.lagNavBruker(
                navVeilederId = navAnsattInTest.id,
                navEnhetId = navEnhetInTest.id,
            )
            TestRepository.insert(navBruker)

            val deltaker = lagDeltaker(
                navBruker = navBruker,
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
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
    inner class TransactionalDeltakerUpsertTests {
        @Test
        fun `ny deltaker - returnerer deltaker`() = runTest {
            // Arrange
            val expectedDeltaker = lagDeltaker()
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
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(
                    tiltakskode = Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
                ),
                startDato = LocalDate.now().plusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val expectedDeltaker = lagDeltaker(
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
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING),
                startDato = LocalDate.now().plusDays(2),
                sluttDato = LocalDate.now().plusDays(30),
            )
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                startdato = null,
                sluttdato = null,
            )
            val vedtak = lagVedtak(
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

    @Nested
    inner class ProduserDeltakereForPerson {
        @Test
        fun `publiserer deltaker til kafka`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker)
            val vedtak = lagVedtak(
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
    }

    @Nested
    inner class OppdaterSistBesokt {
        @Test
        fun `produserer hendelse`() {
            // Arrange
            val deltaker = lagDeltaker()
            TestRepository.insert(deltaker)

            // Act
            deltakerService.oppdaterSistBesokt(
                deltakerId = deltaker.id,
                sistBesokt = ZonedDateTime.now(),
            )

            // Assert
            outboxService.assertProducedHendelse<HendelseType.DeltakerSistBesokt>(deltaker.id)
        }
    }

    @Nested
    inner class FeilregistrerDeltaker {
        @Test
        fun `feilregistrerer deltaker og produserer til kafka`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                sistEndretAv = navAnsattInTest,
                sistEndretAvEnhet = navEnhetInTest,
            )
            val deltakerEndring = lagDeltakerEndring(
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

        @Test
        fun `kladd kaster feil`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            )
            TestRepository.insert(deltaker)

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                deltakerService.feilregistrerDeltaker(deltakerId = deltaker.id)
            }.message shouldBe "Kan ikke feilregistrere deltaker-kladd"
        }
    }

    @Nested
    inner class DeleteDeltaker {
        @Test
        fun `sletter deltaker og alle relaterte data`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            )
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
            )
            val deltakerEndring = lagDeltakerEndring(
                deltakerId = deltaker.id,
                endretAv = navAnsattInTest.id,
                endretAvEnhet = navEnhetInTest.id,
            )
            val forslag = lagForslag(deltakerId = deltaker.id)
            val endringFraArrangor = lagEndringFraArrangor(deltakerId = deltaker.id)
            TestRepository.insertAll(deltaker, vedtak, deltakerEndring, forslag, endringFraArrangor)

            // Act
            deltakerService.deleteDeltaker(deltaker.id)

            // Assert
            deltakerRepository.get(deltaker.id).shouldBeFailure()
            deltakerEndringRepository.getForDeltaker(deltaker.id).shouldBeEmpty()
            forslagRepository.getForDeltaker(deltaker.id).shouldBeEmpty()
            endringFraArrangorRepository.getForDeltaker(deltaker.id).shouldBeEmpty()
            endringFraTiltakskoordinatorRepository.getForDeltaker(deltaker.id).shouldBeEmpty()
        }
    }

    @Nested
    inner class AvsluttDeltakere {
        @Test
        fun `deltaker med passert sluttdato får status HAR_SLUTTET`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().minusDays(1),
            )
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)

            // Act
            deltakerService.avsluttDeltakere(listOf(deltaker))

            // Assert
            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            oppdatertDeltaker.sluttdato shouldBe deltaker.sluttdato
        }

        @Test
        fun `deltaker som venter på oppstart får status IKKE_AKTUELL`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().plusDays(5),
                sluttdato = LocalDate.now().plusWeeks(4),
            )
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)

            // Act
            deltakerService.avsluttDeltakere(listOf(deltaker))

            // Assert
            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
            oppdatertDeltaker.startdato shouldBe null
            oppdatertDeltaker.sluttdato shouldBe null
        }

        @Test
        fun `utkast på avsluttet deltakerliste får status AVBRUTT_UTKAST`() = runTest {
            // Arrange
            val deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.OPPFOLGING),
                status = GjennomforingStatusType.AVSLUTTET,
            )
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
                startdato = null,
                sluttdato = null,
            )
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
            )
            TestRepository.insertAll(deltaker, vedtak)

            // Act
            deltakerService.avsluttDeltakere(listOf(deltaker))

            // Assert
            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.AVBRUTT_UTKAST
            outboxService.assertProducedHendelse<HendelseType.AvbrytUtkast>(deltaker.id)
        }
    }

    @Nested
    inner class OppdaterDeltakerStatuser {
        @Test
        fun `ingen deltakere å oppdatere gjør ingenting`() = runTest {
            // Arrange — ingen deltakere i databasen

            // Act
            deltakerService.oppdaterDeltakerStatuser()

            // Assert — ingen feil, ingen Kafka-meldinger
        }

        @Test
        fun `deltaker med passert sluttdato avsluttes`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().minusDays(1),
            )
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)

            // Act
            deltakerService.oppdaterDeltakerStatuser()

            // Assert
            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
        }

        @Test
        fun `deltaker som venter på oppstart med passert startdato får status DELTAR`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().minusDays(1),
                sluttdato = LocalDate.now().plusWeeks(4),
            )
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)

            // Act
            deltakerService.oppdaterDeltakerStatuser()

            // Assert
            assertSoftly(DeltakerStatusRepository.getGjeldendeDeltakerStatus(deltaker.id).shouldNotBeNull()) {
                type shouldBe DeltakerStatus.Type.DELTAR
            }
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
        }
    }

    @Nested
    inner class ValiderIkkeFeilregistrert {
        @Test
        fun `feilregistrert deltaker kaster feil`() {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.FEILREGISTRERT),
            )

            // Act & Assert
            assertThrows<IllegalArgumentException> {
                DeltakerService.validerIkkeFeilregistrert(deltaker)
            }
        }

        @Test
        fun `aktiv deltaker passerer validering`() {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )

            // Act & Assert — ingen exception
            DeltakerService.validerIkkeFeilregistrert(deltaker)
        }
    }
}
