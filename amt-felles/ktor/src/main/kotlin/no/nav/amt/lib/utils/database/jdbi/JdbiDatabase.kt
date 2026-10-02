package no.nav.amt.lib.utils.database.jdbi

import com.zaxxer.hikari.HikariDataSource
import no.nav.amt.lib.utils.database.DatabaseConfig
import no.nav.amt.lib.utils.database.DatabaseInit
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

/**
 * Databasewrapper basert på Jdbi-biblioteket.
 *
 * Gir støtte for:
 *  - et forenklet [DatabaseApi] som gjør at vi ikke trenger å forholde oss til hele Jdbi-APIet i det daglige (gjør det lett å gjøre rett)
 */
class JdbiDatabase(
    config: DatabaseConfig,
) : DatabaseApiProvider {
    private val dataSource: DataSource = DatabaseInit.createDataSource(config)

    override val db: DatabaseApi = DatabaseApi(ApplicationJdbiHandleProvider(createJdbi(dataSource)))

    init {
        DatabaseInit.runMigration(dataSource)
    }

    fun close() {
        (dataSource as HikariDataSource).close()
    }
}

interface DatabaseApiProvider {
    val db: DatabaseApi
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
    private val jdbiHandleProvider: JdbiHandleProvider,
) {
    fun <T : SqlObject, S> bruk(
        sqlObjectKlasse: KClass<T>,
        blokk: (sqlObject: T) -> S,
    ): S = jdbiHandleProvider.withHandle {
        val sqlObject = it.attach(sqlObjectKlasse.java)
        blokk(sqlObject)
    }

    fun <T> transaksjon(blokk: (Transaksjon) -> T): T = forbindelse { it.transaksjon(blokk) }

    fun <T> forbindelse(blokk: (Forbindelse) -> T): T = jdbiHandleProvider.withHandle {
        blokk(Forbindelse(it))
    }
}

/**
 * Støtter deling av JDBI-transaksjon mellom testoppsett og applikasjonskode.
 *
 * Testutvidelser (f.eks. `JdbiDatabaseTestExtension`) bruker [beginTestTransaction] til å åpne
 * en transaksjon per test, og [rollbackAndCloseTestTransaction] til å rulle den tilbake etterpå.
 * [DatabaseApi] leser det aktive handle-et via [currentHandle] slik at applikasjonskode som
 * kjører i samme tråd automatisk gjenbruker testens transaksjon.
 *
 * Skal ikke brukes fra produksjonskode.
 */
class DatabaseTestSupport internal constructor(
    private val jdbi: Jdbi,
) {
    private val activeHandleThreadLocal = ThreadLocal<Handle?>()

    internal fun currentHandle(): Handle? = activeHandleThreadLocal.get()

    /**
     * Åpner et nytt JDBI-[Handle], starter en transaksjon på det, og binder det som aktivt
     * handle for gjeldende tråd.
     *
     * Kall [rollbackAndCloseTestTransaction] med det returnerte handle-et i `afterEach`
     * for å rulle tilbake og fjerne bindingen igjen.
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
}
