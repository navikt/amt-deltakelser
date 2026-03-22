package no.nav.amt.lib.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class GenericCacheTest {
    val idInTest: UUID = UUID.randomUUID()
    val cache = GenericCache(
        cacheName = "fooCache",
        items = listOf("foo"),
        idSelector = { idInTest },
    )

    @Test
    fun `getOrThrow - skal returnere cachet verdi`() {
        cache.getOrThrow(idInTest) shouldBe "foo"
    }

    @Test
    fun `getOrThrow - skal kaste feil hvis nokkel ikke finnes i cache`() {
        shouldThrow<NoSuchElementException> {
            cache.getOrThrow(UUID.randomUUID())
        }
    }
}
