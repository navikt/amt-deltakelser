package no.nav.amt.lib.utils.database

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotliquery.TransactionalSession
import no.nav.amt.lib.testing.TestPostgresContainer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class DatabaseTransactionalSessionTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupAll() = TestPostgresContainer.bootstrap()
    }

    @Test
    fun `transactionalSession skal være null uten en transaksjonsblokk`() {
        Database.transactionalSession shouldBe null
    }

    @Test
    fun `transactionalSession skal ikke være null i en transaksjonsblokk og fjernes etter blokken`() = runTest {
        var insideSession: TransactionalSession?
        Database.transaction {
            insideSession = Database.transactionalSession
            insideSession shouldNotBe null
        }
        Database.transactionalSession shouldBe null
    }

    @Test
    fun `transaksjoner skal ikke gjenbruke session`() = runTest {
        var firstSession: TransactionalSession? = null
        var secondSession: TransactionalSession? = null

        Database.transaction {
            firstSession = Database.transactionalSession
        }
        Database.transaction {
            secondSession = Database.transactionalSession
        }

        firstSession shouldNotBe null
        secondSession shouldNotBe null
        firstSession shouldNotBe secondSession
    }

    @Test
    fun `sessions skal ikke gjenbrukes på tvers av coroutines og skal lukkes etter ferdig bruk`(): Unit = runBlocking {
        val sessions = mutableListOf<TransactionalSession?>()
        val maxConnectionPoolSize = 10

        val jobs = List(maxConnectionPoolSize + 1) {
            launch {
                delay(1000)
                Database.transaction {
                    sessions.add(Database.transactionalSession)
                }
            }
        }
        jobs.joinAll()

        sessions.forEach { it shouldNotBe null }
        sessions.toSet().size shouldBe maxConnectionPoolSize + 1
    }
}
