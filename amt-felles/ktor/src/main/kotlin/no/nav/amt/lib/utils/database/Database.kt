package no.nav.amt.lib.utils.database

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import org.flywaydb.core.Flyway
import javax.sql.DataSource
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

object Database {
    class Tx internal constructor(
        internal val session: TransactionalSession,
    )

    class TxContext(
        val tx: Tx,
    ) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<TxContext>
    }

    suspend fun currentTx(): Tx = currentCoroutineContext()[TxContext]?.tx
        ?: error("No active transaction")

    suspend fun <T> query(block: (Session) -> T): T {
        val tx = currentCoroutineContext()[TxContext]?.tx

        return if (tx != null) {
            block(tx.session)
        } else {
            queryWithNewSession(block)
        }
    }

    suspend fun <T> transaction(block: suspend (Tx) -> T): T {
        check(currentCoroutineContext()[TxContext] == null) {
            "Nested transactions are not supported"
        }

        val session = sessionOf(dataSource)

        return session.use {
            session.transaction { tx ->
                val wrapped = Tx(tx)
                val ctx = TxContext(wrapped)

                withContext(ctx) {
                    block(wrapped)
                }
            }
        }
    }

    private fun <T> queryWithNewSession(block: (Session) -> T): T = using(sessionOf(dataSource)) { session ->
        block(session)
    }

    private lateinit var dataSource: DataSource

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

            maximumPoolSize = 10
            minimumIdle = 1
            leakDetectionThreshold = 15_000
        }

        runMigration()
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
