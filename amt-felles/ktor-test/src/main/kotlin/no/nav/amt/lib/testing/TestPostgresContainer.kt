package no.nav.amt.lib.testing

import kotliquery.queryOf
import no.nav.amt.lib.testing.utils.ContainerReuseConfig
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.database.DatabaseConfig
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.postgresql.PostgreSQLContainer

object TestPostgresContainer {
    private const val POSTGRES_DOCKER_IMAGE_NAME = "postgres:17-alpine"
    private var dbInitialized = false

    private val reuseConfig = ContainerReuseConfig()

    fun bootstrap() {
        if (!dbInitialized) {
            if (!container.isRunning) container.start()
            initDatabase()
            dbInitialized = true
        }
    }

    fun truncateAllTables() {
        val sql =
            """
            DO $$
            DECLARE table_names TEXT;
            
            BEGIN
                SELECT string_agg(format('%I.%I', schemaname, tablename), ', ')
                INTO table_names
                FROM pg_tables
                WHERE
                    schemaname = 'public'
                    AND tablename NOT IN ('flyway_schema_history');

                IF table_names IS NOT NULL THEN
                    EXECUTE format('TRUNCATE TABLE %s CASCADE', table_names);
                END IF;
            END $$;                
            """.trimIndent()

        Database.query { session -> session.update(queryOf(sql)) }
    }

    private val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer(POSTGRES_DOCKER_IMAGE_NAME)
            .withCommand("postgres", "-c", "wal_level=logical")
            .waitingFor(HostPortWaitStrategy())
            .withReuse(reuseConfig.reuse)
            .withLabel("reuse.UUID", reuseConfig.reuseLabel)
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
