package no.nav.amt.lib.testing

import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.database.DatabaseTestSupport
import org.jdbi.v3.core.Handle
import org.jdbi.v3.sqlobject.SqlObject
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.reflect.KClass

/**
 * @param testSupport Leverandør av [DatabaseTestSupport] som transaksjonen per test skal åpnes mot.
 * Standard er det globale [Database]-singletonet. Kan overstyres til f.eks. en egen
 * [no.nav.amt.lib.utils.database.NewDatabase]-instans sitt `testSupport`.
 * Må være en lambda (ikke evaluert ved konstruksjon), siden [DatabaseTestSupport] typisk
 * ikke er klar før `TestPostgresContainer.bootstrap()` i [beforeAll] har kjørt.
 */
class JdbiDatabaseTestExtension(
    private val testSupport: () -> DatabaseTestSupport = { Database.testSupport },
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
}
