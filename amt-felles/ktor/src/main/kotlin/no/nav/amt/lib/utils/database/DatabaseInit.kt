package no.nav.amt.lib.utils.database

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
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
