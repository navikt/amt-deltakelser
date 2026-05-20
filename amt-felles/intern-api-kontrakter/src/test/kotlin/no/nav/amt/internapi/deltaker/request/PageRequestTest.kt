package no.nav.amt.internapi.deltaker.request

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PageRequestTest {
    @Test
    fun `skal ha fornuftige standardverdier`() {
        val request = PageRequest<String>()

        request.sort shouldBe null
        request.order shouldBe PageRequest.SortDirection.ASC
        request.page shouldBe 1
        request.pageSize shouldBe 200
        request.offset shouldBe 0
    }

    @Test
    fun `skal beregne offset for foerste side`() {
        val request = PageRequest(sort = "NAVN", page = 1, pageSize = 50)

        request.offset shouldBe 0
    }

    @Test
    fun `skal beregne offset for senere sider`() {
        val request = PageRequest(sort = "NAVN", page = 3, pageSize = 50)

        request.offset shouldBe 100
    }

    @Test
    fun `skal tillate sortering og synkende rekkefolge`() {
        val request = PageRequest(
            sort = "STARTDATO",
            order = PageRequest.SortDirection.DESC,
            page = 2,
            pageSize = 25,
        )

        request.sort shouldBe "STARTDATO"
        request.order shouldBe PageRequest.SortDirection.DESC
        request.offset shouldBe 25
    }

    @Test
    fun `skal kaste exception naar page er mindre enn 1`() {
        val exception = shouldThrow<IllegalArgumentException> {
            PageRequest<String>(page = 0)
        }

        exception.message shouldBe "Page must be greater than 0"
    }

    @Test
    fun `skal kaste exception naar pageSize er mindre enn 1`() {
        val exception = shouldThrow<IllegalArgumentException> {
            PageRequest<String>(pageSize = 0)
        }

        exception.message shouldBe "Page size must be greater than 0"
    }
}
