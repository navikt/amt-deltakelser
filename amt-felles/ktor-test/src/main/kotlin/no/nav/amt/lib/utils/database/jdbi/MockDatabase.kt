package no.nav.amt.lib.utils.database.jdbi
import io.mockk.every
import io.mockk.mockk

/**
 * En enkel wrapper over mockk() slik at det blir enklere å skrive unittester med
 * mock-databasekall der koden forventer et [DatabaseApi].
 */
class MockDatabase private constructor() {
    val db: DatabaseApi = mockk(relaxed = true)

    inline fun <reified T : Repository> repo(configure: T.() -> Unit): MockDatabase {
        val repository = mockk<T>(relaxed = true)
        repository.configure()
        every {
            db.repo(T::class, any<(T) -> Any?>())
        } answers {
            val block = secondArg<(T) -> Any?>()
            block(repository)
        }
        return this
    }

    companion object {
        fun create(configure: MockDatabase.() -> Unit): DatabaseApi = MockDatabase().apply(configure).db
    }
}
