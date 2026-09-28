package no.nav.amt.lib.testing

import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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

    @Test
    fun `@Transaction metoder skal ikke starte coroutines med launch eller async`() {
        val importedClasses = ClassFileImporter().importClasses(
            JdbiDemonstrationTest::class.java,
            DemoTransactionDao::class.java,
        )

        val rule = methods()
            .that()
            .areAnnotatedWith(Transaction::class.java)
            .and()
            .areDeclaredIn(DemoTransactionDao::class.java)
            .should(notLaunchOrAsync())

        shouldThrow<AssertionError> {
            rule.check(importedClasses)
        }
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

        @Suppress("unused", "UNUSED_EXPRESSION")
        @Transaction
        fun insertInTransactionWithCoroutineViolation(value: String) {
            insert(value)
            val job = CoroutineScope(Dispatchers.IO).launch { "" }
            val deferred = CoroutineScope(Dispatchers.IO).async { "" }
            job.cancel()
            deferred.cancel()
        }
    }

    private fun notLaunchOrAsync(): ArchCondition<JavaMethod> = object : ArchCondition<JavaMethod>(
        "not call kotlinx.coroutines launch or async",
    ) {
        override fun check(
            method: JavaMethod,
            events: ConditionEvents,
        ) {
            method.methodCallsFromSelf
                .filter { call ->
                    call.targetOwner.packageName.startsWith("kotlinx.coroutines") &&
                        (call.name.startsWith("launch") || call.name.startsWith("async"))
                }.forEach { call ->
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            "${method.fullName} calls ${call.targetOwner.fullName}.${call.name} at line ${call.lineNumber}",
                        ),
                    )
                }
        }
    }
}
