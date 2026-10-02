package no.nav.amt.lib.testing

import no.nav.amt.lib.utils.database.DatabaseInit
import no.nav.amt.lib.utils.database.jdbi.DatabaseApi
import no.nav.amt.lib.utils.database.jdbi.DatabaseTestSupport
import no.nav.amt.lib.utils.database.jdbi.JdbiDatabase
import no.nav.amt.lib.utils.database.jdbi.JdbiHandleProvider
import no.nav.amt.lib.utils.database.jdbi.createJdbi
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
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
 * egne [JdbiDatabase]-instanser.
 * Må være en lambda (ikke evaluert ved konstruksjon), siden [DatabaseTestSupport] typisk
 * ikke er klar før `TestPostgresContainer.bootstrap()` i [beforeAll] har kjørt.
 */
class JdbiDatabaseTestExtension :
    BeforeAllCallback,
    BeforeEachCallback,
    AfterEachCallback {
    lateinit var jdbi: Jdbi
    lateinit var dbApi: DatabaseApi
    private val handleThreadLocal = ThreadLocal<Handle?>()

    override fun beforeAll(context: ExtensionContext) {
        TestPostgresContainer.bootstrap()
        jdbi = createJdbi(DatabaseInit.createDataSource(TestPostgresContainer.databaseConfig()))
        val jdbiHandleProvider = object : JdbiHandleProvider {
            override fun <T> withHandle(block: (Handle) -> T): T {
                val activeHandle = handleThreadLocal.get()
                return if (activeHandle == null) {
                    TODO("maybe this hsouldnot happens")
                    jdbi.withHandle<T, RuntimeException>(block)
                } else {
                    block(activeHandle)
                }
            }
        }
        dbApi = DatabaseApi(jdbiHandleProvider)
    }

    override fun beforeEach(context: ExtensionContext) {
        val handle = jdbi.open()
        handle.begin()
        handleThreadLocal.set(handle)
    }

    override fun afterEach(context: ExtensionContext) {
        handleThreadLocal.get()?.let { handle ->
            handle.rollback()
            handle.close()
            handleThreadLocal.remove()
        }
    }

    fun <T : SqlObject> bruk(extension: KClass<T>): T = handleThreadLocal.get()!!.attach(extension.java)
}

abstract class RepositoryTest {
    inline fun <reified T : SqlObject> repo(): Lazy<T> = lazy { db.bruk(T::class) }

    companion object {
        @RegisterExtension
        @JvmField
        val db = JdbiDatabaseTestExtension()
    }
}
