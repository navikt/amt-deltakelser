package no.nav.amt.deltaker.model

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue

class DeltakerlisteTest {
    @Nested
    inner class ErAvlystEllerAvbrutt {
        @Test
        fun `erAvlystEllerAvbrutt - AVLYST - returnerer true`() {
            // Arrange
            val deltakerliste = TestData.lagDeltakerliste(status = GjennomforingStatusType.AVLYST)

            // Act
            val erAvlystEllerAvbrutt = deltakerliste.erAvlystEllerAvbrutt()

            // Assert
            erAvlystEllerAvbrutt shouldBe true
        }

        @Test
        fun `erAvlystEllerAvbrutt - GJENNOMFORES - returnerer false`() {
            // Arrange
            val deltakerliste = TestData.lagDeltakerliste(status = GjennomforingStatusType.GJENNOMFORES)

            // Act
            val erAvlystEllerAvbrutt = deltakerliste.erAvlystEllerAvbrutt()

            // Assert
            erAvlystEllerAvbrutt shouldBe false
        }
    }

    @Nested
    inner class DeltakelserMaaGodkjennes {
        @Test
        fun `deltakelserMaaGodkjennes - TRENGER_GODKJENNING - returnerer true`() {
            // Arrange
            val deltakerliste = TestData.lagDeltakerliste(pameldingType = GjennomforingPameldingType.TRENGER_GODKJENNING)

            // Act
            val deltakelserMaaGodkjennes = deltakerliste.deltakelserMaaGodkjennes

            // Assert
            deltakelserMaaGodkjennes shouldBe true
        }
    }

    @Nested
    inner class ErFellesOppstart {
        @Test
        fun `erFellesOppstart - FELLES - returnerer true`() {
            // Arrange
            val deltakerliste = TestData.lagDeltakerliste(oppstart = Oppstartstype.FELLES)

            // Act
            val erFellesOppstart = deltakerliste.erFellesOppstart

            // Assert
            erFellesOppstart shouldBe true
        }
    }

    @Nested
    inner class Serialisering {
        @Test
        fun `get-felt serialiseres ikke til JSON`() {
            // Arrange
            val deltakerliste = TestData.lagDeltakerliste(oppstart = Oppstartstype.FELLES)

            // Act
            val json = objectMapper.writeValueAsString(deltakerliste)
            val jsonMap: Map<String, Any?> = objectMapper.readValue(json)

            // Assert
            jsonMap.containsKey("nyForskriftOpplaring") shouldBe false
            jsonMap.containsKey("erFellesOppstart") shouldBe false
            jsonMap.containsKey("deltakelserMaaGodkjennes") shouldBe false
            jsonMap.containsKey("avslutningstype") shouldBe false
            jsonMap.containsKey("harFellesAvslutning") shouldBe false
            jsonMap.containsKey("erDeltMedValp") shouldBe false
        }
    }
}
