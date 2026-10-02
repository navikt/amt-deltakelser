package no.nav.amt.lib.utils.database.jdbi

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
    dataSource: DataSource,
) {
    val db: DatabaseApi = DatabaseApi(ApplicationJdbiHandleProvider(createJdbi(dataSource)))
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
