package no.nav.amt.lib.testing

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.utils.database.Database
import org.jdbi.v3.core.Handle
import org.jdbi.v3.sqlobject.SqlObject
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.transaction.Transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class JdbiDemonstrationTest {
    companion object {
        @RegisterExtension
        @JvmField
        val db = JdbiDatabaseTestExtension()
    }

    private val dao: DemoTransactionDao by lazy { db.use(DemoTransactionDao::class) }

    @BeforeEach
    fun setup() {
        Database.jdbi.withHandle<Unit, Exception> { handle ->
            handle.execute(
                """
                CREATE TABLE IF NOT EXISTS demo_transaction_demo (
                    id SERIAL PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """.trimIndent(),
            )
            handle.execute("TRUNCATE TABLE demo_transaction_demo")
        }
    }

    @Test
    fun `nøsting av transaksjoner gjør at ytterste transaksjon er gjeldende hele tiden`() {
        Database.jdbi.withHandle<Unit, Exception> { handle: Handle ->
            val dao = handle.attach(DemoTransactionDao::class.java)
            handle.begin()
            dao.insertInTransaction("outer")
            dao.insertInTransaction("inner")
            handle.rollback()
        }

        dao.count() shouldBe 0
    }

    @Suppress("SqlResolve")
    interface DemoTransactionDao : SqlObject {
        @SqlQuery("SELECT COUNT(*) FROM demo_transaction_demo")
        fun count(): Int

        @SqlUpdate("INSERT INTO demo_transaction_demo (value) VALUES (:value)")
        fun insert(
            @Bind("value") value: String,
        )

        @Transaction
        fun insertInTransaction(value: String) {
            insert(value)
        }
    }
}
