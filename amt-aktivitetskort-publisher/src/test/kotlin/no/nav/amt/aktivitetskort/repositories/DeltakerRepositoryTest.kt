package no.nav.amt.aktivitetskort.repositories

import io.getunleash.FakeUnleash
import io.kotest.assertions.AssertionErrorBuilder.Companion.fail
import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.database.TestData
import no.nav.amt.aktivitetskort.domain.DeltakerStatusModel
import no.nav.amt.aktivitetskort.utils.RepositoryResult
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class DeltakerRepositoryTest(
    private val deltakerRepository: DeltakerRepository,
    private val unleashClient: FakeUnleash,
) : RepositoryTestBase() {
    @BeforeEach
    fun setup() {
        unleashClient.disableAll()
    }

    @Test
    fun `get - finnes ikke - returnerer null`() {
        deltakerRepository.get(UUID.randomUUID()) shouldBe null
    }

    @Test
    fun `upsert - finnes ikke - returnerer Created Result - finnes i database`() {
        val deltaker = TestData
            .lagDeltaker()
            .also { testDatabase.insertDeltakerliste(TestData.lagDeltakerliste(id = it.deltakerlisteId)) }
        when (val result = deltakerRepository.upsert(deltaker, 0)) {
            is RepositoryResult.Created -> result.data shouldBe deltaker
            else -> fail("Should be Created, was $result")
        }

        deltakerRepository.get(deltaker.id) shouldBe deltaker
    }

    @Test
    fun `upsert - finnes, uendret - returnerer NoChange Result`() {
        val deltaker = TestData
            .lagDeltaker(dagerPerUke = null, prosentStilling = null)
            .also { testDatabase.insertDeltakerliste(TestData.lagDeltakerliste(id = it.deltakerlisteId)) }
            .also { deltakerRepository.upsert(it, 0) }

        when (val result = deltakerRepository.upsert(deltaker, 1)) {
            is RepositoryResult.Created -> {
                fail("Should be NoChange, was $result")
            }

            is RepositoryResult.Modified -> {
                fail("Should be NoChange, was $result")
            }

            is RepositoryResult.NoChange -> {}
        }
    }

    @Test
    fun `upsert - finnes, uendret, skal oppdatere for uendrede deltakere - returnerer Modified Result`() {
        unleashClient.enableAll()
        val deltaker = TestData
            .lagDeltaker(dagerPerUke = null, prosentStilling = null)
            .also { testDatabase.insertDeltakerliste(TestData.lagDeltakerliste(id = it.deltakerlisteId)) }
            .also { deltakerRepository.upsert(it, 0) }

        when (val result = deltakerRepository.upsert(deltaker, 1)) {
            is RepositoryResult.Created -> {
                fail("Should be Modified, was $result")
            }

            is RepositoryResult.Modified -> {}

            is RepositoryResult.NoChange -> {
                fail("Should be Modified, was $result")
            }
        }
    }

    @Test
    fun `upsert - endret - returnerer Modified Result og oppdaterer database`() {
        val initialDeltaker = TestData
            .lagDeltaker()
            .also { testDatabase.insertDeltakerliste(TestData.lagDeltakerliste(id = it.deltakerlisteId)) }
            .also { deltakerRepository.upsert(it, 0) }

        val updatedDeltaker = initialDeltaker.copy(
            personident = "UPDATED",
        )

        when (val result = deltakerRepository.upsert(updatedDeltaker, 1)) {
            is RepositoryResult.Modified -> result.data shouldBe updatedDeltaker
            else -> fail("Should be Modified, was $result")
        }

        deltakerRepository.get(initialDeltaker.id) shouldBe updatedDeltaker
    }

    @Test
    fun `upsert - endret, eldre offset - returnerer NoChange Result og oppdaterer ikke database`() {
        val initialDeltaker = TestData
            .lagDeltaker()
            .also { testDatabase.insertDeltakerliste(TestData.lagDeltakerliste(id = it.deltakerlisteId)) }
            .also { deltakerRepository.upsert(it, 1) }

        val tidligereVersjonAvDeltaker = initialDeltaker.copy(
            personident = "UTDATERT",
        )

        when (val result = deltakerRepository.upsert(tidligereVersjonAvDeltaker, 0)) {
            is RepositoryResult.Created -> {
                fail("Should be NoChange, was $result")
            }

            is RepositoryResult.Modified -> {
                fail("Should be NoChange, was $result")
            }

            is RepositoryResult.NoChange -> {}
        }

        deltakerRepository.get(initialDeltaker.id) shouldBe initialDeltaker
    }

    @Test
    fun `upsert - uendret, samme offset - returnerer NoChange Result og oppdaterer ikke database`() {
        val initialDeltaker = TestData
            .lagDeltaker()
            .also { testDatabase.insertDeltakerliste(TestData.lagDeltakerliste(id = it.deltakerlisteId)) }
            .also { deltakerRepository.upsert(it, 1) }

        when (val result = deltakerRepository.upsert(initialDeltaker, 1)) {
            is RepositoryResult.NoChange -> {}

            else -> {
                fail("Should be NoChange, was $result")
            }
        }

        deltakerRepository.get(initialDeltaker.id) shouldBe initialDeltaker
    }

    @Test
    fun `upsert - uendret, nyere offset - returnerer NoChange Result og oppdaterer ikke database`() {
        val initialDeltaker = TestData
            .lagDeltaker()
            .also { testDatabase.insertDeltakerliste(TestData.lagDeltakerliste(id = it.deltakerlisteId)) }
            .also { deltakerRepository.upsert(it, 1) }

        when (val result = deltakerRepository.upsert(initialDeltaker, 2)) {
            is RepositoryResult.Created -> {
                fail("Should be NoChange, was $result")
            }

            is RepositoryResult.Modified -> {
                fail("Should be NoChange, was $result")
            }

            is RepositoryResult.NoChange -> {}
        }

        deltakerRepository.get(initialDeltaker.id) shouldBe initialDeltaker
    }

    @Test
    fun `upsert - finnes ikke, status feilregistrert - returnerer NoChange Result, lagres ikke`() {
        val deltaker = TestData
            .lagDeltaker()
            .copy(status = DeltakerStatusModel(DeltakerStatus.Type.FEILREGISTRERT, null))
            .also { testDatabase.insertDeltakerliste(TestData.lagDeltakerliste(id = it.deltakerlisteId)) }
        when (val result = deltakerRepository.upsert(deltaker, 0)) {
            is RepositoryResult.NoChange -> {}

            else -> {
                fail("Should be NoChange, was $result")
            }
        }

        deltakerRepository.get(deltaker.id) shouldBe null
    }

    @Test
    fun `upsert - finnes, status feilregistrert - returnerer Modified Result og oppdaterer database`() {
        val initialDeltaker = TestData
            .lagDeltaker()
            .also { testDatabase.insertDeltakerliste(TestData.lagDeltakerliste(id = it.deltakerlisteId)) }
            .also { deltakerRepository.upsert(it, 0) }

        val updatedDeltaker = initialDeltaker.copy(
            status = DeltakerStatusModel(DeltakerStatus.Type.FEILREGISTRERT, null),
        )

        when (val result = deltakerRepository.upsert(updatedDeltaker, 1)) {
            is RepositoryResult.Modified -> result.data shouldBe updatedDeltaker
            else -> fail("Should be Modified, was $result")
        }

        deltakerRepository.get(initialDeltaker.id) shouldBe updatedDeltaker
    }

    @Test
    fun `upsert - avsluttet i nov 2017, finnes ikke - returnerer NoChange Result - lagres ikke`() {
        val deltaker = TestData
            .lagDeltaker(
                oppstartsdato = LocalDate.of(2017, 10, 1),
                sluttdato = LocalDate.of(2017, 11, 30),
                status = DeltakerStatusModel(DeltakerStatus.Type.HAR_SLUTTET, DeltakerStatus.Aarsak.Type.FATT_JOBB),
            ).also { testDatabase.insertDeltakerliste(TestData.lagDeltakerliste(id = it.deltakerlisteId)) }
        when (val result = deltakerRepository.upsert(deltaker, 0)) {
            is RepositoryResult.NoChange -> {}

            else -> {
                fail("Should be NoChange, was $result")
            }
        }

        deltakerRepository.get(deltaker.id) shouldBe null
    }

    @Test
    fun `upsert - avsluttet nov 2017, finnes med annen status - returnerer Modified Result og oppdaterer database`() {
        val initialDeltaker = TestData
            .lagDeltaker(
                oppstartsdato = LocalDate.of(2017, 10, 1),
                sluttdato = null,
                status = DeltakerStatusModel(DeltakerStatus.Type.DELTAR, null),
            ).also { testDatabase.insertDeltakerliste(TestData.lagDeltakerliste(id = it.deltakerlisteId)) }
            .also { deltakerRepository.upsert(it, 0) }

        val updatedDeltaker = initialDeltaker.copy(
            oppstartsdato = LocalDate.of(2017, 10, 1),
            sluttdato = LocalDate.of(2017, 11, 30),
            status = DeltakerStatusModel(DeltakerStatus.Type.FULLFORT, DeltakerStatus.Aarsak.Type.ANNET),
        )

        when (val result = deltakerRepository.upsert(updatedDeltaker, 1)) {
            is RepositoryResult.Modified -> result.data shouldBe updatedDeltaker
            else -> fail("Should be Modified, was $result")
        }

        deltakerRepository.get(initialDeltaker.id) shouldBe updatedDeltaker
    }
}
