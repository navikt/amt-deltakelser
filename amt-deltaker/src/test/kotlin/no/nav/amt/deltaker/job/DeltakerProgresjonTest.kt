package no.nav.amt.deltaker.job

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusMedDeltakerId
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerlisteMedDirekteVedtak
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerlisteMedTrengerGodkjenning
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerProgresjonTest {
    @BeforeEach
    fun setup() = mockkObject(DeltakerStatusRepository)

    @AfterEach
    fun teardown() = unmockkObject(DeltakerStatusRepository)

    @Test
    fun `getAvsluttendeStatusUtfall - ingen deltakere - returnerer tom liste`() {
        val oppdatertDeltakere = DeltakerProgresjonHandler.getAvsluttendeStatusUtfall(emptyList())
        oppdatertDeltakere shouldBe emptyList()
    }

    @Test
    fun `getAvsluttendeStatusUtfall - deltaker deltatt paa opplaering - status fullfort`() {
        val yesterday = LocalDate.now().minusDays(1)
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(
                tiltakskode = Tiltakskode.HOYERE_UTDANNING,
            ),
        )
        val deltaker = lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = deltakerliste.startDato,
            sluttdato = yesterday,
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )

        every { DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(any()) } returns emptyList()

        val oppdatertDeltaker = DeltakerProgresjonHandler
            .getAvsluttendeStatusUtfall(listOf(deltaker))
            .first()

        assertSoftly(oppdatertDeltaker) {
            startdato shouldBe deltaker.startdato
            sluttdato shouldBe deltaker.sluttdato
            status.type shouldBe DeltakerStatus.Type.FULLFORT
            status.aarsak shouldBe null
        }
    }

    @Test
    fun `getAvsluttendeStatusUtfall - deltaker deltatt paa lopende oppstart - status har sluttet`() {
        val yesterday = LocalDate.now().minusDays(1)
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(
                tiltakskode = Tiltakskode.HOYERE_UTDANNING,
            ),
            oppstart = Oppstartstype.LOPENDE,
        )
        val deltaker = lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = deltakerliste.startDato,
            sluttdato = yesterday,
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )

        every { DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(any()) } returns emptyList()

        val oppdatertDeltaker = DeltakerProgresjonHandler
            .getAvsluttendeStatusUtfall(listOf(deltaker))
            .first()

        assertSoftly(oppdatertDeltaker) {
            startdato shouldBe deltaker.startdato
            sluttdato shouldBe deltaker.sluttdato
            status.type shouldBe DeltakerStatus.Type.FULLFORT
            status.aarsak shouldBe null
        }
    }

    @Test
    fun `getAvsluttendeStatusUtfall - deltar avbrutt deltakerliste - far riktig status og arsak`() {
        val deltakerliste = lagDeltakerlisteMedTrengerGodkjenning().copy(status = GjennomforingStatusType.AVBRUTT)
        val deltaker = lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = deltakerliste.startDato,
            sluttdato = deltakerliste.sluttDato,
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )

        every { DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(any()) } returns emptyList()

        val oppdatertDeltaker = DeltakerProgresjonHandler
            .getAvsluttendeStatusUtfall(listOf(deltaker))
            .first()

        assertSoftly(oppdatertDeltaker) {
            sluttdato shouldBe LocalDate.now()
            status.type shouldBe DeltakerStatus.Type.AVBRUTT
            status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
        }
    }

    @Test
    fun `getAvsluttendeStatusUtfall - venter avbrutt deltakerliste - far riktig status og arsak`() {
        val deltakerliste = lagDeltakerlisteMedTrengerGodkjenning().copy(status = GjennomforingStatusType.AVBRUTT)
        val deltaker = lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = deltakerliste.startDato,
            sluttdato = deltakerliste.sluttDato,
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )

        every { DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(any()) } returns emptyList()

        val oppdatertDeltaker = DeltakerProgresjonHandler
            .getAvsluttendeStatusUtfall(listOf(deltaker))
            .first()

        assertSoftly(oppdatertDeltaker) {
            startdato shouldBe null
            sluttdato shouldBe null
            status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
            status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
        }
    }

    @Test
    fun `getAvsluttendeStatusUtfall - fremtig avsluttende status - returnerer deltaker med neste status`() {
        val deltakerliste = lagDeltakerlisteMedDirekteVedtak().copy(status = GjennomforingStatusType.AVBRUTT)
        val deltaker = lagDeltaker(
            deltakerliste = deltakerliste,
            startdato = deltakerliste.startDato,
            sluttdato = deltakerliste.sluttDato,
            status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )

        val fremtidigStatus =
            DeltakerStatus(
                UUID.randomUUID(),
                DeltakerStatus.Type.HAR_SLUTTET,
                aarsak = null,
                gyldigFra = LocalDateTime.now().minusHours(1),
                gyldigTil = null,
                opprettet = LocalDateTime.now().minusDays(2),
            )

        every { DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(any()) } returns listOf(
            DeltakerStatusMedDeltakerId(fremtidigStatus, deltaker.id),
        )

        val oppdatertDeltaker = DeltakerProgresjonHandler
            .getAvsluttendeStatusUtfall(listOf(deltaker))
            .first()

        assertSoftly(oppdatertDeltaker) {
            startdato shouldBe deltaker.startdato
            sluttdato shouldBe LocalDate.now()
            status shouldBe fremtidigStatus
        }
    }
}
