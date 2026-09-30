package no.nav.amt.lib.utils.database

import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi

/**
 * Støtter deling av JDBI-transaksjon mellom testoppsett og applikasjonskode.
 *
 * Testutvidelser (f.eks. `JdbiDatabaseTestExtension`) bruker [beginTestTransaction] til å åpne
 * en transaksjon per test, og [rollbackAndCloseTestTransaction] til å rulle den tilbake etterpå.
 * [DatabaseApi] leser det aktive handle-et via [currentHandle] slik at applikasjonskode som
 * kjører i samme tråd automatisk gjenbruker testens transaksjon.
 *
 * Skal ikke brukes fra produksjonskode.
 */
class DatabaseTestSupport internal constructor(
    private val jdbi: Jdbi,
) {
    private val activeHandleThreadLocal = ThreadLocal<Handle?>()

    internal fun currentHandle(): Handle? = activeHandleThreadLocal.get()

    /**
     * Åpner et nytt JDBI-[Handle], starter en transaksjon på det, og binder det som aktivt
     * handle for gjeldende tråd.
     *
     * Kall [rollbackAndCloseTestTransaction] med det returnerte handle-et i `afterEach`
     * for å rulle tilbake og fjerne bindingen igjen.
     */
    fun beginTestTransaction(): Handle {
        val handle = jdbi.open()
        handle.begin()
        activeHandleThreadLocal.set(handle)
        return handle
    }

    /**
     * Ruller tilbake og lukker et [Handle] åpnet med [beginTestTransaction], og fjerner
     * bindingen til gjeldende tråd.
     */
    fun rollbackAndCloseTestTransaction(handle: Handle) {
        handle.rollback()
        handle.close()
        activeHandleThreadLocal.remove()
    }
}

