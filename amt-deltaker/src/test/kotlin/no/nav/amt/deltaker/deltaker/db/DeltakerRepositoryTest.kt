package no.nav.amt.deltaker.deltaker.db

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate

class DeltakerRepositoryTest {
    private val deltakerRepository = DeltakerRepository()

    @Nested
    inner class GetDeltakereForDeltakerlisteTests {
        val deltakerliste = lagDeltakerliste()

        @Test
        fun `skal returnere failure hvis ingen deltakere`() {
            TestRepository.insert(deltakerliste)

            val kladdResult = deltakerRepository.getKladdForDeltakerliste(
                deltakerlisteId = deltakerliste.id,
                personident = "~personident~",
            )

            kladdResult.shouldBeFailure()
        }

        @Test
        fun `skal returnere success hvis kladd finnes`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
                deltakerliste = deltakerliste,
            )
            TestRepository.insert(deltaker)

            val kladdResult = deltakerRepository.getKladdForDeltakerliste(
                deltakerlisteId = deltakerliste.id,
                personident = deltaker.navBruker.personident,
            )

            kladdResult.shouldBeSuccess()
        }
    }

    @Nested
    inner class GetDeltakereForAvsluttetDeltakerlisteTests {
        val deltakerlisteInTest = lagDeltakerliste()

        @BeforeEach
        fun setup() = TestRepository.insert(deltakerlisteInTest)

        @Test
        fun `skal returnere tom liste hvis ingen deltakere`() {
            val deltakere = deltakerRepository.getDeltakereForAvsluttetDeltakerliste(deltakerlisteInTest.id)

            deltakere.shouldBeEmpty()
        }

        @Test
        fun `skal filtrere bort deltakere med status KLADD`() {
            TestRepository.insert(
                lagDeltaker(
                    status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
                    deltakerliste = deltakerlisteInTest,
                ),
            )

            val deltakere = deltakerRepository.getDeltakereForAvsluttetDeltakerliste(deltakerlisteInTest.id)

            deltakere.shouldBeEmpty()
        }

        @Test
        fun `skal returnere deltakere med status DELTAR`() {
            TestRepository.insert(
                lagDeltaker(
                    status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                    deltakerliste = deltakerlisteInTest,
                ),
            )

            val deltakere = deltakerRepository.getDeltakereForAvsluttetDeltakerliste(deltakerlisteInTest.id)

            deltakere.size shouldBe 1
        }
    }

    @Nested
    inner class GetDeltakerHvorSluttdatoSkalEndresTests {
        val deltakerlisteInTest = lagDeltakerliste(sluttDato = LocalDate.now().minusDays(2))

        @BeforeEach
        fun setup() = TestRepository.insert(deltakerlisteInTest)

        @Test
        fun `skal returnere tom liste hvis ingen deltakere`() {
            val deltakere = deltakerRepository.getDeltakerHvorSluttdatoSkalEndres(deltakerlisteInTest.id)

            deltakere.shouldBeEmpty()
        }

        @ParameterizedTest
        @EnumSource(
            value = DeltakerStatus.Type::class,
            names = ["AVBRUTT", "AVBRUTT_UTKAST", "FULLFORT", "HAR_SLUTTET", "IKKE_AKTUELL", "FEILREGISTRERT"],
        )
        fun `skal filtrere bort deltakere med avsluttende status`(status: DeltakerStatus.Type) {
            TestRepository.insert(
                lagDeltaker(
                    status = lagDeltakerStatus(status),
                    deltakerliste = deltakerlisteInTest,
                ),
            )

            val deltakere = deltakerRepository.getDeltakerHvorSluttdatoSkalEndres(deltakerlisteInTest.id)

            deltakere.shouldBeEmpty()
        }

        @Test
        fun `skal returnere deltakere med sluttdato storre enn deltakerliste sluttdato`() {
            setOf(
                LocalDate.now().plusDays(1), // skal returneres
                LocalDate.now().minusDays(2),
                null,
            ).forEach { sluttdato ->
                TestRepository.insert(
                    lagDeltaker(
                        status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                        deltakerliste = deltakerlisteInTest,
                        sluttdato = sluttdato,
                    ),
                )
            }

            val deltakere = deltakerRepository.getDeltakerHvorSluttdatoSkalEndres(deltakerlisteInTest.id)

            deltakere.size shouldBe 1
        }
    }

    @Nested
    inner class GetAntallDeltakereForDeltakerlisteTests {
        val deltakerlisteInTest = lagDeltakerliste()

        @BeforeEach
        fun setup() = TestRepository.insert(deltakerlisteInTest)

        @Test
        fun `skal returnere 0 hvis ingen deltakere`() {
            val antallDeltakere = deltakerRepository.getAntallDeltakereForDeltakerliste(deltakerlisteInTest.id)

            antallDeltakere shouldBe 0
        }

        @Test
        fun `skal returnere antall deltaker hvis deltakerliste inneholder deltakere`() {
            TestRepository.insert(lagDeltaker(deltakerliste = deltakerlisteInTest))

            val antallDeltakere = deltakerRepository.getAntallDeltakereForDeltakerliste(deltakerlisteInTest.id)

            antallDeltakere shouldBe 1
        }
    }

    @Nested
    inner class UpsertTests {
        @Test
        fun `ny deltaker - insertes`() {
            val expectedDeltaker = lagDeltaker()
            TestRepository.insertAll(expectedDeltaker.deltakerliste, expectedDeltaker.navBruker)

            deltakerRepository.upsert(expectedDeltaker)
            DeltakerStatusRepository.lagreStatus(expectedDeltaker.id, expectedDeltaker.status)

            val deltakerFromDb = deltakerRepository.get(expectedDeltaker.id).shouldBeSuccess()
            assertDeltakereAreEqual(deltakerFromDb, expectedDeltaker)
        }

        @Test
        fun `oppdatert deltaker - oppdaterer`() {
            val deltaker = lagDeltaker()
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                startdato = LocalDate.now().plusWeeks(1),
                sluttdato = LocalDate.now().plusWeeks(5),
                dagerPerUke = 1F,
                deltakelsesprosent = 20F,
            )

            deltakerRepository.upsert(oppdatertDeltaker)

            assertDeltakereAreEqual(
                deltakerRepository.get(deltaker.id).shouldBeSuccess(),
                oppdatertDeltaker,
            )
        }
    }

    @Nested
    inner class SkalHaAvsluttendeStatusTests {
        @Test
        fun `deltar, sluttdato passert - returnerer deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().plusWeeks(2),
            )
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().minusDays(1),
            )
            deltakerRepository.upsert(oppdatertDeltaker)

            val deltakereSomSkalHaAvsluttendeStatus = deltakerRepository.getDeltakereHvorSluttdatoHarPassert()

            deltakereSomSkalHaAvsluttendeStatus.size shouldBe 1
            deltakereSomSkalHaAvsluttendeStatus.first().id shouldBe deltaker.id
        }

        @Test
        fun `venter pa oppstart, sluttdato mangler - returnerer ikke deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = null,
            )
            TestRepository.insert(deltaker)

            val deltakereSomSkalHaAvsluttendeStatus = deltakerRepository.getDeltakereHvorSluttdatoHarPassert()

            deltakereSomSkalHaAvsluttendeStatus.shouldBeEmpty()
        }
    }

    @Nested
    inner class DeltarPaAvsluttetDeltakerlisteTests {
        @Test
        fun `deltar, dl-sluttdato passert - returnerer deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().plusDays(2),
                deltakerliste = lagDeltakerliste(status = GjennomforingStatusType.AVSLUTTET),
            )
            TestRepository.insert(deltaker)

            val deltakerePaAvsluttetDeltakerliste = deltakerRepository.getDeltakereSomDeltarPaAvsluttetDeltakerliste()

            deltakerePaAvsluttetDeltakerliste.size shouldBe 1
            deltakerePaAvsluttetDeltakerliste.first().id shouldBe deltaker.id
        }

        @Test
        fun `har sluttet, dl-sluttdato passert - returnerer ikke deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now(),
                deltakerliste = lagDeltakerliste(status = GjennomforingStatusType.AVSLUTTET),
            )
            TestRepository.insert(deltaker)

            val deltakerePaAvsluttetDeltakerliste = deltakerRepository.getDeltakereSomDeltarPaAvsluttetDeltakerliste()

            deltakerePaAvsluttetDeltakerliste.shouldBeEmpty()
        }
    }

    @Nested
    inner class GetTests {
        @Test
        fun `skal returnere eksisterende deltaker`() {
            val deltaker = lagDeltaker()
            TestRepository.insert(deltaker)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).shouldBeSuccess()

            deltakerFraDb.shouldNotBeNull()
            deltakerFraDb.id shouldBe deltaker.id
        }

        @Test
        fun `deltaker er feilregistrert - fjerner informasjon`() {
            val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.FEILREGISTRERT))
            TestRepository.insert(deltaker)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            assertSoftly(deltakerFraDb) {
                startdato shouldBe null
                sluttdato shouldBe null
                dagerPerUke shouldBe null
                deltakelsesprosent shouldBe null
                bakgrunnsinformasjon shouldBe null
                deltakelsesinnhold shouldBe null
            }
        }
    }

    @Test
    fun `getMany(list) - henter mange deltakere`() {
        val deltaker1 = lagDeltaker()
        val deltaker2 = lagDeltaker()

        TestRepository.insertAll(deltaker1, deltaker2)

        val deltakere = deltakerRepository.getMany(setOf(deltaker1.id, deltaker2.id))
        deltakere shouldHaveSize 2
        deltakere.any { it.id == deltaker1.id } shouldBe true
        deltakere.any { it.id == deltaker2.id } shouldBe true
    }

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        fun assertDeltakereAreEqual(first: Deltaker, second: Deltaker) {
            assertSoftly(first) {
                id shouldBe second.id
                navBruker shouldBe second.navBruker
                startdato shouldBe second.startdato
                sluttdato shouldBe second.sluttdato
                dagerPerUke shouldBe second.dagerPerUke
                deltakelsesprosent shouldBe second.deltakelsesprosent
                bakgrunnsinformasjon shouldBe second.bakgrunnsinformasjon
                deltakelsesinnhold shouldBe second.deltakelsesinnhold
                status.id shouldBe second.status.id
                status.type shouldBe second.status.type
                status.aarsak shouldBe second.status.aarsak
                status.gyldigFra shouldBeCloseTo second.status.gyldigFra
                status.gyldigTil shouldBeCloseTo second.status.gyldigTil
                status.opprettet shouldBeCloseTo second.status.opprettet
                sistEndret shouldBeCloseTo second.sistEndret
            }
        }
    }
}
