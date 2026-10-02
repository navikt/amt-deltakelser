package no.nav.amt.lib.utils.database.jdbi

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.testing.JdbiDatabaseTestExtension
import org.jdbi.v3.core.Handle
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CompletableFuture

private interface TestJdbiRepository : Repository {
    @SqlQuery("SELECT 1")
    fun one(): Int

    @SqlQuery("SELECT :value")
    fun value(
        @Bind("value") value: Int,
    ): Int
}

class DatabaseApiTest {
    companion object {
        @RegisterExtension
        @JvmField
        val db = JdbiDatabaseTestExtension()
    }

    @Test
    fun `forbindelse og transaksjon gjenbrukes på samme tråd`() {
        val originalHandle = db.activeHandle()!!

        db.dbApi.forbindelse { connection ->
            connection.erTransaksjon() shouldBe true
            val repository = connection.bruk(TestJdbiRepository::class)
            repository.one() shouldBe 1
            db.activeHandle() shouldBe originalHandle
        }

        db.dbApi.transaksjon { tx ->
            db.activeHandle() shouldBe originalHandle
            val result = tx.bruk(TestJdbiRepository::class) { repository ->
                repository.value(42)
            }
            result shouldBe 42

            db.dbApi.forbindelse { connection ->
                connection.erTransaksjon() shouldBe true
                db.activeHandle() shouldBe originalHandle
            }
        }
    }

    @Test
    fun `different threads do not reuse the active handle or transaction state`() {
        val originalHandle = db.activeHandle()!!

        val handleOnThread = CompletableFuture<Handle?>()
        val transactionStateOnThread = CompletableFuture<Boolean>()

        val thread = Thread {
            handleOnThread.complete(db.activeHandle())
            transactionStateOnThread.complete(
                db.dbApi.forbindelse { it.erTransaksjon() },
            )
        }

        thread.start()
        thread.join()

        handleOnThread.get() shouldBe null
        transactionStateOnThread.get() shouldBe false
        db.activeHandle() shouldBe originalHandle
    }
}
