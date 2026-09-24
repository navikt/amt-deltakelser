package no.nav.amt.deltaker.bff.model

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test
import java.time.Duration

class DeltakerModelTest {
    @Test
    fun `softMaxVarighet for arbeidsforberedende trening er to år`() {
        val deltaker = TestData.lagDeltaker(
            gjennomforing = TestData.lagGjennomforingModel(
                tiltak = TestData.lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
            ),
        )

        deltaker.softMaxVarighet shouldBe Duration.ofDays(365L * 2)
    }

    @Test
    fun `softMaxVarighet for oppfølging avhenger av innsatsgruppe`() {
        val deltakerMedHoyInnsats = TestData.lagDeltaker(
            navBruker = TestData.lagNavBrukerModel(innsatsgruppe = Innsatsgruppe.SPESIELT_TILPASSET_INNSATS),
            gjennomforing = TestData.lagGjennomforingModel(
                tiltak = TestData.lagTiltakstype(tiltakskode = Tiltakskode.OPPFOLGING),
            ),
        )

        val deltakerMedLavInnsats = TestData.lagDeltaker(
            navBruker = TestData.lagNavBrukerModel(innsatsgruppe = Innsatsgruppe.STANDARD_INNSATS),
            gjennomforing = TestData.lagGjennomforingModel(
                tiltak = TestData.lagTiltakstype(tiltakskode = Tiltakskode.OPPFOLGING),
            ),
        )

        deltakerMedHoyInnsats.softMaxVarighet shouldBe Duration.ofDays(365L * 3)
        deltakerMedLavInnsats.softMaxVarighet shouldBe null
    }

    @Test
    fun `maxVarighet for arbeidsrettet rehabilitering inkluderer ferieavdrag`() {
        val deltaker = TestData.lagDeltaker(
            gjennomforing = TestData.lagGjennomforingModel(
                tiltak = TestData.lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSRETTET_REHABILITERING),
            ),
        )

        deltaker.maxVarighet shouldBe Duration.ofDays((12 + 5L) * 7L)
    }

    @Test
    fun `maxVarighet for oppfølging bruker innsatsgruppe og ekstra seks måneder`() {
        val deltakerMedHoyInnsats = TestData.lagDeltaker(
            navBruker = TestData.lagNavBrukerModel(innsatsgruppe = Innsatsgruppe.VARIG_TILPASSET_INNSATS),
            gjennomforing = TestData.lagGjennomforingModel(
                tiltak = TestData.lagTiltakstype(tiltakskode = Tiltakskode.OPPFOLGING),
            ),
        )

        val deltakerUtenInnsats = TestData.lagDeltaker(
            navBruker = TestData.lagNavBrukerModel(innsatsgruppe = null),
            gjennomforing = TestData.lagGjennomforingModel(
                tiltak = TestData.lagTiltakstype(tiltakskode = Tiltakskode.OPPFOLGING),
            ),
        )

        deltakerMedHoyInnsats.maxVarighet shouldBe Duration.ofDays((365L * 3) + (6L * 30L))
        deltakerUtenInnsats.maxVarighet shouldBe null
    }
}
