package no.nav.amt.lib.testing

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class DatabaseTestExtension :
    BeforeAllCallback,
    BeforeEachCallback {
    override fun beforeAll(context: ExtensionContext) = TestPostgresContainer.bootstrap()

    override fun beforeEach(context: ExtensionContext) = runBlocking { TestPostgresContainer.truncateAllTables() }
}
