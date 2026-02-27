package no.nav.amt.lib.testing

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class DatabaseTestExtension :
    BeforeAllCallback,
    BeforeEachCallback {
    override fun beforeAll(context: ExtensionContext) = TestPostgresContainer.bootstrap()

    override fun beforeEach(context: ExtensionContext) = TestPostgresContainer.truncateAllTables()
}
