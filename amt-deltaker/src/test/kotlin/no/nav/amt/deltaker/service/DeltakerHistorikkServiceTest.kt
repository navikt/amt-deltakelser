package no.nav.amt.deltaker.service

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotliquery.queryOf
import no.nav.amt.deltaker.extensions.toVurderingFraArrangorData
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.navtiltakskoordinator.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.repository.ImportertFraArenaRepository
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.tiltaksarrangor.endring.EndringFraArrangorRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.veileder.InnsokRepository
import no.nav.amt.deltaker.veileder.endring.DeltakerEndringRepository
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Innsok
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Anskaffelse
import no.nav.amt.lib.models.deltaker.extensions.getInnsoktDato
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.testing.utils.TestData
import no.nav.amt.lib.utils.database.Database
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerHistorikkServiceTest {
    private val navEnhetRepository = NavEnhetRepository()
    private val navAnsattRepository = NavAnsattRepository()
    private val deltakerEndringRepository = DeltakerEndringRepository()
    private val forslagRepository = ForslagRepository()
    private val endringFraArrangorRepository = EndringFraArrangorRepository()
    private val vurderingRepository = VurderingRepository()

    private val deltakerHistorikkService = DeltakerHistorikkService(
        deltakerEndringRepository,
        VedtakRepository(),
        forslagRepository,
        endringFraArrangorRepository,
        ImportertFraArenaRepository(),
        InnsokRepository(),
        EndringFraTiltakskoordinatorRepository(),
        vurderingRepository,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class Enkeltplass {
        @Test
        fun `getForDeltaker - enkeltplass vedtak - returnerer vedtak med opplæringkategorisering`() {
            // Arrange
            val navEnhet = TestData.lagNavEnhet()
            navEnhetRepository.upsert(navEnhet)

            val navAnsatt = TestData.lagNavAnsatt()
            TestRepository.insert(navAnsatt)
            navAnsattRepository.upsert(navAnsatt)

            val kategorisering = OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(UUID.randomUUID() to "Bransje 1"),
                    ),
                ),
                valgteSertifiseringer = setOf(
                    SertifiseringValg(
                        id = 32143L,
                        navn = "Sertifisering 1",
                    ),
                ),
            )

            val deltaker = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltaker(
                    gjennomforing = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                        opplaringKategorisering = kategorisering,
                    ),
                )

            val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                fattet = LocalDateTime.now().minusMonths(1),
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndret = LocalDateTime.now().minusMonths(1),
            )

            TestRepository.insert(deltaker)
            TestRepository.insert(vedtak)

            // Act
            val historikk = deltakerHistorikkService.getForDeltaker(deltaker.id)
            val vedtakResult = historikk.first()

            // Assert
            historikk.size shouldBe 1
            vedtakResult.shouldBeInstanceOf<DeltakerHistorikk.Vedtak>()
            vedtakResult.vedtak.deltakerVedVedtak.opplaringKategorisering shouldNotBe null
            DeltakerTestUtils.sammenlignHistorikk(vedtakResult, DeltakerHistorikk.Vedtak(vedtak))
        }

        @Test
        fun `utelater eldre godkjent prisinfo for vedtaket nar flere finnes`() {
            // Arrange
            val vedtakFattet = LocalDateTime.now()
            val deltaker = lagDeltakerMedGodkjentPrisinfo(
                vedtakFattet = vedtakFattet,
                prisinfoTidspunkter = listOf(
                    34 to vedtakFattet.minusMinutes(3),
                    90 to vedtakFattet.minusSeconds(1),
                    120 to vedtakFattet.plusSeconds(1),
                ),
            )

            // Act
            val godkjentPrisinfo = deltakerHistorikkService
                .getForDeltaker(deltaker)
                .filterIsInstance<DeltakerHistorikk.EnkeltplassOkonomiGodkjent>()
            val v2Historikk = deltakerHistorikkService
                .getForDeltaker(deltaker.id)
                .filterIsInstance<DeltakerHistorikk.EnkeltplassOkonomiGodkjent>()
            val gruppedeltaker = deltaker.copy(
                gjennomforing = deltaker.gjennomforing.copy(gjennomforingstype = GjennomforingType.Gruppe),
            )
            val utenOkonomiHistorikk = deltakerHistorikkService
                .getForDeltaker(gruppedeltaker)
                .filterIsInstance<DeltakerHistorikk.EnkeltplassOkonomiGodkjent>()

            // Assert
            godkjentPrisinfo.map { it.data.prisinformasjon } shouldBe listOf(
                Anskaffelse(pris = 120, begrunnelse = "Pris 120"),
                Anskaffelse(pris = 90, begrunnelse = "Pris 90"),
            )
            godkjentPrisinfo.map { it.data.erForsteGodkjenning } shouldBe listOf(false, true)
            v2Historikk.map { it.data.prisinformasjon } shouldBe godkjentPrisinfo.map { it.data.prisinformasjon }
            v2Historikk.map { it.data.erForsteGodkjenning } shouldBe listOf(false, true)
            utenOkonomiHistorikk shouldBe emptyList()
        }

        @Test
        fun `beholder godkjent prisinfo nar bare en ligger for vedtaket`() {
            // Arrange
            val vedtakFattet = LocalDateTime.now()
            val deltaker = lagDeltakerMedGodkjentPrisinfo(
                vedtakFattet = vedtakFattet,
                prisinfoTidspunkter = listOf(
                    34 to vedtakFattet.minusMinutes(1),
                    90 to vedtakFattet.plusSeconds(1),
                ),
            )

            // Act
            val godkjentPrisinfo = deltakerHistorikkService
                .getForDeltaker(deltaker)
                .filterIsInstance<DeltakerHistorikk.EnkeltplassOkonomiGodkjent>()

            // Assert
            godkjentPrisinfo.map { it.data.prisinformasjon } shouldBe listOf(
                Anskaffelse(pris = 90, begrunnelse = "Pris 90"),
                Anskaffelse(pris = 34, begrunnelse = "Pris 34"),
            )
            godkjentPrisinfo.map { it.data.erForsteGodkjenning } shouldBe listOf(false, true)
        }

        @Test
        fun `beholder alle prisinfoer når ingen ligger før vedtaket`() {
            // Arrange
            // Denne rekkefølgen skal ikke forekomme, siden godkjent prisinfo utløser vedtaket.
            val vedtakFattet = LocalDateTime.now()
            val deltaker = lagDeltakerMedGodkjentPrisinfo(
                vedtakFattet = vedtakFattet,
                prisinfoTidspunkter = listOf(
                    34 to vedtakFattet.plusSeconds(1),
                    90 to vedtakFattet.plusSeconds(2),
                ),
            )

            // Act
            val godkjentPrisinfo = deltakerHistorikkService
                .getForDeltaker(deltaker)
                .filterIsInstance<DeltakerHistorikk.EnkeltplassOkonomiGodkjent>()

            // Assert
            godkjentPrisinfo.map { it.data.prisinformasjon } shouldBe listOf(
                Anskaffelse(pris = 90, begrunnelse = "Pris 90"),
                Anskaffelse(pris = 34, begrunnelse = "Pris 34"),
            )
            godkjentPrisinfo.map { it.data.erForsteGodkjenning } shouldBe listOf(false, false)
        }

        @Test
        fun `merker ingen prisinfo som forste godkjenning nar vedtaket ikke er fattet`() {
            // Arrange
            val deltaker = lagDeltakerMedGodkjentPrisinfo(
                vedtakFattet = null,
                prisinfoTidspunkter = listOf(
                    34 to LocalDateTime.now().minusMinutes(2),
                    90 to LocalDateTime.now().minusMinutes(1),
                ),
            )

            // Act
            val godkjentPrisinfo = deltakerHistorikkService
                .getForDeltaker(deltaker)
                .filterIsInstance<DeltakerHistorikk.EnkeltplassOkonomiGodkjent>()

            // Assert
            godkjentPrisinfo.size shouldBe 2
            godkjentPrisinfo.map { it.data.erForsteGodkjenning } shouldBe listOf(false, false)
        }

        @Test
        fun `merker ingen prisinfo som forste godkjenning nar vedtak mangler`() {
            val deltaker = lagDeltakerMedGodkjentPrisinfo(
                vedtakFattet = null,
                prisinfoTidspunkter = listOf(34 to LocalDateTime.now().minusMinutes(1)),
                opprettVedtak = false,
            )

            val godkjentPrisinfo = deltakerHistorikkService
                .getForDeltaker(deltaker)
                .filterIsInstance<DeltakerHistorikk.EnkeltplassOkonomiGodkjent>()

            godkjentPrisinfo.map { it.data.erForsteGodkjenning } shouldBe listOf(false)
        }

        private fun lagDeltakerMedGodkjentPrisinfo(
            vedtakFattet: LocalDateTime?,
            prisinfoTidspunkter: List<Pair<Int, LocalDateTime>>,
            opprettVedtak: Boolean = true,
        ): Deltaker {
            val navEnhet = TestData.lagNavEnhet()
            navEnhetRepository.upsert(navEnhet)

            val navAnsatt = TestData.lagNavAnsatt(navEnhetId = navEnhet.id)
            TestRepository.insert(navAnsatt)
            navAnsattRepository.upsert(navAnsatt)

            val gjennomforing = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste(
                gjennomforingstype = GjennomforingType.Enkeltplass,
            )
            val deltaker = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltaker(gjennomforing = gjennomforing)
            TestRepository.insert(deltaker)

            if (opprettVedtak) {
                val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                    deltakerId = deltaker.id,
                    deltakerVedVedtak = deltaker,
                    fattet = vedtakFattet,
                    opprettetAv = navAnsatt,
                    opprettetAvEnhet = navEnhet,
                    sistEndret = vedtakFattet ?: LocalDateTime.now(),
                )
                TestRepository.insert(vedtak)
            }

            prisinfoTidspunkter.forEach { (pris, tidspunkt) ->
                val prisinfoId = PrisinfoRepoAdapter.lagrePrisinfoEndring(
                    gjennomforingId = gjennomforing.id,
                    prisinformasjon = Anskaffelse(pris = pris),
                    begrunnelse = "Pris $pris",
                )
                PrisinfoRepoAdapter.godkjennOkonomi(
                    gjennomforingId = gjennomforing.id,
                    prisinformasjonId = prisinfoId,
                    godkjentAv = navAnsatt.id,
                    godkjentAvEnhet = navEnhet.id,
                )
                settPrisinfoTidspunkt(prisinfoId, tidspunkt)
            }

            return deltaker
        }
    }

    @Test
    fun `getForDeltaker - ett vedtak flere endringer og forslag - returner liste riktig sortert`() {
        // Arrange
        val navEnhet = TestData.lagNavEnhet()
        navEnhetRepository.upsert(navEnhet)

        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)
        navAnsattRepository.upsert(navAnsatt)

        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now().minusMonths(1),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
            sistEndret = LocalDateTime.now().minusMonths(1),
        )
        val gammelEndring = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endret = LocalDateTime.now().minusDays(20),
        )
        val endringFraArrangor = no.nav.amt.deltaker.utils.data.TestData.lagEndringFraArrangor(
            deltakerId = deltaker.id,
            opprettet = LocalDateTime.now().minusDays(18),
        )
        val forslag = no.nav.amt.deltaker.utils.data.TestData.lagForslag(
            deltakerId = deltaker.id,
            status = Forslag.Status.Tilbakekalt(
                tilbakekaltAvArrangorAnsattId = UUID.randomUUID(),
                tilbakekalt = LocalDateTime.now().minusDays(15),
            ),
        )
        val forslagVenter = no.nav.amt.deltaker.utils.data.TestData
            .lagForslag(deltakerId = deltaker.id)
        val nyEndring = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endret = LocalDateTime.now().minusDays(13),
        )
        val nyVurdering = no.nav.amt.deltaker.utils.data.TestData.lagVurdering(
            deltakerId = deltaker.id,
            gyldigFra = LocalDateTime.now().minusDays(10),
        )

        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)
        deltakerEndringRepository.upsert(gammelEndring)
        endringFraArrangorRepository.insert(endringFraArrangor)
        deltakerEndringRepository.upsert(nyEndring)
        forslagRepository.upsert(forslag)
        forslagRepository.upsert(forslagVenter)
        vurderingRepository.upsert(nyVurdering)

        // Act
        val historikk = deltakerHistorikkService.getForDeltaker(deltaker.id)

        // Assert
        historikk.size shouldBe 6
        DeltakerTestUtils.sammenlignHistorikk(
            historikk[0],
            DeltakerHistorikk.VurderingFraArrangor(nyVurdering.toVurderingFraArrangorData()),
        )
        DeltakerTestUtils.sammenlignHistorikk(historikk[1], DeltakerHistorikk.Endring(nyEndring))
        DeltakerTestUtils.sammenlignHistorikk(historikk[2], DeltakerHistorikk.Forslag(forslag))
        DeltakerTestUtils.sammenlignHistorikk(historikk[3], DeltakerHistorikk.EndringFraArrangor(endringFraArrangor))
        DeltakerTestUtils.sammenlignHistorikk(historikk[4], DeltakerHistorikk.Endring(gammelEndring))
        DeltakerTestUtils.sammenlignHistorikk(historikk[5], DeltakerHistorikk.Vedtak(vedtak))
    }

    @Test
    fun `getForDeltaker - ingen endringer - returner tom liste`() {
        // Arrange
        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        TestRepository.insert(deltaker)

        // Act & Assert
        deltakerHistorikkService.getForDeltaker(deltaker.id) shouldBe emptyList()
    }

    @Test
    fun `getInnsoktDato - ingen vedtak - returnerer null`() {
        // Arrange
        val deltakerhistorikk =
            listOf<DeltakerHistorikk>(
                DeltakerHistorikk.Endring(
                    no.nav.amt.deltaker.utils.data.TestData
                        .lagDeltakerEndring(),
                ),
            )

        // Act & Assert
        deltakerhistorikk.getInnsoktDato() shouldBe null
    }

    @Test
    fun `getInnsoktDato - to vedtak - returnerer tidligste opprettetdato`() {
        // Arrange
        val deltakerhistorikk = listOf(
            DeltakerHistorikk.Endring(
                no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerEndring(),
            ),
            DeltakerHistorikk.Vedtak(
                no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                    opprettet = LocalDateTime.now().minusMonths(1),
                ),
            ),
            DeltakerHistorikk.Vedtak(
                no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
                    opprettet = LocalDateTime.now().minusDays(4),
                ),
            ),
        )

        // Act & Assert
        deltakerhistorikk.getInnsoktDato() shouldBeCloseTo LocalDateTime.now().minusMonths(1)
    }

    @Test
    fun `getInnsoktDato - importert arenadeltaker - returnerer riktig dato`() {
        // Arrange
        val innsoktDato = LocalDate.now().minusMonths(1)
        val deltakerhistorikk = listOf(
            DeltakerHistorikk.Endring(
                no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerEndring(),
            ),
            DeltakerHistorikk.ImportertFraArena(
                importertFraArena = ImportertFraArena(
                    deltakerId = UUID.randomUUID(),
                    importertDato = LocalDateTime.now(),
                    deltakerVedImport = no.nav.amt.deltaker.utils.data.TestData
                        .lagDeltaker()
                        .toDeltakerVedImport(innsoktDato = innsoktDato),
                ),
            ),
        )

        // Act & Assert
        deltakerhistorikk.getInnsoktDato() shouldBe innsoktDato.atStartOfDay()
    }

    @Test
    fun `getInnsoktDato - har innsok - returnerer riktig dato`() {
        // Arrange
        val innsoktDato = LocalDate.now().minusMonths(1)
        val deltakerhistorikk = listOf(
            DeltakerHistorikk.Endring(
                no.nav.amt.deltaker.utils.data.TestData
                    .lagDeltakerEndring(),
            ),
            DeltakerHistorikk.InnsokPaaFellesOppstart(
                Innsok(
                    id = UUID.randomUUID(),
                    deltakerId = UUID.randomUUID(),
                    innsokt = innsoktDato.atStartOfDay(),
                    innsoktAv = UUID.randomUUID(),
                    innsoktAvEnhet = UUID.randomUUID(),
                    startdato = null,
                    sluttdato = null,
                    deltakelsesinnholdVedInnsok = null,
                    utkastDelt = null,
                    utkastGodkjentAvNav = true,
                    opplaringKategoriseringVedInnsok = null,
                    prisinformasjonVedInnsok = null,
                ),
            ),
        )

        // Act & Assert
        deltakerhistorikk.getInnsoktDato() shouldBe innsoktDato.atStartOfDay()
    }

    @Test
    fun `getForDeltaker med inkluderFullHistorikk false inkluderer InnsokPaaFellesOppstart`() {
        // Arrange
        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(deltaker)
        TestRepository.insert(navAnsatt)
        navEnhetRepository.upsert(navEnhet)

        val innsok = no.nav.amt.deltaker.utils.data.TestData.lagInnsok(
            deltakerId = deltaker.id,
            innsoktAv = navAnsatt.id,
            innsoktAvEnhet = navEnhet.id,
        )
        InnsokRepository().insert(innsok)

        // Act
        val historikk = deltakerHistorikkService.getForDeltaker(deltaker.id, inkluderFullHistorikk = false)

        // Assert
        historikk.filterIsInstance<DeltakerHistorikk.InnsokPaaFellesOppstart>().size shouldBe 1
    }

    @Test
    fun `getForDeltaker med inkluderFullHistorikk false inkluderer EndringFraArrangor`() {
        // Arrange
        // EndringFraArrangor er en del av kjernehistorikken fordi `LeggTilOppstartsdato` brukes
        // av `toDeltakelsesmengder.avgrensPeriodeTilStartdato` for å justere første gyldigFra.
        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        TestRepository.insert(deltaker)

        val endringFraArrangor = no.nav.amt.deltaker.utils.data.TestData.lagEndringFraArrangor(
            deltakerId = deltaker.id,
        )
        endringFraArrangorRepository.insert(endringFraArrangor)

        // Act
        val historikk = deltakerHistorikkService.getForDeltaker(deltaker.id, inkluderFullHistorikk = false)

        // Assert
        historikk.filterIsInstance<DeltakerHistorikk.EndringFraArrangor>().size shouldBe 1
    }

    @Test
    fun `getInnsoktDato via kjernehistorikk - ingen data - returnerer null`() {
        // Arrange
        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        TestRepository.insert(deltaker)

        // Act
        val historikk = deltakerHistorikkService.getForDeltaker(deltaker.id, inkluderFullHistorikk = false)

        // Assert
        historikk.getInnsoktDato() shouldBe null
    }

    @Test
    fun `getInnsoktDato via kjernehistorikk - importert fra arena - returnerer innsoktDato`() {
        // Arrange
        val innsoktDato = LocalDate.now().minusMonths(2)
        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        TestRepository.insert(deltaker)

        val importertFraArena = ImportertFraArena(
            deltakerId = deltaker.id,
            importertDato = LocalDateTime.now(),
            deltakerVedImport = deltaker.toDeltakerVedImport(innsoktDato = innsoktDato),
        )
        ImportertFraArenaRepository().upsert(importertFraArena)

        // Act
        val historikk = deltakerHistorikkService.getForDeltaker(deltaker.id, inkluderFullHistorikk = false)

        // Assert
        historikk.getInnsoktDato() shouldBe innsoktDato.atStartOfDay()
    }

    @Test
    fun `getInnsoktDato via kjernehistorikk - innsok paa felles oppstart - returnerer innsoktDato`() {
        // Arrange
        val innsoktDato = LocalDateTime.now().minusMonths(1)
        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        TestRepository.insert(deltaker)
        TestRepository.insert(navAnsatt)
        navEnhetRepository.upsert(navEnhet)

        val innsok = no.nav.amt.deltaker.utils.data.TestData.lagInnsok(
            deltakerId = deltaker.id,
            innsokt = innsoktDato,
            innsoktAv = navAnsatt.id,
            innsoktAvEnhet = navEnhet.id,
        )
        InnsokRepository().insert(innsok)

        // Act
        val historikk = deltakerHistorikkService.getForDeltaker(deltaker.id, inkluderFullHistorikk = false)

        // Assert
        historikk.getInnsoktDato() shouldBeCloseTo innsoktDato
    }

    @Test
    fun `getInnsoktDato via kjernehistorikk - vedtak uten arena eller innsok - returnerer vedtak opprettet dato`() {
        // Arrange
        val navEnhet = TestData.lagNavEnhet()
        navEnhetRepository.upsert(navEnhet)
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)

        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        val vedtakOpprettet = LocalDateTime.now().minusWeeks(3)
        val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerId = deltaker.id,
            opprettet = vedtakOpprettet,
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )
        TestRepository.insert(deltaker)
        TestRepository.insert(vedtak)

        // Act
        val historikk = deltakerHistorikkService.getForDeltaker(deltaker.id, inkluderFullHistorikk = false)

        // Assert
        historikk.getInnsoktDato() shouldBeCloseTo vedtakOpprettet
    }

    @Test
    fun `getInnsoktDato via kjernehistorikk - importert fra arena prioriteres over innsok og vedtak`() {
        // Arrange
        val innsoktDato = LocalDate.now().minusMonths(3)
        val navEnhet = TestData.lagNavEnhet()
        navEnhetRepository.upsert(navEnhet)
        val navAnsatt = TestData.lagNavAnsatt()
        TestRepository.insert(navAnsatt)

        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        TestRepository.insert(deltaker)

        val importertFraArena = ImportertFraArena(
            deltakerId = deltaker.id,
            importertDato = LocalDateTime.now(),
            deltakerVedImport = deltaker.toDeltakerVedImport(innsoktDato = innsoktDato),
        )
        ImportertFraArenaRepository().upsert(importertFraArena)

        val innsok = no.nav.amt.deltaker.utils.data.TestData.lagInnsok(
            deltakerId = deltaker.id,
            innsokt = LocalDateTime.now().minusMonths(1),
            innsoktAv = navAnsatt.id,
            innsoktAvEnhet = navEnhet.id,
        )
        InnsokRepository().insert(innsok)

        val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerId = deltaker.id,
            opprettet = LocalDateTime.now().minusWeeks(1),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )
        TestRepository.insert(vedtak)

        // Act
        val historikk = deltakerHistorikkService.getForDeltaker(deltaker.id, inkluderFullHistorikk = false)

        // Assert
        historikk.getInnsoktDato() shouldBe innsoktDato.atStartOfDay()
    }

    private fun settPrisinfoTidspunkt(
        prisinformasjonId: UUID,
        tidspunkt: LocalDateTime,
    ) {
        Database.query { session ->
            session.update(
                queryOf(
                    "UPDATE prisinformasjon SET modified_at = ? WHERE id = ?",
                    tidspunkt,
                    prisinformasjonId,
                ),
            )
        }
    }
}
