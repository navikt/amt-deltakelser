package no.nav.amt.deltaker.bff.gjennomforing

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerliste
import no.nav.amt.deltaker.bff.utils.TestData.lagEnkeltplassDeltakerlistePayload
import no.nav.amt.deltaker.bff.utils.TestData.lagGruppeDeltakerlistePayload
import no.nav.amt.deltaker.bff.utils.TestData.lagTiltakstype
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import org.junit.jupiter.api.Test

class GjennomforingV2KafkaPayloadExtensionsTest {
    @Test
    fun `toModel gruppe - mapper felter korrekt`() {
        val tiltakstypeInTest = lagTiltakstype(tiltakskode = Tiltakskode.JOBBKLUBB)

        val deltakerListeInTest = lagDeltakerliste(
            tiltakstype = tiltakstypeInTest,
            arrangor = arrangorInTest,
        )

        val payload = lagGruppeDeltakerlistePayload(deltakerliste = deltakerListeInTest)

        val model = payload.toModel(arrangorInTest, tiltakstypeInTest)

        assertSoftly(model) {
            it.tiltak shouldBe deltakerListeInTest.tiltak
            it.arrangor.arrangor shouldBe arrangorInTest

            id shouldBe id
            navn shouldBe deltakerListeInTest.navn
            startDato shouldBe deltakerListeInTest.startDato
            sluttDato shouldBe deltakerListeInTest.sluttDato
            status shouldBe deltakerListeInTest.status
            oppstart shouldBe deltakerListeInTest.oppstart
            apentForPamelding shouldBe deltakerListeInTest.apentForPamelding
            antallPlasser shouldBe deltakerListeInTest.antallPlasser
        }
    }

    @Test
    fun `toModel enkeltplass - mapper felter korrekt`() {
        // Arrange
        val tiltakstypeInTest = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING)

        val deltakerListeInTest = lagDeltakerliste(
            navn = tiltakstypeInTest.tiltakskode.name,
            tiltakstype = tiltakstypeInTest,
            arrangor = arrangorInTest,
            oppstart = Oppstartstype.ENKELTPLASS,
            status = GjennomforingStatusType.GJENNOMFORES,
        ).copy(navn = tiltakstypeInTest.tiltakskode.name)

        val payload = lagEnkeltplassDeltakerlistePayload(deltakerliste = deltakerListeInTest)

        // Act
        val model = payload.toModel(
            arrangor = arrangorInTest,
            tiltakstype = tiltakstypeInTest,
        )

        // Assert
        assertSoftly(model) {
            it.tiltak shouldBe deltakerListeInTest.tiltak
            it.arrangor.arrangor shouldBe arrangorInTest

            id shouldBe id
            navn shouldBe deltakerListeInTest.tiltak.navn
            status shouldBe deltakerListeInTest.status
            startDato.shouldBeNull()
            sluttDato.shouldBeNull()
            oppstart shouldBe deltakerListeInTest.oppstart
            apentForPamelding.shouldBeTrue()
            antallPlasser.shouldBeNull()
        }
    }

    companion object {
        private val arrangorInTest = lagArrangor()
    }
}
