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

fun createJdbiDatabaseApi(dataSource: DataSource) = DatabaseApi(ApplicationJdbiHandleProvider(createJdbi(dataSource)))

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

/**
 * Jdbi har et kraftig, men ganske omfattende og stundom omstendelig API. Denne klassen tilbyr enkle innganger til databasen som dekker
 * de vanligste brukstilfellene:
 *
 *  * For enkle spørringer som ikke bruker mer enn ett repository: [bruk]
 *  * For flere spørringer som skal pakkes inn i en transaksjon: [transaksjon]
 *  * For mer kompliserte situasjoner, f.eks. der flere repositories skal sys sammen med varierende bruk av transaksjoner: [forbindelse]
 */
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
}
