package no.nav.amt.aktivitetskort.repositories

import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.database.TestData
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class MeldingRepositoryTest(
    private val meldingRepository: MeldingRepository,
    private val dataSource: DataSource,
) : RepositoryTestBase() {
    @Test
    fun `V28 bevarer eldre periodekoblinger og tillater perioder uten lokal kopi`() {
        val jdbc = JdbcTemplate(dataSource)
        val arrangorId = UUID.randomUUID()
        val deltakerlisteId = UUID.randomUUID()
        val meldingId = UUID.randomUUID()
        val gammelPeriodeId = UUID.randomUUID()
        val nyPeriodeId = UUID.randomUUID()
        val config = Flyway
            .configure()
            .dataSource(dataSource)
            .schemas("periode_migrering_test")
            .locations("classpath:db/migration")

        try {
            config.target("27").load().migrate()
            jdbc.update(
                "INSERT INTO periode_migrering_test.arrangor (id, navn) VALUES (?, ?)",
                arrangorId,
                "Testarrangør",
            )
            jdbc.update(
                """
                INSERT INTO periode_migrering_test.deltakerliste (id, navn, arrangor_id, tiltaksnavn, tiltakstype)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                deltakerlisteId,
                "Testgjennomføring",
                arrangorId,
                "Oppfølging",
                "OPPFOLGING",
            )
            jdbc.update(
                "INSERT INTO periode_migrering_test.oppfolgingsperiode (id, start_dato) VALUES (?, current_timestamp)",
                gammelPeriodeId,
            )
            jdbc.update(
                """
                INSERT INTO periode_migrering_test.melding
                    (id, deltaker_id, deltakerliste_id, arrangor_id, melding, oppfolgingsperiode)
                VALUES (?, ?, ?, ?, '{}'::jsonb, ?)
                """.trimIndent(),
                meldingId,
                UUID.randomUUID(),
                deltakerlisteId,
                arrangorId,
                gammelPeriodeId,
            )

            config
                .target("28")
                .load()
                .migrate()
                .migrationsExecuted shouldBe 1

            jdbc.queryForObject<UUID>(
                "SELECT oppfolgingsperiode FROM periode_migrering_test.melding WHERE id = ?",
                meldingId,
            ) shouldBe gammelPeriodeId
            jdbc.update(
                "UPDATE periode_migrering_test.melding SET oppfolgingsperiode = ? WHERE id = ?",
                nyPeriodeId,
                meldingId,
            ) shouldBe 1
            jdbc.queryForObject<UUID>(
                "SELECT oppfolgingsperiode FROM periode_migrering_test.melding WHERE id = ?",
                meldingId,
            ) shouldBe nyPeriodeId
            jdbc.queryForObject<Int>("SELECT count(*) FROM periode_migrering_test.oppfolgingsperiode") shouldBe 1
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS periode_migrering_test CASCADE")
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
