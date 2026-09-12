package no.nav.amt.lib.utils

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DatabaseUtilsTest {
    @Test
    fun `toPGObject - serialiserer verdi til json`() {
        val pgObject = objectMapper.toPGObject(mapOf("navn" to "test", "antall" to 2))

        pgObject.type shouldBe "json"
        pgObject.value shouldBe """{"navn":"test","antall":2}"""
    }

    @Test
    fun `polymorphicToPGObject - serialiserer polymorf liste med typeinfo`() {
        val value = listOf<Hendelse>(Hendelse.Startet("123"))

        val pgObject = objectMapper.polymorphicToPGObject(value)

        pgObject.type shouldBe "json"
        pgObject.value shouldBe """[{"type":"startet","id":"123"}]"""
    }

    @Test
    fun `polymorphicToPGObject - serialiserer enkeltverdi med typeinfo`() {
        val pgObject = objectMapper.polymorphicToPGObject(Hendelse.Startet("abc"))

        pgObject.type shouldBe "json"
        pgObject.value shouldBe """{"type":"startet","id":"abc"}"""
    }

    @Test
    fun `polymorphicToPGObject - serialiserer enkeltverdi med typeinfo likt som toPGObject`() {
        val polymorphicPGObject = objectMapper.polymorphicToPGObject(Hendelse.Startet("abc"))
        val pGObject = objectMapper.toPGObject(Hendelse.Startet("abc"))

        polymorphicPGObject shouldBe pGObject
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = Hendelse.Startet::class, name = "startet"),
        JsonSubTypes.Type(value = Hendelse.Stoppet::class, name = "stoppet"),
    )
    private sealed interface Hendelse {
        data class Startet(
            val id: String,
        ) : Hendelse

        data class Stoppet(
            val id: String,
        ) : Hendelse
    }
}
