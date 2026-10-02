package no.nav.amt.lib.utils.database.jdbi

import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi

interface JdbiHandleProvider {
    fun <T> withHandle(block: (Handle) -> T): T
}

class ApplicationJdbiHandleProvider(
    private val jdbi: Jdbi,
) : JdbiHandleProvider {
    override fun <T> withHandle(block: (Handle) -> T): T {
        val handle = jdbi.open()
        val result = block(handle)
        handle.close()
        return result
    }
}
