package no.nav.amt.lib.testing

import no.nav.amt.lib.utils.database.Database
import org.jdbi.v3.core.Handle
import org.jdbi.v3.sqlobject.SqlObject
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.reflect.KClass

class JdbiDatabaseTestExtension :
    BeforeAllCallback,
    BeforeEachCallback,
    AfterEachCallback {
    private val handleThreadLocal = ThreadLocal<Handle>()

    override fun beforeAll(context: ExtensionContext) {
        TestPostgresContainer.bootstrap()
    }

    override fun beforeEach(context: ExtensionContext) {
        handleThreadLocal.set(Database.testSupport.beginTestTransaction())
    }

    override fun afterEach(context: ExtensionContext) {
        handleThreadLocal.get()?.let { handle ->
            Database.testSupport.rollbackAndCloseTestTransaction(handle)
            handleThreadLocal.remove()
        }
    }

    fun <T : SqlObject> bruk(extension: KClass<T>): T = handleThreadLocal.get().attach(extension.java)
}
