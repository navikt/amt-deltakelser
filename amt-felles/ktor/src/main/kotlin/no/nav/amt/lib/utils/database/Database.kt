package no.nav.amt.lib.utils.database

import com.zaxxer.hikari.HikariDataSource
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

object Database {
    private lateinit var dataSource: DataSource
    lateinit var db: DatabaseApi
    lateinit var testSupport: DatabaseTestSupport
    private val transactionalSessionThreadLocal = ThreadLocal<TransactionalSession?>()
    internal val transactionalSession get() = transactionalSessionThreadLocal.get()

    fun init(config: DatabaseConfig) {
        dataSource = DatabaseInit.createDataSource(config)
        val jdbi = DatabaseInit.createJdbi(dataSource)
        testSupport = DatabaseTestSupport(jdbi)
        db = DatabaseApi(jdbi, testSupport)

        DatabaseInit.runMigration(dataSource)
    }

    fun <A> query(block: (Session) -> A): A {
        val tx = transactionalSession
        return if (tx != null) block(tx) else queryWithNewSession(block)
    }

    /**
     * Kjør synkron kode innenfor en database-transaksjon.
     *
     * Transaksjonen er tråd-bundet og basert på JDBC.
     * [TransactionalSession] lagres i en [ThreadLocal] slik at [query] automatisk
     * gjenbruker samme sesjon innenfor transaksjonen.
     *
     * **Viktig:** Ikke bruk `launch`, `async` eller andre coroutine-builders inne i
     * transaksjonsblokken — [ThreadLocal] propageres ikke til nye coroutines,
     * og spørringer i den nye coroutinen vil kjøre utenfor transaksjonen.
     *
     * @param block Kode som skal kjøres i transaksjon. Må ikke suspendere eller bytte tråd.
     * @return Resultatet fra blokken
     * @throws IllegalStateException hvis funksjonen kalles mens en annen transaksjon er aktiv
     * @throws [org.postgresql.util.PSQLException] hvis en utilsiktet prøver å committe direkte via session.transaction innenfor aktiv transaksjon
     */
    fun <T> transaction(block: () -> T): T {
        check(transactionalSession == null) { "Nested transactions are not supported" }
        return sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                transactionalSessionThreadLocal.set(tx)
                try {
                    block()
                } finally {
                    transactionalSessionThreadLocal.remove()
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
}
