package no.nav.amt.aktivitetskort.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.database.TestData
import no.nav.amt.aktivitetskort.utils.advisoryLock
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.IllegalTransactionStateException
import java.time.LocalDate
import java.util.UUID

class MeldingRepositoryTest(
    private val meldingRepository: MeldingRepository,
    private val template: NamedParameterJdbcTemplate,
) : RepositoryTestBase() {
    @Test
    fun `låsing uten transaksjon feiler i stedet for å slippe låsen før lagring`() {
        shouldThrow<IllegalTransactionStateException> {
            meldingRepository.advisoryLockMedlemshistorikk(UUID.randomUUID())
        }
        shouldThrow<IllegalTransactionStateException> {
            meldingRepository.advisoryLockMedlemskap("deltakerliste", UUID.randomUUID())
        }
    }

    @Test
    fun `databasehjelperen avviser delte og eksklusive låser uten transaksjon`() {
        shouldThrow<IllegalTransactionStateException> {
            template.advisoryLock("test:uten-transaksjon", delt = true)
        }
        shouldThrow<IllegalTransactionStateException> {
            template.advisoryLock("test:uten-transaksjon")
        }
    }

    @Test
    fun `lagring med låser eid av kalleren krever en eksisterende transaksjon`() {
        shouldThrow<IllegalTransactionStateException> {
            meldingRepository.upsertLaast(TestData.melding())
        }
    }

    @Test
    fun `get - finnes ikke - returnerer tom liste`() {
        meldingRepository.getByDeltakerId(UUID.randomUUID()) shouldBe emptyList()
    }

    @Test
    fun `upsert - finnes ikke - oppretter ny melding`() {
        val ctx = TestData.MockContext()
        val melding = ctx.melding
            .also { testDatabase.insertArrangor(ctx.arrangor) }
            .also { testDatabase.insertDeltakerliste(ctx.deltakerliste) }
            .also { testDatabase.insertDeltaker(ctx.deltaker) }
            .also { meldingRepository.upsert(it) }

        meldingRepository.getByDeltakerId(melding.deltakerId).first() shouldBe melding
    }

    @Test
    fun `getByDeltakerlisteId - henter meldinger på deltakerliste`() {
        val ctx = TestData.MockContext()
        val melding = ctx.melding
            .also { testDatabase.insertArrangor(ctx.arrangor) }
            .also { testDatabase.insertDeltakerliste(ctx.deltakerliste) }
            .also { testDatabase.insertDeltaker(ctx.deltaker) }
            .also { meldingRepository.upsert(it) }

        meldingRepository.getByDeltakerlisteId(ctx.deltakerliste.id) shouldBe listOf(melding)
    }

    @Test
    fun `getByArrangorId - henter meldinger på arrangør`() {
        val ctx = TestData.MockContext()
        val melding = ctx.melding
            .also { testDatabase.insertArrangor(ctx.arrangor) }
            .also { testDatabase.insertDeltakerliste(ctx.deltakerliste) }
            .also { testDatabase.insertDeltaker(ctx.deltaker) }
            .also { meldingRepository.upsert(it) }

        meldingRepository.getByArrangorId(ctx.arrangor.id) shouldBe listOf(melding)
    }

    @Test
    fun `upsert - melding er endret - oppdaterer melding`() {
        val ctx = TestData.MockContext()
        val initialMelding = ctx.melding
            .also { testDatabase.insertArrangor(ctx.arrangor) }
            .also { testDatabase.insertDeltakerliste(ctx.deltakerliste) }
            .also { testDatabase.insertDeltaker(ctx.deltaker) }
            .also { meldingRepository.upsert(it) }

        val updatedMelding = initialMelding.copy(
            aktivitetskort = initialMelding.aktivitetskort.copy(sluttDato = LocalDate.now()),
        )

        meldingRepository.upsert(updatedMelding)

        val melding = meldingRepository.getByDeltakerId(initialMelding.deltakerId).first()

        melding.aktivitetskort shouldBe updatedMelding.aktivitetskort
    }
}
