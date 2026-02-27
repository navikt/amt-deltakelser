package no.nav.amt.deltaker.deltakerliste.kafka

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.deltakerliste.toModel
import no.nav.amt.deltaker.utils.data.TestData.lagArrangor
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerlistePayload
import no.nav.amt.deltaker.utils.data.TestData.lagEnkeltplassDeltakerlistePayload
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GjennomforingV2KafkaPayloadExtensionsTest {
    @Nested
    inner class ToModel {
        @Test
        fun `toModel - mapper felter korrekt`() {
            val payload = lagDeltakerlistePayload(arrangor = arrangorInTest)

            val deltakerliste = payload.toModel(arrangorInTest, tiltakstypeInTest)

            assertSoftly(deltakerliste) {
                id shouldBe payload.id
                tiltakstype shouldBe tiltakstypeInTest
                navn shouldBe "Test Deltakerliste OPPFOLGING"
                tiltakstype shouldBe tiltakstypeInTest
                startDato shouldBe payload.startDato
                sluttDato shouldBe payload.sluttDato
                status shouldBe GjennomforingStatusType.GJENNOMFORES
                oppstart shouldBe Oppstartstype.LOPENDE
                apentForPamelding.shouldBeTrue()
                arrangor shouldBe arrangorInTest
            }
        }

        @Test
        fun `toModel - bruker tiltakstype-navn for enkeltplass`() {
            val payload = lagEnkeltplassDeltakerlistePayload(arrangor = arrangorInTest)

            val deltakerliste = payload.toModel(arrangorInTest, tiltakstypeInTest)

            assertSoftly(deltakerliste) {
                id shouldBe payload.id
                tiltakstype shouldBe tiltakstypeInTest
                navn shouldBe "Test tiltak ${tiltakstypeInTest.tiltakskode.name}"
                tiltakstype shouldBe tiltakstypeInTest
                startDato shouldBe null
                sluttDato shouldBe null
                status shouldBe null
                oppstart shouldBe null
                apentForPamelding.shouldBeTrue()
                arrangor shouldBe arrangorInTest
            }
        }
    }

    companion object {
        private val arrangorInTest = lagArrangor()
        val tiltakstypeInTest = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING)
    }
}
