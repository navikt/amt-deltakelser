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

    fun cleanDatabase(dataSource: DataSource) {
        val jdbcTemplate = JdbcTemplate(dataSource)

        val tables = getAllTables(jdbcTemplate).filter { it != FLYWAY_SCHEMA_HISTORY_TABLE_NAME }
        val sequences = getAllSequences(jdbcTemplate)

        tables.forEach {
            jdbcTemplate.update("TRUNCATE TABLE $it CASCADE")
        }

        sequences.forEach {
            jdbcTemplate.update("ALTER SEQUENCE $it RESTART WITH 1")
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
