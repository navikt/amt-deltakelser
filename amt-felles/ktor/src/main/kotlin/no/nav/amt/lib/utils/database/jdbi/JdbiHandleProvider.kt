package no.nav.amt.lib.utils.database.jdbi

import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi

/**
 * Dette interfacet gjør det mulig å kontrollere hvordan databaseforbindelser opprettes. Dette er spesielt nyttig i tester.
 */
interface JdbiHandleProvider {
    fun <T> withHandle(block: (Handle) -> T): T
}

/**
 * Standard-implementasjon som delegerer alt sammen til Jdbi selv. Bruk der vi ikke trenger eksplisitt kontroll.
 */
class ApplicationJdbiHandleProvider(
    private val jdbi: Jdbi,
) : JdbiHandleProvider {
    override fun <T> withHandle(block: (Handle) -> T): T = jdbi.withHandle<T, Exception>(block)
}
