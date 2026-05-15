package no.nav.amt.deltaker.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.amt.deltaker.api.external.response.DeltakelserResponse
import no.nav.amt.deltaker.api.external.response.DeltakelserResponseMapper
import no.nav.amt.deltaker.api.external.response.Periode
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.repository.DeltakerRepository
import no.nav.amt.deltaker.repository.ImportertFraArenaRepository
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.service.DeltakerHistorikkService
import no.nav.amt.deltaker.tiltaksansvarlig.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.tiltaksarrangor.endring.EndringFraArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.veileder.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakelserResponseMapperTest {
    private val navEnhet = TestData.lagNavEnhet()
    private val navAnsatt = TestData.lagNavAnsatt(navEnhetId = navEnhet.id)

    private val navEnhetRepository = NavEnhetRepository()
    private val navAnsattRepository = NavAnsattRepository()

    private val deltakerHistorikkService = DeltakerHistorikkService(
        DeltakerEndringRepository(),
        VedtakRepository(),
        ForslagRepository(),
        EndringFraArrangorRepository(),
        ImportertFraArenaRepository(),
        InnsokPaaFellesOppstartRepository(),
        EndringFraTiltakskoordinatorRepository(),
        VurderingRepository(),
        DeltakerRepository(),
    )

    private val arrangorRepository = ArrangorRepository()
    private val arrangorService = ArrangorService(arrangorRepository, mockk())
    private val deltakelserResponseMapper = DeltakelserResponseMapper(deltakerHistorikkService, arrangorService)

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @BeforeEach
    fun beforeEach() {
        navEnhetRepository.upsert(navEnhet)
        navAnsattRepository.upsert(navAnsatt)
    }

    @Test
    fun `toDeltakelserResponse - kladd - returnerer riktig aktiv deltakelse`() {
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = null),
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.OPPFOLGING,
                    navn = "Oppfølging",
                ),
            ),
            status = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltakerStatus(DeltakerStatus.Type.KLADD),
        )
        TestRepository.insert(deltaker)

        val deltakelserResponse = deltakelserResponseMapper.toDeltakelserResponse(listOf(deltaker))

        deltakelserResponse.historikk.size shouldBe 0
        deltakelserResponse.aktive.size shouldBe 1

        assertSoftly(deltakelserResponse.aktive.first()) {
            deltakerId shouldBe deltaker.id
            deltakerlisteId shouldBe deltaker.deltakerliste.id
            tittel shouldBe "${deltaker.deltakerliste.tiltakstype.navn} hos Arrangør"
            tiltakstype shouldBe DeltakelserResponse.Tiltakstype(
                deltaker.deltakerliste.tiltakstype.navn,
                deltaker.deltakerliste.tiltakstype.tiltakskode,
            )
            status.type shouldBe DeltakerStatus.Type.KLADD
            status.visningstekst shouldBe "Kladden er ikke delt"
            status.aarsak shouldBe null
            innsoktDato shouldBe null
            sistEndretDato shouldBe deltaker.sistEndret.toLocalDate()
            periode shouldBe null
        }
    }

    @Test
    fun `toDeltakelserResponse - utkast, har overordnet arrangor - returnerer riktig aktiv deltakelse`() {
        val overordnetArrangor = TestData.lagArrangor(navn = "OVERORDNET ARRANGØR")
        arrangorRepository.upsert(overordnetArrangor)

        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = overordnetArrangor.id),
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.OPPFOLGING,
                    navn = "Oppfølging",
                ),
            ),
            status = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = null,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            opprettet = LocalDateTime.now().minusDays(4),
        )
        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)

        val deltakelserResponse = deltakelserResponseMapper.toDeltakelserResponse(listOf(deltaker))

        deltakelserResponse.historikk.size shouldBe 0
        deltakelserResponse.aktive.size shouldBe 1

        assertSoftly(deltakelserResponse.aktive.first()) {
            deltakerId shouldBe deltaker.id
            deltakerlisteId shouldBe deltaker.deltakerliste.id
            tittel shouldBe "${deltaker.deltakerliste.tiltakstype.navn} hos Overordnet Arrangør"
            tiltakstype shouldBe DeltakelserResponse.Tiltakstype(
                deltaker.deltakerliste.tiltakstype.navn,
                deltaker.deltakerliste.tiltakstype.tiltakskode,
            )
            status.type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
            status.visningstekst shouldBe "Utkastet er delt og venter på godkjenning"
            status.aarsak shouldBe null
            innsoktDato shouldBe null
            sistEndretDato shouldBe deltaker.sistEndret.toLocalDate()
            periode shouldBe null
        }
    }

    @Test
    fun `toDeltakelserResponse - venter pa oppstart - returnerer riktig aktiv deltakelse`() {
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = null),
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.OPPFOLGING,
                    navn = "Oppfølging",
                ),
            ),
            status = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().plusWeeks(1),
            sluttdato = null,
        )
        val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            opprettet = LocalDateTime.now().minusDays(4),
        )
        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)

        val deltakelserResponse = deltakelserResponseMapper.toDeltakelserResponse(listOf(deltaker))

        deltakelserResponse.historikk.size shouldBe 0
        deltakelserResponse.aktive.size shouldBe 1

        assertSoftly(deltakelserResponse.aktive.first()) {
            deltakerId shouldBe deltaker.id
            deltakerlisteId shouldBe deltaker.deltakerliste.id
            tittel shouldBe "${deltaker.deltakerliste.tiltakstype.navn} hos Arrangør"
            tiltakstype shouldBe DeltakelserResponse.Tiltakstype(
                deltaker.deltakerliste.tiltakstype.navn,
                deltaker.deltakerliste.tiltakstype.tiltakskode,
            )
            status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            status.visningstekst shouldBe "Venter på oppstart"
            status.aarsak shouldBe null
            innsoktDato shouldBe LocalDate.now().minusDays(4)
            sistEndretDato shouldBe null
            periode shouldBe Periode(deltaker.startdato, deltaker.sluttdato)
        }
    }

    @Test
    fun `toDeltakelserResponse - deltar - returnerer riktig aktiv deltakelse`() {
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = null),
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.OPPFOLGING,
                    navn = "Oppfølging",
                ),
            ),
            status = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )
        val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            opprettet = LocalDateTime.now().minusDays(4),
        )
        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)

        val deltakelserResponse = deltakelserResponseMapper.toDeltakelserResponse(listOf(deltaker))

        deltakelserResponse.historikk.size shouldBe 0
        deltakelserResponse.aktive.size shouldBe 1

        assertSoftly(deltakelserResponse.aktive.first()) {
            deltakerId shouldBe deltaker.id
            deltakerlisteId shouldBe deltaker.deltakerliste.id
            tittel shouldBe "${deltaker.deltakerliste.tiltakstype.navn} hos Arrangør"
            tiltakstype shouldBe DeltakelserResponse.Tiltakstype(
                deltaker.deltakerliste.tiltakstype.navn,
                deltaker.deltakerliste.tiltakstype.tiltakskode,
            )
            status.type shouldBe DeltakerStatus.Type.DELTAR
            status.visningstekst shouldBe "Deltar"
            status.aarsak shouldBe null
            innsoktDato shouldBe LocalDate.now().minusDays(4)
            sistEndretDato shouldBe null
            periode shouldBe Periode(deltaker.startdato, deltaker.sluttdato)
        }
    }

    @Test
    fun `toDeltakelserResponse - ikke aktuell - returnerer riktig historisk deltakelse`() {
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = null),
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.OPPFOLGING,
                    navn = "Oppfølging",
                ),
            ),
            status = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.IKKE_AKTUELL,
                aarsakType = DeltakerStatus.Aarsak.Type.ANNET,
                beskrivelse = "flyttet til Spania",
            ),
        )
        val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            opprettet = LocalDateTime.now().minusDays(4),
        )
        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)

        val deltakelserResponse = deltakelserResponseMapper.toDeltakelserResponse(listOf(deltaker))

        deltakelserResponse.aktive.size shouldBe 0
        deltakelserResponse.historikk.size shouldBe 1

        assertSoftly(deltakelserResponse.historikk.first()) {
            deltakerId shouldBe deltaker.id
            deltakerlisteId shouldBe deltaker.deltakerliste.id
            tittel shouldBe "${deltaker.deltakerliste.tiltakstype.navn} hos Arrangør"
            tiltakstype shouldBe DeltakelserResponse.Tiltakstype(
                deltaker.deltakerliste.tiltakstype.navn,
                deltaker.deltakerliste.tiltakstype.tiltakskode,
            )
            status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
            status.visningstekst shouldBe "Ikke aktuell"
            status.aarsak shouldBe "flyttet til Spania"
            innsoktDato shouldBe LocalDate.now().minusDays(4)
            sistEndretDato shouldBe null
            periode shouldBe null
        }
    }

    @Test
    fun `toDeltakelserResponse - har sluttet - returnerer riktig historisk deltakelse`() {
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = null),
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.OPPFOLGING,
                    navn = "Oppfølging",
                ),
            ),
            status = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.TRENGER_ANNEN_STOTTE,
                beskrivelse = null,
            ),
        )
        val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            opprettet = LocalDateTime.now().minusDays(4),
        )
        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)

        val deltakelserResponse = deltakelserResponseMapper.toDeltakelserResponse(listOf(deltaker))

        deltakelserResponse.aktive.size shouldBe 0
        deltakelserResponse.historikk.size shouldBe 1

        assertSoftly(deltakelserResponse.historikk.first()) {
            deltakerId shouldBe deltaker.id
            deltakerlisteId shouldBe deltaker.deltakerliste.id
            tittel shouldBe "${deltaker.deltakerliste.tiltakstype.navn} hos Arrangør"
            tiltakstype shouldBe DeltakelserResponse.Tiltakstype(
                deltaker.deltakerliste.tiltakstype.navn,
                deltaker.deltakerliste.tiltakstype.tiltakskode,
            )
            status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            status.visningstekst shouldBe "Har sluttet"
            status.aarsak shouldBe "Trenger annen støtte"
            innsoktDato shouldBe LocalDate.now().minusDays(4)
            sistEndretDato shouldBe null
            periode shouldBe Periode(deltaker.startdato, deltaker.sluttdato)
        }
    }

    @Test
    fun `toDeltakelserResponse - avbrutt utkast - returnerer riktig historisk deltakelse`() {
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = null),
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.OPPFOLGING,
                    navn = "Oppfølging",
                ),
            ),
            status = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.AVBRUTT_UTKAST,
                aarsakType = null,
                beskrivelse = null,
            ),
        )
        val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            opprettet = LocalDateTime.now().minusDays(4),
        )
        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)

        val deltakelserResponse = deltakelserResponseMapper.toDeltakelserResponse(listOf(deltaker))

        deltakelserResponse.aktive.size shouldBe 0
        deltakelserResponse.historikk.size shouldBe 1

        assertSoftly(deltakelserResponse.historikk.first()) {
            deltakerId shouldBe deltaker.id
            deltakerlisteId shouldBe deltaker.deltakerliste.id
            tittel shouldBe "${deltaker.deltakerliste.tiltakstype.navn} hos Arrangør"
            tiltakstype shouldBe DeltakelserResponse.Tiltakstype(
                deltaker.deltakerliste.tiltakstype.navn,
                deltaker.deltakerliste.tiltakstype.tiltakskode,
            )
            status.type shouldBe DeltakerStatus.Type.AVBRUTT_UTKAST
            status.visningstekst shouldBe "Avbrutt utkast"
            status.aarsak shouldBe null
            innsoktDato shouldBe null
            sistEndretDato shouldBe deltaker.sistEndret.toLocalDate()
            periode shouldBe null
        }
    }

    @Test
    fun `toDeltakelserResponse - har sluttet og ikke aktuell - returnerer nyeste historiske deltakelse forst`() {
        val deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
            arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = null),
            tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                tiltakskode = Tiltakskode.OPPFOLGING,
                navn = "Oppfølging",
            ),
        )

        TestRepository.insert(deltakerliste)

        val deltakerHarSluttet = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            status = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.TRENGER_ANNEN_STOTTE,
                beskrivelse = null,
            ),
            sistEndret = LocalDateTime.now().minusWeeks(2),
        )
        val vedtakHarSluttet = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerId = deltakerHarSluttet.id,
            fattet = LocalDateTime.now(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            opprettet = LocalDateTime.now().minusDays(4),
        )
        TestRepository.insert(deltakerHarSluttet)
        TestRepository.insert(vedtakHarSluttet)

        val deltakerIkkeAktuell = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            status = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.IKKE_AKTUELL,
                aarsakType = DeltakerStatus.Aarsak.Type.ANNET,
                beskrivelse = "flyttet til Spania",
            ),
            sistEndret = LocalDateTime.now().minusDays(1),
        )
        val vedtakIkkeAktuell = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerId = deltakerIkkeAktuell.id,
            fattet = LocalDateTime.now(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            opprettet = LocalDateTime.now().minusDays(4),
        )
        TestRepository.insert(deltakerIkkeAktuell)
        TestRepository.insert(vedtakIkkeAktuell)

        val deltakelserResponse = deltakelserResponseMapper.toDeltakelserResponse(listOf(deltakerHarSluttet, deltakerIkkeAktuell))

        assertSoftly(deltakelserResponse) {
            aktive.size shouldBe 0
            historikk.size shouldBe 2
            historikk[0].deltakerId shouldBe deltakerIkkeAktuell.id
            historikk[1].deltakerId shouldBe deltakerHarSluttet.id
        }
    }

    @Test
    fun `toDeltakelserResponse - pabegynt registrering - returnerer tomme lister`() {
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            deltakerliste = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                arrangor = TestData.lagArrangor(navn = "ARRANGØR", overordnetArrangorId = null),
                tiltakstype = no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype(
                    tiltakskode = Tiltakskode.OPPFOLGING,
                    navn = "Oppfølging",
                ),
            ),
            status = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltakerStatus(statusType = DeltakerStatus.Type.PABEGYNT_REGISTRERING),
        )
        TestRepository.insert(deltaker)

        val deltakelserResponse = deltakelserResponseMapper.toDeltakelserResponse(listOf(deltaker))
        assertSoftly(deltakelserResponse) {
            historikk.size shouldBe 0
            aktive.size shouldBe 0
        }
    }
}
