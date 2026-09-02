package no.nav.amt.lib.models.deltakerliste.tiltakstype

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskoder.skalKometLagreTiltakstype
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test

class TiltakskoderTest {
    @Test
    fun `skal lagre nar system-felt mangler`() {
        // Arrange
        val tiltakAsJson = """{"id":"123","navn":"Tiltak uten system"}"""

        // Act
        val resultat = skalKometLagreTiltakstype(tiltakAsJson, objectMapper)

        // Assert
        resultat shouldBe true
    }

    @Test
    fun `skal lagre nar system er null`() {
        // Arrange
        val tiltakAsJson = """{"id":"123","system":null}"""

        // Act
        val resultat = skalKometLagreTiltakstype(tiltakAsJson, objectMapper)

        // Assert
        resultat shouldBe true
    }

    @Test
    fun `skal lagre nar system er TILTAKSADMINISTRASJON`() {
        // Arrange
        val tiltakAsJson = """{"id":"123","system":"TILTAKSADMINISTRASJON"}"""

        // Act
        val resultat = skalKometLagreTiltakstype(tiltakAsJson, objectMapper)

        // Assert
        resultat shouldBe true
    }

    @Test
    fun `skal ikke lagre nar system er ARENA`() {
        // Arrange
        val tiltakAsJson = """{"id":"123","system":"ARENA"}"""

        // Act
        val resultat = skalKometLagreTiltakstype(tiltakAsJson, objectMapper)

        // Assert
        resultat shouldBe false
    }
}
