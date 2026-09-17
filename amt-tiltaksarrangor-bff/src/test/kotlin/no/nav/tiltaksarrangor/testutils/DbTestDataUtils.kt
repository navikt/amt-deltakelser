package no.nav.tiltaksarrangor.testutils

import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.nulls.shouldNotBeNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDateTime
import javax.sql.DataSource

object DbTestDataUtils {
    private const val SCHEMA = "public"

    private const val FLYWAY_SCHEMA_HISTORY_TABLE_NAME = "flyway_schema_history"

    inline fun <reified T : Any> loggerFor(): Logger = LoggerFactory.getLogger(T::class.java)

    infix fun LocalDateTime.shouldBeCloseTo(expected: LocalDateTime?) {
        expected.shouldNotBeNull().shouldBeWithin(Duration.ofSeconds(2), this)
    }

    // Skjemaet endres ikke i løpet av en test-kjøring (migreringer kjører kun ved oppstart),
    // så tabell- og sekvensnavn hentes fra information_schema kun én gang og caches, i stedet
    // for å spørre databasen på nytt før hver eneste test.
    private var cachedTables: List<String>? = null
    private var cachedSequences: List<String>? = null

    fun cleanDatabase(dataSource: DataSource) {
        val jdbcTemplate = JdbcTemplate(dataSource)

        val tables = cachedTables ?: getAllTables(jdbcTemplate)
            .filter { it != FLYWAY_SCHEMA_HISTORY_TABLE_NAME }
            .also { cachedTables = it }

        val sequences = cachedSequences ?: getAllSequences(jdbcTemplate).also { cachedSequences = it }

        // Én kombinert TRUNCATE for alle tabeller og batchet ALTER SEQUENCE i stedet for
        // ett JDBC-kall per tabell/sekvens - reduserer antall network round-trips per test.
        if (tables.isNotEmpty()) {
            jdbcTemplate.update("TRUNCATE TABLE ${tables.joinToString(", ")} CASCADE")
        }

        if (sequences.isNotEmpty()) {
            jdbcTemplate.batchUpdate(*sequences.map { "ALTER SEQUENCE $it RESTART WITH 1" }.toTypedArray())
        }
    }

    private fun getAllTables(jdbcTemplate: JdbcTemplate): List<String> {
        val sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ?"

        return jdbcTemplate.query(sql, { rs, _ -> rs.getString(1) }, SCHEMA)
    }

    private fun getAllSequences(jdbcTemplate: JdbcTemplate): List<String> {
        val sql = "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = ?"

        return jdbcTemplate.query(sql, { rs, _ -> rs.getString(1) }, SCHEMA)
    }
}
