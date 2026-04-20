package no.nav.amt.lib.utils

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.LocalDateTime

class ObjectMapperTest {
    @Test
    fun `LocalDateTime serialiseres som ISO-8601 string`() {
        val dateTime = LocalDateTime.of(2026, 11, 23, 12, 34, 56)

        val json = objectMapper.writeValueAsString(dateTime)

        json shouldBe "\"2026-11-23T12:34:56\""
    }

    @Test
    fun `LocalDateTime deserialiseres fra ISO-8601 string`() {
        val json = "\"2026-11-23T12:34:56\""

        val dateTime = objectMapper.readValue<LocalDateTime>(json)

        dateTime shouldBe LocalDateTime.of(2026, 11, 23, 12, 34, 56)
    }

    @Test
    fun `LocalDate serialiseres som ISO-8601 string`() {
        val date = LocalDate.of(2026, 11, 23)

        val json = objectMapper.writeValueAsString(date)

        json shouldBe "\"2026-11-23\""
    }

    @Test
    fun `ukjente felter ignoreres ved deserialisering`() {
        val json = """{"name":"test","unknown":"ignored"}"""

        val result = objectMapper.readValue<TestDto>(json)

        result.name shouldBe "test"
    }

    @Test
    fun `serialisering produserer kompakt JSON`() {
        val dto = TestDto("test")

        val json = objectMapper.writeValueAsString(dto)

        json shouldNotContain "\n"
        json shouldBe """{"name":"test"}"""
    }

    private data class TestDto(
        val name: String,
    )
}
