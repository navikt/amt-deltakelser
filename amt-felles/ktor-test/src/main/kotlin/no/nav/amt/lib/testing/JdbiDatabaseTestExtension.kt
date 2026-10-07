package no.nav.amt.lib.testing

import no.nav.amt.lib.utils.database.DatabaseInit
import no.nav.amt.lib.utils.database.jdbi.DatabaseApi
import no.nav.amt.lib.utils.database.jdbi.JdbiHandleProvider
import no.nav.amt.lib.utils.database.jdbi.Repository
import no.nav.amt.lib.utils.database.jdbi.createJdbi
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.sqlobject.SqlObject
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

class JdbiDatabaseTestExtension :
    BeforeAllCallback,
    BeforeEachCallback,
    AfterEachCallback {
    /**
     * Eksponert for at kode under test skal kunne bruke samme inngang til databasen som testkoden.
     */
    lateinit var dbApi: DatabaseApi

    private lateinit var jdbi: Jdbi
    private val handleThreadLocal = ThreadLocal.withInitial { null as Handle? }
    private val log = LoggerFactory.getLogger(javaClass)

    override fun beforeAll(context: ExtensionContext) {
        TestPostgresContainer.bootstrap()
        jdbi = createJdbi(DatabaseInit.createDataSource(TestPostgresContainer.databaseConfig()))

        val jdbiHandleProvider = object : JdbiHandleProvider {
            /**
             * Definerer hva som skjer når applikasjonskode (kode under test) ber om en databaseforbindelse.
             * Vi ønsker som hovedregel å bruke samme forbindelse som testkoden, slik at vi kan pakke hver
             * test inn i en enkelt transaksjon (se nedenfor).
             *
             * ThreadLocal gir støtte for å kjøre flere tester parallelt og i praksis isolert fra hverandre
             * ved at de kjører i hver sin transaksjon.
             */
            override fun <T> withHandle(block: (Handle) -> T): T {
                val activeHandle = activeHandle()
                return if (activeHandle == null) {
                    // Dette skal normalt ikke skje, men kan tenkes oppstå ved testing av kode som bruker flere tråder.
                    log.warn(
                        "Ingen databaseforbindelse er satt opp på tråden. Spørringene vil *ikke* bli omfattet av test-transaksjonen og eventuelle endringer vil ikke bli rullet tilbake.",
                    )
                    jdbi.withHandle<T, RuntimeException>(block)
                } else {
                    block(activeHandle)
                }
            }
        }
        dbApi = DatabaseApi(jdbiHandleProvider)
    }

    fun activeHandle(): Handle? = handleThreadLocal.get()

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
    inline fun <reified T : Repository> repo(): Lazy<T> = lazy { db.bruk(T::class) }

    companion object {
        @RegisterExtension
        @JvmField
        val db = JdbiDatabaseTestExtension()
    }
}
