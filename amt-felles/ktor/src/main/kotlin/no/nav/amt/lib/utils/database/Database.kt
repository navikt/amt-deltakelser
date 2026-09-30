package no.nav.amt.lib.utils.database

import com.zaxxer.hikari.HikariDataSource
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.argument.AbstractArgumentFactory
import org.jdbi.v3.core.argument.Argument
import org.jdbi.v3.core.argument.Arguments
import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.postgres.PostgresPlugin
import org.jdbi.v3.sqlobject.SqlObject
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.postgresql.util.PGobject
import java.sql.Types
import javax.sql.DataSource
import kotlin.reflect.KClass

object Database {
    private lateinit var dataSource: DataSource
    lateinit var db: DatabaseApi
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

            maximumPoolSize = 10
            minimumIdle = 1
            leakDetectionThreshold = 15_000
        }
        val jdbi = Jdbi
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
        db = DatabaseApi(jdbi)

        runMigration()
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

private class PgObjectArgumentFactory : AbstractArgumentFactory<PGobject>(Types.OTHER) {
    override fun build(
        value: PGobject,
        config: ConfigRegistry,
    ): Argument = Argument { position, statement, _ ->
        statement.setObject(position, value)
    }
}

class Transaksjon internal constructor(
    private val handle: Handle,
) {
    fun <T : SqlObject, S> bruk(
        sqlObjectKlasse: KClass<T>,
        blokk: (sqlObject: T) -> S,
    ): S = handle.attach(sqlObjectKlasse.java).let(blokk)
}

class Forbindelse internal constructor(
    private val handle: Handle,
) {
    fun <T : SqlObject> bruk(sqlObjectKlasse: KClass<T>): T = handle.attach(sqlObjectKlasse.java)

    fun <T> transaksjon(blokk: (Transaksjon) -> T): T = handle.inTransaction<T, Exception> { handle ->
        blokk(Transaksjon(handle))
    }
}

class DatabaseApi(
    private val jdbi: Jdbi,
) {
    /**
     * Aktivt JDBI-[Handle] for gjeldende tråd, satt av testutvidelser slik at
     * applikasjonskode og testoppsett deler samme transaksjon.
     *
     * Skal ikke brukes fra produksjonskode.
     */
    private val activeHandleThreadLocal = ThreadLocal<Handle?>()

    /**
     * Åpner et nytt JDBI-[Handle], starter en transaksjon på det, og binder det som aktivt
     * handle for gjeldende tråd. Brukes av testutvidelser som vil dele transaksjon med
     * applikasjonskode.
     *
     * Kall [rollbackAndCloseTestTransaction] med det returnerte handle-et i `afterEach`
     * for å rulle tilbake og fjerne bindingen igjen.
     *
     * Skal ikke brukes fra produksjonskode.
     */
    fun beginTestTransaction(): Handle {
        val handle = jdbi.open()
        handle.begin()
        activeHandleThreadLocal.set(handle)
        return handle
    }

    /**
     * Ruller tilbake og lukker et [Handle] åpnet med [beginTestTransaction], og fjerner
     * bindingen til gjeldende tråd.
     */
    fun rollbackAndCloseTestTransaction(handle: Handle) {
        handle.rollback()
        handle.close()
        activeHandleThreadLocal.remove()
    }

    fun <T : SqlObject, S> bruk(
        sqlObjectKlasse: KClass<T>,
        blokk: (sqlObject: T) -> S,
    ): S {
        val activeHandle = activeHandleThreadLocal.get()
        return if (activeHandle != null) {
            activeHandle.attach(sqlObjectKlasse.java).let(blokk)
        } else {
            jdbi.withExtension<S, T, Exception>(sqlObjectKlasse.java) { blokk(it) }
        }
    }

    fun <T> transaksjon(blokk: (Transaksjon) -> T): T {
        val activeHandle = activeHandleThreadLocal.get()
        return if (activeHandle != null) {
            activeHandle.inTransaction<T, Exception> { handle ->
                blokk(Transaksjon(handle))
            }
        } else {
            forbindelse { it.transaksjon(blokk) }
        }
    }

    fun <T> forbindelse(blokk: (Forbindelse) -> T): T {
        val activeHandle = activeHandleThreadLocal.get()
        return if (activeHandle != null) {
            blokk(Forbindelse(activeHandle))
        } else {
            jdbi.withHandle<T, Exception> { handle ->
                blokk(Forbindelse(handle))
            }
        }
    }
}
