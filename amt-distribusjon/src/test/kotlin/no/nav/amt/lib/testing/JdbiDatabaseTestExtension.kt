package no.nav.amt.lib.testing

import no.nav.amt.distribusjon.hendelse.JdbiHendelseRepository
import no.nav.amt.lib.utils.database.Database
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class JdbiDatabaseTestExtension :
    BeforeAllCallback,
    BeforeEachCallback,
    AfterEachCallback {
    private lateinit var jdbi: Jdbi
    private lateinit var conn: Handle

    lateinit var hendelseRepository: JdbiHendelseRepository

    override fun beforeAll(context: ExtensionContext) {
        TestPostgresContainer.bootstrap()
        jdbi = Database.jdbi
    }

    override fun beforeEach(context: ExtensionContext) {
        conn = jdbi.open()
        conn.begin()
        hendelseRepository = conn.attach(JdbiHendelseRepository::class.java)
    }

    override fun afterEach(context: ExtensionContext) {
        conn.rollback()
    }
}
