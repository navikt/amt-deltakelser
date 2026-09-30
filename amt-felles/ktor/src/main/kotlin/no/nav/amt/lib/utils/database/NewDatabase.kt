package no.nav.amt.lib.utils.database

import com.zaxxer.hikari.HikariDataSource
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.sqlobject.SqlObject
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * Ikke-statisk variant av [Database] som kun støtter det nye JDBI-baserte grensesnittet
 * ([DatabaseApi.bruk] / [DatabaseApi.transaksjon] / [DatabaseApi.forbindelse]).
 *
 * I motsetning til [Database], som er et globalt singleton-objekt med legacy Kotliquery-støtte,
 * er [NewDatabase] en vanlig klasse. Det gjør det mulig å opprette flere uavhengige
 * databaseinstanser i samme JVM (f.eks. i tester), uten delt globalt state.
 */
class NewDatabase(
    config: DatabaseConfig,
) {
    private val dataSource: DataSource = DatabaseInit.createDataSource(config)

    val testSupport: DatabaseTestSupport
    val db: DatabaseApi

    init {
        val jdbi = DatabaseInit.createJdbi(dataSource)
        testSupport = DatabaseTestSupport(jdbi)
        db = DatabaseApi(jdbi, testSupport)

        DatabaseInit.runMigration(dataSource)
    }

    fun close() {
        (dataSource as HikariDataSource).close()
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
    private val testSupport: DatabaseTestSupport,
) {
    fun <T : SqlObject, S> bruk(
        sqlObjectKlasse: KClass<T>,
        blokk: (sqlObject: T) -> S,
    ): S {
        val activeHandle = testSupport.currentHandle()
        return if (activeHandle != null) {
            activeHandle.attach(sqlObjectKlasse.java).let(blokk)
        } else {
            jdbi.withExtension<S, T, Exception>(sqlObjectKlasse.java) { blokk(it) }
        }
    }

    fun <T> transaksjon(blokk: (Transaksjon) -> T): T {
        val activeHandle = testSupport.currentHandle()
        return if (activeHandle != null) {
            activeHandle.inTransaction<T, Exception> { handle ->
                blokk(Transaksjon(handle))
            }
        } else {
            forbindelse { it.transaksjon(blokk) }
        }
    }

    fun <T> forbindelse(blokk: (Forbindelse) -> T): T {
        val activeHandle = testSupport.currentHandle()
        return if (activeHandle != null) {
            blokk(Forbindelse(activeHandle))
        } else {
            jdbi.withHandle<T, Exception> { handle ->
                blokk(Forbindelse(handle))
            }
        }
    }
}
