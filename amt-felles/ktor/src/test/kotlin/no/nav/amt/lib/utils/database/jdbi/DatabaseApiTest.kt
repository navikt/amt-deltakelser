package no.nav.amt.lib.utils.database.jdbi

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.testing.TestPostgresContainer
import no.nav.amt.lib.utils.database.DatabaseInit
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

private interface TestJdbiRepository : Repository {
    @SqlQuery("SELECT 1")
    fun one(): Int

    @SqlQuery("SELECT :value")
    fun value(@Bind("value") value: Int): Int
}

class ProductionDatabaseApiTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            TestPostgresContainer.bootstrap()
        }
    }

    private val databaseApi = DatabaseApi(
        ApplicationJdbiHandleProvider(
            createJdbi(DatabaseInit.createDataSource(TestPostgresContainer.databaseConfig())),
        ),
    )

    @Test
    fun `ApplicationJdbiHandleProvider does not keep a transaction alive across independent calls`() {
        databaseApi.forbindelse { it.erTransaksjon() } shouldBe false
        databaseApi.forbindelse { it.erTransaksjon() } shouldBe false

        databaseApi.transaksjon { tx ->
            tx.bruk(TestJdbiRepository::class) { repository ->
                repository.one() shouldBe 1
            }

            databaseApi.forbindelse { connection ->
                connection.erTransaksjon() shouldBe true
            }
        }

        databaseApi.forbindelse { it.erTransaksjon() } shouldBe false
    }

    @Test
    fun `ApplicationJdbiHandleProvider does not share state between threads`() {
        val otherThreadState = CompletableFuture<Boolean>()
        val otherThreadConnState = CompletableFuture<Boolean>()

        val thread = Thread {
            otherThreadState.complete(databaseApi.forbindelse { it.erTransaksjon() })
            otherThreadConnState.complete(databaseApi.transaksjon { tx ->
                tx.bruk(TestJdbiRepository::class) { repository ->
                    repository.value(42) shouldBe 42
                }
                databaseApi.forbindelse { it.erTransaksjon() }
            })
        }

        thread.start()
        thread.join()

        otherThreadState.get() shouldBe false
        otherThreadConnState.get() shouldBe true

        databaseApi.forbindelse { it.erTransaksjon() } shouldBe false
    }
}
