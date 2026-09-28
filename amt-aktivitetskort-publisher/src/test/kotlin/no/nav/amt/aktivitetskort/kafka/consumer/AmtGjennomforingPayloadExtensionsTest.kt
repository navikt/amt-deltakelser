package no.nav.amt.aktivitetskort.kafka.consumer

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.database.TestData
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test
import java.util.UUID

class AmtGjennomforingPayloadExtensionsTest {
    private val arrangorId = UUID.randomUUID()

    @Test
    fun `toTiltakstype - mapper felter fra embeddet tiltak`() {
        val payload = TestData.lagAmtGjennomforingPayload()

        val tiltakstype = payload.toTiltakstype()

        assertSoftly(tiltakstype) {
            id shouldBe payload.tiltak.id
            navn shouldBe payload.tiltak.navn
            tiltakskode shouldBe payload.tiltak.tiltakskode
        }
    }

    @Test
    fun `toDeltakerliste - gruppe - mapper alle felter`() {
        val payload = TestData
            .lagAmtGjennomforingPayload()
            .copy(
                type = GjennomforingType.Gruppe,
                navn = "~gruppenavn~",
                status = GjennomforingStatusType.AVLYST,
                oppstart = Oppstartstype.FELLES,
                pameldingstype = GjennomforingPameldingType.DIREKTE_VEDTAK,
            )

        val deltakerliste = payload.toDeltakerliste(arrangorId)

        assertSoftly(deltakerliste) {
            id shouldBe payload.id
            navn shouldBe "~gruppenavn~"
            this.arrangorId shouldBe arrangorId
            tiltak.navn shouldBe payload.tiltak.navn
            tiltak.tiltakskode shouldBe payload.tiltak.tiltakskode
        }
    }

    @Test
    fun `toDeltakerliste - enkeltplass - bruker tiltaksnavn som navn`() {
        val payload = TestData
            .lagAmtGjennomforingPayload()
            .copy(
                type = GjennomforingType.Enkeltplass,
                navn = null,
            )

        val deltakerliste = payload.toDeltakerliste(arrangorId)

        deltakerliste.navn shouldBe payload.tiltak.navn
    }

    @Test
    fun `toDeltakerliste - enkeltplass med navn satt - foretrekker likevel tiltaksnavn`() {
        val payload = TestData
            .lagAmtGjennomforingPayload()
            .copy(
                type = GjennomforingType.Enkeltplass,
                navn = "~skal-ikke-brukes~",
            )

        payload.toDeltakerliste(arrangorId).navn shouldBe payload.tiltak.navn
    }

    @Test
    fun `toDeltakerliste - gruppe uten navn - faller tilbake til tiltaksnavn`() {
        val payload = TestData
            .lagAmtGjennomforingPayload()
            .copy(
                type = GjennomforingType.Gruppe,
                navn = null,
            )

        payload.toDeltakerliste(arrangorId).navn shouldBe payload.tiltak.navn
    }

    @Test
    fun `toDeltakerliste - beholder tiltakskode fra payload`() {
        val payload = TestData.lagAmtGjennomforingPayload()

        payload.toDeltakerliste(arrangorId).tiltak.tiltakskode shouldBe payload.tiltak.tiltakskode
        payload.tiltak.tiltakskode shouldBe Tiltakskode.OPPFOLGING
    }
}
