package no.nav.amt.lib.utils.database

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import org.flywaydb.core.Flyway
import javax.sql.DataSource

object Database {
    private lateinit var dataSource: DataSource
    private val transactionalSessionThreadLocal = ThreadLocal<TransactionalSession?>()
    internal val transactionalSession get() = transactionalSessionThreadLocal.get()

    fun init(config: DatabaseConfig) {
        dataSource = HikariDataSource().apply {
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

            maximumPoolSize = 20
            minimumIdle = 1
            leakDetectionThreshold = 10_000
        }

        runMigration()
    }

    fun <A> query(block: (Session) -> A): A = if (transactionalSession != null) {
        block(transactionalSession!!)
    } else {
        queryWithNewSession(block)
    }

    /**
     * Kjør synkron kode innenfor en database-transaksjon.
     *
     * Blokken må ikke suspendere eller bytte coroutine dispatcher.
     * Transaksjonen er tråd-bundet og basert på JDBC.
     *
     * @param block Kode som skal kjøres i transaksjon
     * @return Resultatet fra blokken
     * @throws IllegalStateException hvis funksjonen kalles mens en annen transaksjon er aktiv
     * @throws [org.postgresql.util.PSQLException] hvis en utilsiktet prøver å committe direkte via session.transaction innenfor aktiv transaksjon
     */
    suspend fun <T> transaction(block: () -> T): T {
        check(transactionalSession == null) { "Nested transactions are not supported" }

        return sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                val txContext = transactionalSessionThreadLocal.asContextElement(tx)
                withContext(txContext) {
                    try {
                        block()
                    } finally {
                        transactionalSessionThreadLocal.remove()
                    }
                }
            }
        }
    }

    private fun <A> queryWithNewSession(block: (Session) -> A): A = using(sessionOf(dataSource)) { session ->
        block(session)
    }

    fun close() {
        (dataSource as HikariDataSource).close()
    }

    private fun runMigration(initSql: String? = null): Int = Flyway
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
