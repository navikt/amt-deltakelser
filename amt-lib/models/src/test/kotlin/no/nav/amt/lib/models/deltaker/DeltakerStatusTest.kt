package no.nav.amt.lib.models.deltaker

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import no.nav.amt.lib.models.deltaker.DeltakerStatus.Aarsak
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.utils.TestData.lagDeltakerStatus
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class DeltakerStatusTest {
    @Nested
    inner class HarLiktInnholdSomTests {
        val deltakerStatusInTest = lagDeltakerStatus().copy(
            type = DeltakerStatus.Type.VENTER_PA_OPPSTART,
            aarsak = Aarsak(
                type = Aarsak.Type.AVLYST_KONTRAKT,
                beskrivelse = null,
            ),
        )

        @Test
        fun `kun forskjellig id - skal returnere true`() {
            val otherDeltakerStatus = deltakerStatusInTest.copy(id = UUID.randomUUID())

            deltakerStatusInTest.harLiktInnholdSom(otherDeltakerStatus).shouldBeTrue()
        }

        @Test
        fun `forskjellig type - skal returnere false`() {
            val otherDeltakerStatus = deltakerStatusInTest.copy(
                id = UUID.randomUUID(),
                type = DeltakerStatus.Type.DELTAR,
            )

            deltakerStatusInTest.harLiktInnholdSom(otherDeltakerStatus).shouldBeFalse()
        }

        @Test
        fun `forskjellig aarsak - skal returnere false`() {
            val otherDeltakerStatus = deltakerStatusInTest.copy(
                id = UUID.randomUUID(),
                aarsak = Aarsak(
                    type = Aarsak.Type.SYK,
                    beskrivelse = null,
                ),
            )

            deltakerStatusInTest.harLiktInnholdSom(otherDeltakerStatus).shouldBeFalse()
        }
    }
}
