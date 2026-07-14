package no.nav.amt.deltaker.veileder

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import no.nav.amt.deltaker.model.Vedtaksinformasjon
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Anskaffelse
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class InnsokServiceTest {
    private val repository = mockk<InnsokRepository>(relaxed = true)
    private val innsokService = InnsokService(
        repository = repository,
    )

    private val vedtaksinformasjon = Vedtaksinformasjon(
        fattet = LocalDateTime.now(),
        fattetAvNav = false,
        opprettet = LocalDateTime.now(),
        opprettetAv = UUID.randomUUID(),
        opprettetAvEnhet = UUID.randomUUID(),
        sistEndret = LocalDateTime.now(),
        sistEndretAv = UUID.randomUUID(),
        sistEndretAvEnhet = UUID.randomUUID(),
    )

    @Test
    fun `nyttInnsokUtkastGodkjentAvNav - setter startdato og sluttdato fra deltaker`() {
        val startdato = LocalDate.of(2026, 3, 1)
        val sluttdato = LocalDate.of(2026, 6, 30)

        val deltaker = lagDeltaker(
            startdato = startdato,
            sluttdato = sluttdato,
            vedtaksinformasjon = vedtaksinformasjon,
            status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
        )
        val forrigeStatus = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING)

        val innsok = innsokService.nyttInnsokUtkastGodkjentAvNav(deltaker, forrigeStatus)

        assertSoftly(innsok) {
            this.startdato shouldBe startdato
            this.sluttdato shouldBe sluttdato
        }
    }

    @Test
    fun `nyttInnsokUtkastGodkjentAvNav - setter opplaringKategorisering fra deltakerliste`() {
        val kategorisering = OpplaringKategoriseringValg(
            valgteKategoriseringer = setOf(
                OpplaringKategoriseringValg.ValgteFelt(
                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
                    valg = mapOf(UUID.randomUUID() to "Bygg og anlegg"),
                ),
            ),
            valgteSertifiseringer = setOf(
                SertifiseringValg(id = 1, navn = "Truckfører T1"),
            ),
        )

        val deltaker = lagDeltaker(
            deltakerliste = lagDeltakerliste(opplaringKategorisering = kategorisering),
            vedtaksinformasjon = vedtaksinformasjon,
            status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
        )
        val forrigeStatus = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING)

        val innsok = innsokService.nyttInnsokUtkastGodkjentAvNav(deltaker, forrigeStatus)

        innsok.opplaringKategoriseringVedInnsok shouldBe kategorisering
    }

    @Test
    fun `nyttInnsokUtkastGodkjentAvNav - setter deltakelsesinnhold fra deltaker`() {
        val innhold = Deltakelsesinnhold(
            ledetekst = "Innhold i tiltaket",
            innhold = listOf(
                Innhold(tekst = "Arbeidspraksis", innholdskode = "arbeidspraksis", valgt = true, beskrivelse = null),
            ),
        )

        val deltaker = lagDeltaker(
            innhold = innhold,
            vedtaksinformasjon = vedtaksinformasjon,
            status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
        )
        val forrigeStatus = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING)

        val innsok = innsokService.nyttInnsokUtkastGodkjentAvNav(deltaker, forrigeStatus)

        innsok.deltakelsesinnholdVedInnsok shouldBe innhold
    }

    @Test
    fun `nyttInnsokUtkastGodkjentAvNav - enkeltplass setter prisinformasjon fra prisinfo repo`() {
        val pris = 12000
        mockkObject(PrisinfoRepoAdapter)
        every { PrisinfoRepoAdapter.hentPrisinfo(any()) } returns Anskaffelse(pris = pris)
        val deltaker = lagDeltaker(
            deltakerliste = lagDeltakerliste(gjennomforingstype = GjennomforingType.Enkeltplass),
            vedtaksinformasjon = vedtaksinformasjon,
            status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
        )
        val forrigeStatus = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING)

        val innsok = try {
            innsokService.nyttInnsokUtkastGodkjentAvNav(deltaker, forrigeStatus)
        } finally {
            unmockkObject(PrisinfoRepoAdapter)
        }

        innsok.prisinformasjonVedInnsok shouldBe Anskaffelse(pris = pris)
    }

    @Test
    fun `nyttInnsokUtkastGodkjentAvNav - gruppe setter ikke prisinformasjon`() {
        mockkObject(PrisinfoRepoAdapter)
        every { PrisinfoRepoAdapter.hentPrisinfo(any()) } returns Anskaffelse(pris = 1000)
        val deltaker = lagDeltaker(
            deltakerliste = lagDeltakerliste(gjennomforingstype = GjennomforingType.Gruppe),
            vedtaksinformasjon = vedtaksinformasjon,
            status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
        )
        val forrigeStatus = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING)

        val innsok = try {
            innsokService.nyttInnsokUtkastGodkjentAvNav(deltaker, forrigeStatus).also {
                verify(exactly = 0) { PrisinfoRepoAdapter.hentPrisinfo(any()) }
            }
        } finally {
            unmockkObject(PrisinfoRepoAdapter)
        }

        innsok.prisinformasjonVedInnsok shouldBe null
    }

    @Test
    fun `nyttInnsokUtkastGodkjentAvNav - enkeltplass setter dager per uke ved innsok`() {
        mockkObject(PrisinfoRepoAdapter)
        every { PrisinfoRepoAdapter.hentPrisinfo(any()) } returns Anskaffelse(pris = 1000)
        val deltaker = lagDeltaker(
            deltakerliste = lagDeltakerliste(gjennomforingstype = GjennomforingType.Enkeltplass),
            dagerPerUke = 4F,
            vedtaksinformasjon = vedtaksinformasjon,
            status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
        )
        val forrigeStatus = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING)

        val innsok = try {
            innsokService.nyttInnsokUtkastGodkjentAvNav(deltaker, forrigeStatus)
        } finally {
            unmockkObject(PrisinfoRepoAdapter)
        }

        innsok.dagerPerUkeVedInnsok shouldBe 4
    }

    @Test
    fun `nyttInnsokUtkastGodkjentAvNav - forrige status UTKAST - setter utkastDelt`() {
        val utkastOpprettet = LocalDateTime.of(2026, 6, 10, 12, 0)
        val deltaker = lagDeltaker(
            vedtaksinformasjon = vedtaksinformasjon,
            status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
        )
        val forrigeStatus = lagDeltakerStatus(
            DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
            opprettet = utkastOpprettet,
        )

        val innsok = innsokService.nyttInnsokUtkastGodkjentAvNav(deltaker, forrigeStatus)

        innsok.utkastDelt shouldBe utkastOpprettet
        innsok.utkastGodkjentAvNav shouldBe true
    }

    @Test
    fun `nyttInnsokUtkastGodkjentAvDeltaker - forrige status KLADD - setter ikke utkastDelt`() {
        val deltaker = lagDeltaker(
            vedtaksinformasjon = vedtaksinformasjon,
            status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
        )
        val forrigeStatus = lagDeltakerStatus(DeltakerStatus.Type.KLADD)

        val innsok = innsokService.nyttInnsokUtkastGodkjentAvDeltaker(deltaker, forrigeStatus)

        innsok.utkastDelt shouldBe null
        innsok.utkastGodkjentAvNav shouldBe false
    }

    @Test
    fun `nyttInnsokUtkastGodkjentAvNav - setter innsoktAv og innsoktAvEnhet fra vedtaksinformasjon`() {
        val deltaker = lagDeltaker(
            vedtaksinformasjon = vedtaksinformasjon,
            status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
        )
        val forrigeStatus = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING)

        val innsok = innsokService.nyttInnsokUtkastGodkjentAvNav(deltaker, forrigeStatus)

        innsok.innsoktAv shouldBe vedtaksinformasjon.sistEndretAv
        innsok.innsoktAvEnhet shouldBe vedtaksinformasjon.sistEndretAvEnhet
    }

    @Test
    fun `nyttInnsokUtkastGodkjentAvNav - deltaker uten vedtaksinformasjon - kaster exception`() {
        val deltaker = lagDeltaker(
            vedtaksinformasjon = null,
            status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
        )
        val forrigeStatus = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING)

        shouldThrow<IllegalStateException> {
            innsokService.nyttInnsokUtkastGodkjentAvNav(deltaker, forrigeStatus)
        }
    }

    @Test
    fun `nyttInnsokUtkastGodkjentAvNav - kaller repository insert`() {
        val deltaker = lagDeltaker(
            vedtaksinformasjon = vedtaksinformasjon,
            status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
        )
        val forrigeStatus = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING)

        val innsok = innsokService.nyttInnsokUtkastGodkjentAvNav(deltaker, forrigeStatus)

        innsok.id shouldNotBe null
        innsok.deltakerId shouldBe deltaker.id
        verify(exactly = 1) { repository.insert(innsok) }
    }
}
