package no.nav.amt.internapi.deltaker.response

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PaginatedResultTest {
    @Test
    fun `skal beregne totalPages naar totalCount gaar opp i pageSize`() {
        val result = PaginatedResult(
            totalCount = 100,
            pageSize = 25,
            data = listOf("a"),
        )

        result.totalPages shouldBe 4
    }

    @Test
    fun `skal runde totalPages opp naar siste side ikke er full`() {
        val result = PaginatedResult(
            totalCount = 101,
            pageSize = 25,
            data = listOf("a"),
        )

        result.totalPages shouldBe 5
    }

    @Test
    fun `skal returnere 0 totalPages naar totalCount er 0`() {
        val result = PaginatedResult<String>(
            totalCount = 0,
            pageSize = 25,
            data = emptyList(),
        )

        result.totalPages shouldBe 0
    }

    @Test
    fun `skal tillate default pageSize naar totalCount er 0`() {
        val result = PaginatedResult<String>(data = emptyList())

        result.totalCount shouldBe 0
        result.pageSize shouldBe 0
        result.totalPages shouldBe 0
    }

    @Test
    fun `skal kaste exception naar totalCount er negativ`() {
        val exception = shouldThrow<IllegalArgumentException> {
            PaginatedResult(
                totalCount = -1,
                pageSize = 25,
                data = listOf("a"),
            )
        }

        exception.message shouldBe "Total count must be greater than or equal to 0"
    }

    @Test
    fun `skal kaste exception naar pageSize er negativ`() {
        val exception = shouldThrow<IllegalArgumentException> {
            PaginatedResult(
                totalCount = 0,
                pageSize = -1,
                data = emptyList<String>(),
            )
        }

        exception.message shouldBe "Page size must be greater than or equal to 0"
    }

    @Test
    fun `skal kaste exception naar pageSize er 0 og totalCount er storre enn 0`() {
        val exception = shouldThrow<IllegalArgumentException> {
            PaginatedResult(
                totalCount = 1,
                pageSize = 0,
                data = listOf("a"),
            )
        }

        exception.message shouldBe "Page size must be greater than 0 when total count is greater than 0"
    }

    @Test
    fun `skal kaste exception naar totalCount er 0 og data ikke er tom`() {
        val exception = shouldThrow<IllegalArgumentException> {
            PaginatedResult(
                totalCount = 0,
                pageSize = 25,
                data = listOf("a"),
            )
        }

        exception.message shouldBe "Total count must be greater than 0 when data is not empty"
    }

    @Test
    fun `skal kaste exception naar pageSize er 0 og totalCount er storre enn 0 selv om data er tom`() {
        val exception = shouldThrow<IllegalArgumentException> {
            PaginatedResult<String>(
                totalCount = 1,
                pageSize = 0,
                data = emptyList(),
            )
        }

        exception.message shouldBe "Page size must be greater than 0 when total count is greater than 0"
    }
}
