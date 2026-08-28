package no.nav.amt.distribusjon.journalforing

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import no.nav.amt.distribusjon.IntegrationTestBase
import no.nav.amt.distribusjon.distribusjonskanal.Distribusjonskanal
import no.nav.amt.distribusjon.hendelse.model.toModel
import no.nav.amt.distribusjon.journalforing.dokdistfordeling.DistribuerJournalpostRequest
import no.nav.amt.distribusjon.journalforing.model.HendelseMedJournalforingstatus
import no.nav.amt.distribusjon.journalforing.model.Journalforingstatus
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.distribusjon.utils.data.Persondata
import no.nav.amt.distribusjon.veilarboppfolging.Sak
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class JournalforingServiceTest : IntegrationTestBase() {
    @BeforeEach
    fun setupMocks() {
        coEvery { pdfgenClient.genererHovedvedtakForIndividuellOppfolging(any()) } returns "pdf".toByteArray()
        coEvery { veilarboppfolgingClient.opprettEllerHentSak(any()) } returns Sak(
            oppfolgingsperiodeId = UUID.randomUUID(),
            sakId = 42L,
            fagsaksystem = "~fagsaksystem~",
        )

        coEvery {
            dokarkivClient.opprettJournalpost(any(), any(), any(), any(), any(), any())
        } returns "journalpostId"

        coEvery {
            dokdistfordelingClient.distribuerJournalpost(
                any<String>(),
                any<DistribuerJournalpostRequest.Distribusjonstype>(),
                any<Boolean>(),
            )
        } returns UUID.randomUUID()
    }

    @Test
    fun `handleHendelse - InnbyggerGodkjennUtkast - journalforer hovedvedtak`() = runTest {
        // Arrange
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.innbyggerGodkjennUtkast())

        coEvery { amtPersonClient.hentNavBruker(hendelse.deltaker.personident) } returns Persondata.lagNavBruker()

        // Act
        journalforingService.handleHendelse(hendelse)

        // Assert
        journalforingstatusRepository
            .get(hendelse.id)
            .shouldNotBeNull()
            .journalpostId shouldNotBe null
    }

    @Test
    fun `handleHendelse - InnbyggerGodkjennUtkast, er allerede journalfort, skal ikke sende brev - ignorerer hendelse`() = runTest {
        // Arrange
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.innbyggerGodkjennUtkast())

        coEvery { amtPersonClient.hentNavBruker(hendelse.deltaker.personident) } returns Persondata.lagNavBruker()

        val journalpostId = "12345"
        hendelseRepository.insert(hendelse)
        journalforingstatusRepository.upsert(
            Journalforingstatus(
                hendelseId = hendelse.id,
                journalpostId = journalpostId,
                bestillingsId = null,
                kanIkkeDistribueres = null,
                kanIkkeJournalfores = false,
            ),
        )

        // Act
        journalforingService.handleHendelse(hendelse)

        // Assert
        assertSoftly(journalforingstatusRepository.get(hendelse.id).shouldNotBeNull()) {
            it.journalpostId shouldBe journalpostId
            bestillingsId shouldBe null
            kanIkkeDistribueres shouldBe false
            kanIkkeJournalfores shouldBe false
        }
    }

    @Test
    fun `handleHendelse - NavGodkjennUtkast, er journalfort, ikke sendt brev - sender brev`() = runTest {
        // Arrange
        val hendelse = Hendelsesdata.hendelse(
            payload = HendelseTypeData.navGodkjennUtkast(),
            distribusjonskanal = Distribusjonskanal.PRINT,
        )

        coEvery { amtPersonClient.hentNavBruker(hendelse.deltaker.personident) } returns Persondata.lagNavBruker()

        val journalpostId = "12345"
        hendelseRepository.insert(hendelse)
        journalforingstatusRepository.upsert(Journalforingstatus(hendelse.id, journalpostId, null, null, false))

        // Act
        journalforingService.handleHendelse(hendelse)

        // Assert
        assertSoftly(journalforingstatusRepository.get(hendelse.id).shouldNotBeNull()) {
            it.journalpostId shouldBe journalpostId
            bestillingsId shouldNotBe null
            kanIkkeDistribueres shouldBe false
            kanIkkeJournalfores shouldBe false
        }
    }

    @Test
    fun `handleHendelse - NavGodkjennUtkast, manuell oppfolging - sender brev`() = runTest {
        // Arrange
        val hendelse = Hendelsesdata.hendelse(
            HendelseTypeData.navGodkjennUtkast(),
            distribusjonskanal = Distribusjonskanal.SDP,
            manuellOppfolging = true,
        )
        hendelseRepository.insert(hendelse)

        coEvery { amtPersonClient.hentNavBruker(hendelse.deltaker.personident) } returns Persondata.lagNavBruker()

        // Act
        journalforingService.handleHendelse(hendelse)

        // Assert
        assertSoftly(journalforingstatusRepository.get(hendelse.id).shouldNotBeNull()) {
            journalpostId shouldNotBe null
            bestillingsId shouldNotBe null
            kanIkkeDistribueres shouldBe false
            kanIkkeJournalfores shouldBe false
        }
    }

    @Test
    fun `handleHendelse - NavGodkjennUtkast, ikke digital, ingen adresse - sender ikke brev`() = runTest {
        // Arrange
        val navBruker = Persondata.lagNavBruker(adresse = null)

        val hendelse = Hendelsesdata.hendelse(
            payload = HendelseTypeData.navGodkjennUtkast(),
            distribusjonskanal = Distribusjonskanal.PRINT,
            manuellOppfolging = false,
        )
        hendelseRepository.insert(hendelse)

        coEvery { amtPersonClient.hentNavBruker(hendelse.deltaker.personident) } returns navBruker

        // Act
        journalforingService.handleHendelse(hendelse)

        // Assert
        assertSoftly(journalforingstatusRepository.get(hendelse.id).shouldNotBeNull()) {
            journalpostId shouldNotBe null
            bestillingsId shouldBe null
            kanIkkeDistribueres shouldBe true
            kanIkkeJournalfores shouldBe false
        }
    }

    @Test
    fun `handleHendelse - AvsluttDeltakelse, er allerede journalfort, skal ikke sende brev - ignorerer hendelse`() = runTest {
        // Arrange
        val journalpostId = "12345"
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.avsluttDeltakelse())
        hendelseRepository.insert(hendelse)

        journalforingstatusRepository.upsert(
            Journalforingstatus(
                hendelseId = hendelse.id,
                journalpostId = journalpostId,
                bestillingsId = null,
                kanIkkeDistribueres = false,
                kanIkkeJournalfores = false,
            ),
        )

        // Act
        journalforingService.handleHendelse(hendelse)

        // Assert
        assertSoftly(journalforingstatusRepository.get(hendelse.id).shouldNotBeNull()) {
            it.journalpostId shouldNotBe null
            kanIkkeJournalfores shouldBe false
        }
    }

    @Test
    fun `handleHendelse - AvsluttDeltakelse, er allerede journalfort, kan ikke sende brev - ignorerer hendelse`() = runTest {
        // Arrange
        val journalpostId = "12345"
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.avsluttDeltakelse())
        hendelseRepository.insert(hendelse)

        journalforingstatusRepository.upsert(
            Journalforingstatus(
                hendelseId = hendelse.id,
                journalpostId = journalpostId,
                bestillingsId = null,
                kanIkkeDistribueres = true,
                kanIkkeJournalfores = false,
            ),
        )

        // Act
        journalforingService.handleHendelse(hendelse)

        // Assert
        assertSoftly(journalforingstatusRepository.get(hendelse.id).shouldNotBeNull()) {
            it.journalpostId shouldNotBe null
            kanIkkeJournalfores shouldBe false
        }
    }

    @Test
    fun `handleHendelse - InnbyggerGodkjennUtkast, har ikke aktiv oppfolgingsperiode - feiler`() = runTest {
        val navBruker = Persondata.lagNavBruker(
            oppfolgingsperioder = listOf(
                Persondata.lagOppfolgingsperiode(
                    startdato = LocalDateTime.now().minusYears(2),
                    sluttdato = LocalDateTime.now().minusMonths(4),
                ),
            ),
        )

        val hendelse = Hendelsesdata.lagHendelseDto(HendelseTypeData.innbyggerGodkjennUtkast())

        coEvery { amtPersonClient.hentNavBruker(hendelse.deltaker.personident) } returns navBruker

        shouldThrow<IllegalArgumentException> {
            journalforingService.handleHendelse(hendelse.toModel(Distribusjonskanal.DITT_NAV, false))
        }
    }

    @Test
    fun `handleHendelse - EndreSluttarsak - journalforer ikke`() = runTest {
        // Arrange
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.endreSluttarsak())

        // Act
        journalforingService.handleHendelse(hendelse)

        // Assert
        journalforingstatusRepository.get(hendelse.id) shouldBe null
    }

    @Test
    fun `journalforOgDistribuerEndringsvedtak - deltakelsesmengde og forleng - journalforer endringsvedtak`() = runTest {
        // Arrange
        val deltaker = Hendelsesdata.lagDeltaker()

        val hendelseDeltakelsesmengde = Hendelsesdata.hendelse(
            payload = HendelseTypeData.endreDeltakelsesmengde(),
            deltaker = deltaker,
            opprettet = LocalDateTime.now().minusMinutes(20),
        )
        val journalforingstatusDeltakelsesmengde = Journalforingstatus(
            hendelseId = hendelseDeltakelsesmengde.id,
            journalpostId = null,
            bestillingsId = null,
            kanIkkeDistribueres = null,
            kanIkkeJournalfores = null,
        )
        journalforingstatusRepository.upsert(journalforingstatusDeltakelsesmengde)

        val hendelseForleng = Hendelsesdata.hendelse(
            payload = HendelseTypeData.forlengDeltakelse(),
            deltaker = deltaker,
            ansvarlig = hendelseDeltakelsesmengde.ansvarlig,
            opprettet = LocalDateTime.now(),
        )
        val journalforingstatusForleng = Journalforingstatus(
            hendelseId = hendelseForleng.id,
            journalpostId = null,
            bestillingsId = null,
            kanIkkeDistribueres = null,
            kanIkkeJournalfores = null,
        )
        journalforingstatusRepository.upsert(journalforingstatusForleng)

        coEvery { amtPersonClient.hentNavBruker(any()) } returns Persondata.lagNavBruker()
        coEvery { pdfgenClient.endringsvedtak(any()) } returns "pdf".toByteArray()

        // Act
        journalforingService.journalforOgDistribuerEndringsvedtak(
            hendelseMedJournalforingstatuser = listOf(
                HendelseMedJournalforingstatus(hendelseForleng, journalforingstatusForleng),
                HendelseMedJournalforingstatus(hendelseDeltakelsesmengde, journalforingstatusDeltakelsesmengde),
            ),
        )

        // Assert
        val journalpostForleng = journalforingstatusRepository.get(hendelseForleng.id).shouldNotBeNull()
        journalpostForleng.kanIkkeJournalfores shouldBe false

        assertSoftly(journalforingstatusRepository.get(hendelseDeltakelsesmengde.id).shouldNotBeNull()) {
            journalpostId shouldNotBe null
            journalpostId shouldBe journalpostForleng.journalpostId
            kanIkkeJournalfores shouldBe false
        }
    }

    @Test
    fun `journalforOgDistribuerEndringsvedtak - to endringer, en allerede journalfort - journalforer 1, distribuerer 2`() = runTest {
        // Arrange
        val deltaker = Hendelsesdata.lagDeltaker()

        val hendelseDeltakelsesmengde = Hendelsesdata.hendelse(
            payload = HendelseTypeData.endreDeltakelsesmengde(),
            deltaker = deltaker,
            opprettet = LocalDateTime.now().minusMinutes(20),
            distribusjonskanal = Distribusjonskanal.PRINT,
        )
        val journalforingstatusDeltakelsesmengde = Journalforingstatus(
            hendelseId = hendelseDeltakelsesmengde.id,
            journalpostId = "99887",
            bestillingsId = null,
            kanIkkeDistribueres = null,
            kanIkkeJournalfores = false,
        )
        journalforingstatusRepository.upsert(journalforingstatusDeltakelsesmengde)

        val hendelseForleng = Hendelsesdata.hendelse(
            payload = HendelseTypeData.forlengDeltakelse(),
            deltaker = deltaker,
            ansvarlig = hendelseDeltakelsesmengde.ansvarlig,
            opprettet = LocalDateTime.now(),
            distribusjonskanal = Distribusjonskanal.PRINT,
        )
        val journalforingstatusForleng = Journalforingstatus(
            hendelseId = hendelseForleng.id,
            journalpostId = null,
            bestillingsId = null,
            kanIkkeDistribueres = null,
            kanIkkeJournalfores = null,
        )
        journalforingstatusRepository.upsert(journalforingstatusForleng)

        coEvery { amtPersonClient.hentNavBruker(any()) } returns Persondata.lagNavBruker()
        coEvery { pdfgenClient.endringsvedtak(any()) } returns "pdf".toByteArray()

        // Act
        journalforingService.journalforOgDistribuerEndringsvedtak(
            listOf(
                HendelseMedJournalforingstatus(hendelseForleng, journalforingstatusForleng),
                HendelseMedJournalforingstatus(hendelseDeltakelsesmengde, journalforingstatusDeltakelsesmengde),
            ),
        )

        // Assert
        val journalpostDeltakelsesmengde = journalforingstatusRepository.get(hendelseDeltakelsesmengde.id).shouldNotBeNull()

        assertSoftly(journalpostDeltakelsesmengde) {
            journalpostId shouldBe journalforingstatusDeltakelsesmengde.journalpostId
            bestillingsId shouldNotBe null
            kanIkkeDistribueres shouldBe false
            kanIkkeJournalfores shouldBe false
        }

        assertSoftly(journalforingstatusRepository.get(hendelseForleng.id).shouldNotBeNull()) {
            journalpostId shouldNotBe journalpostDeltakelsesmengde.journalpostId
            bestillingsId shouldNotBe null
            journalpostId shouldNotBe null
            kanIkkeDistribueres shouldBe false
            kanIkkeJournalfores shouldBe false
        }
    }

    @Test
    fun `journalforOgDistribuerEndringsvedtak - tom liste - gjør ingenting`() = runTest {
        // Act
        journalforingService.journalforOgDistribuerEndringsvedtak(emptyList())

        // Assert
        coVerify(exactly = 0) { amtPersonClient.hentNavBruker(any()) }
        coVerify(exactly = 0) { pdfgenClient.endringsvedtak(any()) }
    }

    @Test
    fun `journalforOgDistribuerEndringsvedtak - falsk identitet - journalforer ikke eller distribuerer`() = runTest {
        // Arrange
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.forlengDeltakelse())
        hendelseRepository.insert(hendelse)
        val journalforingstatus = Journalforingstatus(
            hendelseId = hendelse.id,
            journalpostId = null,
            bestillingsId = null,
            kanIkkeDistribueres = null,
            kanIkkeJournalfores = null,
        )
        journalforingstatusRepository.upsert(journalforingstatus)

        coEvery { amtPersonClient.hentNavBruker(hendelse.deltaker.personident) } returns Persondata.lagNavBruker(
            harFalskIdentitet = true,
        )

        // Act
        journalforingService.journalforOgDistribuerEndringsvedtak(
            listOf(
                HendelseMedJournalforingstatus(hendelse, journalforingstatus),
            ),
        )

        // Assert
        assertSoftly(journalforingstatusRepository.get(hendelse.id).shouldNotBeNull()) {
            journalpostId shouldBe null
            bestillingsId shouldBe null
            kanIkkeDistribueres shouldBe true
            kanIkkeJournalfores shouldBe true
        }
        hendelseRepository.hentIkkeJournalforteHendelser().none { it.hendelse.id == hendelse.id } shouldBe true
        coVerify(exactly = 0) { pdfgenClient.endringsvedtak(any()) }
        coVerify(exactly = 0) { dokarkivClient.opprettJournalpost(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { dokdistfordelingClient.distribuerJournalpost(any<String>(), any(), any()) }
    }

    @Test
    fun `journalforOgDistribuerEndringsvedtak - avslutt deltakelse, ikke under oppfolging - journalforer endringsvedtak`() = runTest {
        // Arrange
        val navBruker = Persondata.lagNavBruker(
            oppfolgingsperioder = listOf(
                Persondata.lagOppfolgingsperiode(
                    startdato = LocalDateTime.now().minusYears(2),
                    sluttdato = LocalDateTime.now().minusMonths(4),
                ),
            ),
        )
        val avsluttDeltakelseHendelse = Hendelsesdata.hendelse(HendelseTypeData.avsluttDeltakelse())
        val journalforingstatus = Journalforingstatus(avsluttDeltakelseHendelse.id, null, null, null, kanIkkeJournalfores = null)
        journalforingstatusRepository.upsert(journalforingstatus)

        coEvery { amtPersonClient.hentNavBruker(avsluttDeltakelseHendelse.deltaker.personident) } returns navBruker

        // Act
        journalforingService.journalforOgDistribuerEndringsvedtak(
            listOf(
                HendelseMedJournalforingstatus(avsluttDeltakelseHendelse, journalforingstatus),
            ),
        )

        // Assert
        assertSoftly(journalforingstatusRepository.get(avsluttDeltakelseHendelse.id).shouldNotBeNull()) {
            journalpostId shouldBe null
            bestillingsId shouldBe null
            kanIkkeJournalfores shouldBe true
        }
    }

    @Test
    fun `journalforOgDistribuerEndringsvedtak - forleng deltakelse, ikke under oppfolging - feiler`() = runTest {
        val navBruker = Persondata.lagNavBruker(
            oppfolgingsperioder = listOf(
                Persondata.lagOppfolgingsperiode(
                    startdato = LocalDateTime.now().minusYears(2),
                    sluttdato = LocalDateTime.now().minusMonths(4),
                ),
            ),
        )
        val hendelseForleng = Hendelsesdata.hendelse(HendelseTypeData.forlengDeltakelse())

        val journalforingstatusForleng = Journalforingstatus(
            hendelseId = hendelseForleng.id,
            journalpostId = null,
            bestillingsId = null,
            kanIkkeDistribueres = null,
            kanIkkeJournalfores = null,
        )
        journalforingstatusRepository.upsert(journalforingstatusForleng)

        coEvery { amtPersonClient.hentNavBruker(hendelseForleng.deltaker.personident) } returns navBruker

        // Act & Assert
        shouldThrow<IllegalArgumentException> {
            journalforingService.journalforOgDistribuerEndringsvedtak(
                listOf(
                    HendelseMedJournalforingstatus(hendelseForleng, journalforingstatusForleng),
                ),
            )
        }
    }

    @Test
    fun `journalforOgDistribuerEndringsvedtak - ulik deltakerid - feiler`() = runTest {
        // Arrange
        val hendelseDeltakelsesmengde = Hendelsesdata.hendelse(
            payload = HendelseTypeData.endreDeltakelsesmengde(),
            opprettet = LocalDateTime.now().minusMinutes(20),
        )
        val hendelseForleng = Hendelsesdata.hendelse(
            payload = HendelseTypeData.forlengDeltakelse(),
            opprettet = LocalDateTime.now(),
        )

        // Act & Assert
        shouldThrow<IllegalArgumentException> {
            journalforingService.journalforOgDistribuerEndringsvedtak(
                listOf(
                    HendelseMedJournalforingstatus(
                        hendelse = hendelseForleng,
                        journalforingstatus = Journalforingstatus(
                            hendelseId = hendelseForleng.id,
                            journalpostId = null,
                            bestillingsId = null,
                            kanIkkeDistribueres = null,
                            kanIkkeJournalfores = null,
                        ),
                    ),
                    HendelseMedJournalforingstatus(
                        hendelse = hendelseDeltakelsesmengde,
                        journalforingstatus = Journalforingstatus(
                            hendelseId = hendelseDeltakelsesmengde.id,
                            journalpostId = null,
                            bestillingsId = null,
                            kanIkkeDistribueres = null,
                            kanIkkeJournalfores = null,
                        ),
                    ),
                ),
            )
        }
    }
}
