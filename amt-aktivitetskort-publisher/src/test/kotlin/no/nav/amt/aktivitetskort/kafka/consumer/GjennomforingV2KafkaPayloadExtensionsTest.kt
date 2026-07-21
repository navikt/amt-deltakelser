package no.nav.amt.aktivitetskort.kafka.consumer

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.database.TestData.lagArrangor
import no.nav.amt.aktivitetskort.database.TestData.lagDeltakerliste
import no.nav.amt.aktivitetskort.database.TestData.lagEnkeltplassDeltakerlistePayload
import no.nav.amt.aktivitetskort.database.TestData.lagGruppeDeltakerlistePayload
import no.nav.amt.aktivitetskort.database.TestData.lagTiltak
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test

class GjennomforingV2KafkaPayloadExtensionsTest {
    @Test
    fun `toModel gruppe - mapper felter korrekt`() {
        val tiltakInTest = lagTiltak(tiltakskode = Tiltakskode.JOBBKLUBB)

        val deltakerlisteInTest = lagDeltakerliste(
            tiltak = tiltakInTest,
            arrangorId = arrangorIdInTest,
        )

        val payload: GjennomforingV2KafkaPayload.Gruppe = lagGruppeDeltakerlistePayload(
            deltakerliste = deltakerlisteInTest,
            arrangor = arrangorInTest,
        )

        val model = payload.toModel(
            arrangorId = arrangorIdInTest,
            navnTiltakstype = tiltakInTest.navn,
        )

        assertSoftly(model) {
            id shouldBe deltakerlisteInTest.id
            navn shouldBe deltakerlisteInTest.navn
            tiltak shouldBe tiltakInTest
            arrangorId shouldBe deltakerlisteInTest.arrangorId
        }
    }

    @Test
    fun `toModel enkeltplass - mapper felter korrekt`() {
        val tiltakInTest = lagTiltak(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING)

        val deltakerListeInTest = lagDeltakerliste(
            tiltak = tiltakInTest,
            arrangorId = arrangorIdInTest,
        )

        val payload = lagEnkeltplassDeltakerlistePayload(arrangor = arrangorInTest, deltakerliste = deltakerListeInTest)

        val model = payload.toModel(
            arrangorId = arrangorIdInTest,
            navnTiltakstype = tiltakInTest.navn,
        )

        assertSoftly(model) {
            id shouldBe deltakerListeInTest.id
            navn shouldBe tiltakInTest.navn
            tiltak shouldBe tiltakInTest
            arrangorId shouldBe deltakerListeInTest.arrangorId
        }
    }

    companion object {
        private val arrangorInTest = lagArrangor()
        private val arrangorIdInTest = arrangorInTest.id
    }
}
