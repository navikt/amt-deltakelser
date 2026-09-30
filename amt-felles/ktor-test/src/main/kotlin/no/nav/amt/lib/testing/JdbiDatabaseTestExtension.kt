package no.nav.amt.lib.testing

import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.database.DatabaseHandleProvider
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
        val handle = Database.db._jdbi.open()
        handle.begin()
        handleThreadLocal.set(handle)
        Database.setHandleProvider(object : DatabaseHandleProvider {
            override fun currentHandle(): Handle? = handleThreadLocal.get()
        })
    }

    override fun afterEach(context: ExtensionContext) {
        val handle = handleThreadLocal.get()
        if (handle != null) {
            handle.rollback()
            handle.close()
            handleThreadLocal.remove()
            Database.setHandleProvider(null)
        }
    }

    fun <T : SqlObject> bruk(extension: KClass<T>): T = handleThreadLocal.get().attach(extension.java)
}
