package no.nav.amt.lib.testing

import kotliquery.queryOf
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.database.DatabaseConfig
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.postgresql.PostgreSQLContainer

object TestPostgresContainer {
    private const val POSTGRES_DOCKER_IMAGE_NAME = "postgres:17-alpine"
    private var dbInitialized = false

    val isInitialized: Boolean get() = dbInitialized

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
            DECLARE r RECORD;
            
            BEGIN
                FOR r IN (
                    SELECT tablename
                    FROM pg_tables
                    WHERE 
                        schemaname = 'public'
                        AND tablename NOT IN ('flyway_schema_history', 'outbox_record')
                ) 
                LOOP
                    EXECUTE format('TRUNCATE TABLE %I CASCADE', r.tablename);
                END LOOP;
            END $$;                
            """.trimIndent()

        Database.query { session -> session.update(queryOf(sql)) }
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
