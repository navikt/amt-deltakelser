package no.nav.amt.lib.testing

import kotliquery.queryOf
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.database.DatabaseConfig
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.postgresql.PostgreSQLContainer

object TestPostgresContainer {
    private const val POSTGRES_DOCKER_IMAGE_NAME = "postgres:17-alpine"
    private var dbInitialized = false

    // Schema doesn't change during a test run (migrations only run once in bootstrap()),
    // so the table list is resolved once and reused instead of re-querying pg_tables
    // and building the dynamic TRUNCATE statement before every single test.
    private var cachedTableNames: List<String>? = null

    fun bootstrap() {
        if (!dbInitialized) {
            if (!container.isRunning) container.start()
            initDatabase()
            dbInitialized = true
        }
    }

    fun truncateAllTables() {
        val tableNames = cachedTableNames ?: resolveTableNames().also { cachedTableNames = it }
        if (tableNames.isEmpty()) return

        val sql = "TRUNCATE TABLE ${tableNames.joinToString(", ")} CASCADE"
        Database.query { session -> session.update(queryOf(sql)) }
    }

    private fun resolveTableNames(): List<String> = Database.query { session ->
        session.run(
            queryOf(
                """
                SELECT format('%I.%I', schemaname, tablename) AS table_name
                FROM pg_tables
                WHERE
                    schemaname = 'public'
                    AND tablename NOT IN ('flyway_schema_history')
                """.trimIndent(),
            ).map { row -> row.string("table_name") }.asList,
        )
    }

    private val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer(POSTGRES_DOCKER_IMAGE_NAME)
            .withCommand("postgres", "-c", "wal_level=logical")
            .waitingFor(HostPortWaitStrategy())
            .apply { addEnv("TZ", "Europe/Oslo") }
    }

    private fun initDatabase() {
        val c = container
        Database.init(
            DatabaseConfig(
                dbUsername = c.username,
                dbPassword = c.password,
                dbDatabase = c.databaseName,
                dbHost = c.host,
                dbPort = c.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT).toString(),
            ),
        )
    }
}
