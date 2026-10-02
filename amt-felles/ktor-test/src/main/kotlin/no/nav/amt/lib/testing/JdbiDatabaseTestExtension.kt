package no.nav.amt.lib.testing

import no.nav.amt.lib.utils.database.DatabaseTestSupport
import no.nav.amt.lib.utils.database.NewDatabase
import org.jdbi.v3.core.Handle
import org.jdbi.v3.sqlobject.SqlObject
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.reflect.KClass

/**
 * @param testSupport Leverandør av [DatabaseTestSupport] som transaksjonen per test skal åpnes mot.
 * Standard er den delte [instance]s `testSupport`. Kan overstyres for f.eks.
 * egne [NewDatabase]-instanser.
 * Må være en lambda (ikke evaluert ved konstruksjon), siden [DatabaseTestSupport] typisk
 * ikke er klar før `TestPostgresContainer.bootstrap()` i [beforeAll] har kjørt.
 */
class JdbiDatabaseTestExtension(
    private val testSupport: () -> DatabaseTestSupport = {
        instance.testSupport ?: error("instance ble opprettet uten withTestSupport = true")
    },
) : BeforeAllCallback,
    BeforeEachCallback,
    AfterEachCallback {
    private val handleThreadLocal = ThreadLocal<Handle>()

    override fun beforeAll(context: ExtensionContext) {
        TestPostgresContainer.bootstrap()
    }

    override fun beforeEach(context: ExtensionContext) {
        handleThreadLocal.set(testSupport().beginTestTransaction())
    }

    override fun afterEach(context: ExtensionContext) {
        handleThreadLocal.get()?.let { handle ->
            testSupport().rollbackAndCloseTestTransaction(handle)
            handleThreadLocal.remove()
        }
    }

    fun <T : SqlObject> bruk(extension: KClass<T>): T = handleThreadLocal.get().attach(extension.java)

    companion object {
        /**
         * Delt [NewDatabase]-instans for tester som bruker det JDBI-baserte grensesnittet
         * ([no.nav.amt.lib.utils.database.DatabaseApi]) i stedet for det globale, Kotliquery-baserte
         * [no.nav.amt.lib.utils.database.Database]-singletonet.
         *
         * Kobler seg til samme Postgres-testcontainer som [TestPostgresContainer], og opprettes
         * kun én gang per JVM slik at testklasser i samme modul deler samme datakilde.
         */
        val instance: NewDatabase by lazy {
            TestPostgresContainer.bootstrap()
            NewDatabase(TestPostgresContainer.databaseConfig(), withTestSupport = true)
        }
    }
}

abstract class RepositoryTest {
    inline fun <reified T : SqlObject> repo(): Lazy<T> = lazy { db.bruk(T::class) }

    companion object {
        @RegisterExtension
        @JvmField
        val db = JdbiDatabaseTestExtension()
    }
}
