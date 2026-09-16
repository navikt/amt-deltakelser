package no.nav.amt.aktivitetskort.database

import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

object DbTestDataUtils {
    private const val SCHEMA = "public"

    private const val FLYWAY_SCHEMA_HISTORY_TABLE_NAME = "flyway_schema_history"

    // Skjemaet endres ikke i løpet av en test-kjøring (migreringer kjører kun ved oppstart),
    // så tabell- og sekvensnavn hentes fra information_schema kun én gang og caches, i stedet
    // for å spørre databasen på nytt før hver eneste test.
    private var cachedTables: List<String>? = null
    private var cachedSequences: List<String>? = null

    fun cleanDatabase(dataSource: DataSource) {
        val jdbcTemplate = JdbcTemplate(dataSource)

        val tables = cachedTables ?: getAllTables(jdbcTemplate, SCHEMA)
            .filter { it != FLYWAY_SCHEMA_HISTORY_TABLE_NAME }
            .also { cachedTables = it }

        val sequences = cachedSequences ?: getAllSequences(jdbcTemplate, SCHEMA).also { cachedSequences = it }

        // Én kombinert TRUNCATE for alle tabeller og batchet ALTER SEQUENCE i stedet for
        // ett JDBC-kall per tabell/sekvens - reduserer antall network round-trips per test.
        if (tables.isNotEmpty()) {
            jdbcTemplate.update("TRUNCATE TABLE ${tables.joinToString(", ")} CASCADE")
        }

        if (sequences.isNotEmpty()) {
            jdbcTemplate.batchUpdate(*sequences.map { "ALTER SEQUENCE $it RESTART WITH 1" }.toTypedArray())
        }
    }

    private fun getAllTables(
        jdbcTemplate: JdbcTemplate,
        schema: String,
    ): List<String> {
        val sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ?"

        return jdbcTemplate.query(sql, { rs, _ -> rs.getString(1) }, schema)
    }

    private fun getAllSequences(
        jdbcTemplate: JdbcTemplate,
        schema: String,
    ): List<String> {
        val sql = "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = ?"

        return jdbcTemplate.query(sql, { rs, _ -> rs.getString(1) }, schema)
    }
}
