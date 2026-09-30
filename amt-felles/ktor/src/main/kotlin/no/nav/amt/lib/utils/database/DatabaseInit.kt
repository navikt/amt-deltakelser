package no.nav.amt.lib.utils.database

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.argument.AbstractArgumentFactory
import org.jdbi.v3.core.argument.Argument
import org.jdbi.v3.core.argument.Arguments
import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.postgres.PostgresPlugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.postgresql.util.PGobject
import java.sql.Types
import javax.sql.DataSource

/**
 * Felles oppsett av datakilde, JDBI og migrering, delt mellom [Database] og den ikke-statiske databasetilgangen.
 */
internal object DatabaseInit {
    fun createDataSource(config: DatabaseConfig): DataSource = HikariDataSource().apply {
        if (config.jdbcURL.isNotEmpty()) {
            jdbcUrl = config.jdbcURL
        } else {
            dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
            addDataSourceProperty("serverName", config.dbHost)
            addDataSourceProperty("portNumber", config.dbPort)
            addDataSourceProperty("databaseName", config.dbDatabase)
            addDataSourceProperty("user", config.dbUsername)
            addDataSourceProperty("password", config.dbPassword)
        }

        maximumPoolSize = 10
        minimumIdle = 1
        leakDetectionThreshold = 15_000
    }

    fun createJdbi(dataSource: DataSource): Jdbi = Jdbi
        .create(dataSource)
        // Støtter definisjon av repositories etc som interface
        .installPlugin(SqlObjectPlugin())
        // Støtter automatisk mapping av database-resultater til Kotlin-dataklasser
        .installPlugin(KotlinPlugin())
        // Støtter mapping av en del vanlige Postgres-spesifikke typer
        .installPlugin(PostgresPlugin())
        .configure(Arguments::class.java) { arguments ->
            arguments.register(PgObjectArgumentFactory())
        }

    fun runMigration(
        dataSource: DataSource,
        initSql: String? = null,
    ): Int = Flyway
        .configure()
        .connectRetries(5)
        .dataSource(dataSource)
        .initSql(initSql)
        .validateMigrationNaming(true)
        .load()
        .migrate()
        .migrations
        .size
}

private class PgObjectArgumentFactory : AbstractArgumentFactory<PGobject>(Types.OTHER) {
    override fun build(
        value: PGobject,
        config: ConfigRegistry,
    ): Argument = Argument { position, statement, _ ->
        statement.setObject(position, value)
    }
}

