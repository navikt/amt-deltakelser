package no.nav.amt.deltaker.bff.deltaker

import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteRepository
import no.nav.amt.deltaker.bff.innbygger.NavBrukerRepository
import no.nav.amt.deltaker.bff.innbygger.NavBrukerService
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.bff.navansatt.NavAnsattService
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.vurdering.VurderingService
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.deltaker.bff.utils.endre
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerStatusDto
import no.nav.amt.lib.models.deltaker.Deltakerliste
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.amt.lib.models.deltaker.Navn
import no.nav.amt.lib.models.deltaker.Personalia
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerV2ConsumerTest {
    private val amtPersonServiceClient: AmtPersonServiceClient = mockk(relaxed = true)

    private val navAnsattService = NavAnsattService(
        repository = NavAnsattRepository(),
        amtPersonServiceClient = amtPersonServiceClient,
    )
    private val navEnhetService = NavEnhetService(
        repository = NavEnhetRepository(),
        amtPersonServiceClient = amtPersonServiceClient,
    )
    private val navBrukerService = NavBrukerService(
        amtPersonServiceClient = mockk(relaxed = true),
        navBrukerRepository = NavBrukerRepository(),
        navAnsattService = navAnsattService,
        navEnhetService = navEnhetService,
    )
    private val deltakerRepository = DeltakerRepository()
    private val deltakerService = DeltakerService(
        deltakerRepository = deltakerRepository,
        amtDeltakerClient = mockk(relaxed = true),
        forslagRepository = mockk(relaxed = true),
    )
    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val vurdersRepository = VurderingRepository()
    private val vurderingService = VurderingService(VurderingRepository())
    private val unleashToggle = mockk<CommonUnleashToggle>()
    private val consumer = DeltakerV2Consumer(
        deltakerRepository,
        deltakerService,
        deltakerlisteRepository,
        vurderingService,
        navBrukerService,
        unleashToggle,
    )

    @BeforeEach
    fun setup() {
        every { unleashToggle.erKometMasterForTiltakstype(Tiltakskode.ARBEIDSFORBEREDENDE_TRENING) } returns true
    }

    @Test
    fun `consume - kilde er ARENA, deltaker finnes - konsumerer melding, oppdaterer`() = runTest {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val deltaker = TestData.lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
        TestRepository.insert(deltaker)
        val vurdering = TestData.lagVurdering(deltakerId = deltaker.id)
        val startdato = LocalDate.now().plusDays(1)
        val sluttdato = LocalDate.now().plusWeeks(3)
        val sistEndret = LocalDateTime.now().minusDays(2)
        val mottattDeltaker = deltaker.copy(
            startdato = startdato,
            sluttdato = sluttdato,
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            sistEndret = sistEndret,
        )

        consumer.consume(
            deltaker.id,
            objectMapper.writeValueAsString(mottattDeltaker.toKafkaPayload(Kilde.ARENA, listOf(vurdering), deltakerliste)),
        )

        val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()
        oppdatertDeltaker.startdato shouldBe startdato
        oppdatertDeltaker.sluttdato shouldBe sluttdato
        oppdatertDeltaker.sistEndret shouldBeCloseTo sistEndret

        val lagretVurdering = vurdersRepository.getForDeltaker(deltaker.id)
        lagretVurdering.size shouldBe 1
    }

    @Test
    fun `consume - kilde er ARENA, deltaker finnes ikke, ingen andre deltakelser - konsumerer melding, lagrer`() = runTest {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        TestRepository.insert(deltakerliste)
        val navbruker = lagNavBruker()
        val sistEndret = LocalDateTime.now().minusDays(1)
        val statusOpprettet = LocalDateTime.now().minusWeeks(1)
        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            navBruker = navbruker,
            sistEndret = sistEndret,
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.DELTAR,
                opprettet = statusOpprettet,
            ),
        )
        TestRepository.insert(navbruker)
        consumer.consume(
            deltaker.id,
            objectMapper.writeValueAsString(deltaker.toKafkaPayload(Kilde.ARENA, deltakerliste = deltakerliste)),
        )

        val lagretDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()
        lagretDeltaker.startdato shouldBe deltaker.startdato
        lagretDeltaker.kanEndres shouldBe true
        lagretDeltaker.sistEndret shouldBeCloseTo sistEndret
        lagretDeltaker.status.opprettet shouldBeCloseTo statusOpprettet
    }

    @Test
    fun `consume - kilde ARENA, finnes ikke, en tidligere deltakelse - lagrer, tidligere deltaker kan ikke endres`() = runTest {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val navbruker = lagNavBruker()
        val tidligereDeltakelse = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            navBruker = navbruker,
            historikk = true,
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
        )
        TestRepository.insert(tidligereDeltakelse)

        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            navBruker = navbruker,
            historikk = true,
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )

        consumer.consume(
            deltaker.id,
            objectMapper.writeValueAsString(deltaker.toKafkaPayload(Kilde.ARENA, deltakerliste = deltakerliste)),
        )

        val lagretDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()
        lagretDeltaker.startdato shouldBe deltaker.startdato
        lagretDeltaker.kanEndres shouldBe true

        val lagretTidligereDeltaker = deltakerRepository.get(tidligereDeltakelse.id).getOrThrow()
        lagretTidligereDeltaker.kanEndres shouldBe false
    }

    @Test
    fun `consume - kilde ARENA, finnes ikke, avsluttet, en avsluttet deltakelse - tidligere deltaker kan ikke endres`() = runTest {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val navbruker = lagNavBruker()
        val statusdato = LocalDateTime.now().minusMonths(2)
        val tidligereDeltakelse = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            navBruker = navbruker,
            historikk = true,
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                opprettet = statusdato,
            ),
        )
        TestRepository.insert(tidligereDeltakelse)

        val statusdato2 = LocalDateTime.now().minusDays(3)
        val deltaker = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            navBruker = navbruker,
            historikk = true,
            status = TestData.lagDeltakerStatus(
                statusType = DeltakerStatus.Type.IKKE_AKTUELL,
                opprettet = statusdato2,
            ),
        )

        consumer.consume(
            deltaker.id,
            objectMapper.writeValueAsString(deltaker.toKafkaPayload(Kilde.ARENA, deltakerliste = deltakerliste)),
        )

        val lagretDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()
        lagretDeltaker.startdato shouldBe deltaker.startdato
        lagretDeltaker.kanEndres shouldBe true

        val lagretTidligereDeltaker = deltakerRepository.get(tidligereDeltakelse.id).getOrThrow()
        lagretTidligereDeltaker.kanEndres shouldBe false
    }

    @Test
    fun `consume - kilde ARENA, leser inn eldste deltakelse først - eldste deltaker kan ikke endres`() = runTest {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val navbruker = lagNavBruker()
        val eldsteDeltakelse = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            navBruker = navbruker,
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            historikk = true,
        )

        val nyesteDeltakelse = TestData.lagDeltaker(
            deltakerliste = deltakerliste,
            navBruker = navbruker,
            historikk = true,
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
        )
        TestRepository.insert(eldsteDeltakelse)

        consumer.consume(
            nyesteDeltakelse.id,
            objectMapper.writeValueAsString(nyesteDeltakelse.toKafkaPayload(Kilde.ARENA, deltakerliste = deltakerliste)),
        )

        val lagretDeltaker = deltakerRepository.get(nyesteDeltakelse.id).getOrThrow()
        lagretDeltaker.startdato shouldBe nyesteDeltakelse.startdato
        lagretDeltaker.kanEndres shouldBe true

        val lagretTidligereDeltaker = deltakerRepository.get(eldsteDeltakelse.id).getOrThrow()
        lagretTidligereDeltaker.kanEndres shouldBe false
    }

    @Test
    fun `consume - kilde er KOMET, deltaker finnes - konsumerer melding, oppdaterer`() = runTest {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val deltaker = TestData.lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
        TestRepository.insert(deltaker)

        val startdato = LocalDate.now().plusDays(1)
        val sluttdato = LocalDate.now().plusWeeks(3)
        val mottattDeltaker = deltaker.copy(
            startdato = startdato,
            sluttdato = sluttdato,
            status = TestData.lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )

        consumer.consume(
            deltaker.id,
            objectMapper.writeValueAsString(mottattDeltaker.toKafkaPayload(Kilde.KOMET, deltakerliste = deltakerliste)),
        )

        val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()
        oppdatertDeltaker.startdato shouldBe startdato
        oppdatertDeltaker.sluttdato shouldBe sluttdato
    }

    @Test
    fun `consume - tombstone - sletter deltaker`() = runTest {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )
        val deltaker = TestData.lagDeltaker(deltakerliste = deltakerliste, startdato = null, sluttdato = null)
        TestRepository.insert(deltaker)

        consumer.consume(deltaker.id, null)

        deltakerRepository.get(deltaker.id).getOrNull() shouldBe null
    }

    @Test
    fun `consume - alle endringstyper fra amt-deltaker - lagrer ny tilstand i bff-db`() = runTest {
        val deltakerliste = TestData.lagDeltakerliste(
            tiltakstype = TestData.lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING),
        )

        val endringer = listOf(
            DeltakerEndring.Endring.EndreBakgrunnsinformasjon("ny bakgrunn"),
            DeltakerEndring.Endring.EndreInnhold(
                ledetekst = "ny ledetekst",
                innhold = listOf(Innhold("Annet", "annet", true, "beskrivelse")),
            ),
            DeltakerEndring.Endring.EndreDeltakelsesmengde(
                deltakelsesprosent = 50f,
                dagerPerUke = 2f,
                gyldigFra = LocalDate.now(),
                begrunnelse = null,
            ),
            DeltakerEndring.Endring.EndreStartdato(
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusWeeks(2),
                begrunnelse = null,
            ),
            DeltakerEndring.Endring.EndreSluttdato(
                sluttdato = LocalDate.now(),
                begrunnelse = null,
            ),
            DeltakerEndring.Endring.EndreSluttarsak(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, "beskrivelse"),
                begrunnelse = null,
            ),
            DeltakerEndring.Endring.ForlengDeltakelse(
                sluttdato = LocalDate.now().plusWeeks(4),
                begrunnelse = "begrunnelse",
            ),
            DeltakerEndring.Endring.IkkeAktuell(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, "beskrivelse"),
                begrunnelse = "begrunnelse",
            ),
            DeltakerEndring.Endring.AvsluttDeltakelse(
                sluttdato = LocalDate.now(),
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, "beskrivelse"),
                begrunnelse = "begrunnelse",
                harFullfort = true,
            ),
            DeltakerEndring.Endring.EndreAvslutning(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, "beskrivelse"),
                begrunnelse = "begrunnelse",
                harFullfort = false,
            ),
            DeltakerEndring.Endring.AvbrytDeltakelse(
                sluttdato = LocalDate.now(),
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.ANNET, "beskrivelse"),
                begrunnelse = "begrunnelse",
            ),
            DeltakerEndring.Endring.ReaktiverDeltakelse(
                reaktivertDato = LocalDate.now(),
                begrunnelse = "begrunnelse",
            ),
            DeltakerEndring.Endring.FjernOppstartsdato(
                begrunnelse = "begrunnelse",
            ),
        )

        endringer.forEach { endring ->
            val deltaker = TestData.lagDeltaker(
                deltakerliste = deltakerliste,
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker)

            // Statuser må få ny id for at insertIfNotExists (ON CONFLICT DO NOTHING) faktisk skal lagre dem.
            // Dette matcher produksjon, der amt-deltaker genererer ny status-id ved hver statusendring.
            val oppdatertDeltaker = deltaker
                .endre(TestData.lagDeltakerEndring(deltakerId = deltaker.id, endring = endring))
                .let { it.copy(status = it.status.copy(id = UUID.randomUUID())) }

            consumer.consume(
                deltaker.id,
                objectMapper.writeValueAsString(oppdatertDeltaker.toKafkaPayload(Kilde.KOMET, deltakerliste = deltakerliste)),
            )

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            withClue("Endringstype ${endring::class.simpleName}") {
                when (endring) {
                    is DeltakerEndring.Endring.EndreBakgrunnsinformasjon ->
                        deltakerFraDb.bakgrunnsinformasjon shouldBe endring.bakgrunnsinformasjon

                    is DeltakerEndring.Endring.EndreInnhold -> {
                        deltakerFraDb.deltakelsesinnhold.shouldNotBeNull().ledetekst shouldBe endring.ledetekst
                        deltakerFraDb.deltakelsesinnhold.innhold shouldBe endring.innhold
                    }

                    is DeltakerEndring.Endring.EndreDeltakelsesmengde -> {
                        deltakerFraDb.deltakelsesprosent shouldBe endring.deltakelsesprosent
                        deltakerFraDb.dagerPerUke shouldBe endring.dagerPerUke
                    }

                    is DeltakerEndring.Endring.EndreStartdato -> {
                        deltakerFraDb.startdato shouldBe endring.startdato
                        deltakerFraDb.sluttdato shouldBe endring.sluttdato
                    }

                    is DeltakerEndring.Endring.EndreSluttdato ->
                        deltakerFraDb.sluttdato shouldBe endring.sluttdato

                    is DeltakerEndring.Endring.EndreSluttarsak ->
                        deltakerFraDb.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.valueOf(endring.aarsak.type.name)

                    is DeltakerEndring.Endring.ForlengDeltakelse ->
                        deltakerFraDb.sluttdato shouldBe endring.sluttdato

                    is DeltakerEndring.Endring.IkkeAktuell -> {
                        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
                        deltakerFraDb.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.valueOf(endring.aarsak.type.name)
                    }

                    is DeltakerEndring.Endring.AvsluttDeltakelse -> {
                        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                        deltakerFraDb.sluttdato shouldBe endring.sluttdato
                    }

                    is DeltakerEndring.Endring.EndreAvslutning -> {
                        val forventet = if (endring.harFullfort == true) DeltakerStatus.Type.FULLFORT else DeltakerStatus.Type.AVBRUTT
                        deltakerFraDb.status.type shouldBe forventet
                    }

                    is DeltakerEndring.Endring.AvbrytDeltakelse -> {
                        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.AVBRUTT
                        deltakerFraDb.sluttdato shouldBe endring.sluttdato
                    }

                    is DeltakerEndring.Endring.ReaktiverDeltakelse -> {
                        deltakerFraDb.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                        deltakerFraDb.startdato shouldBe null
                        deltakerFraDb.sluttdato shouldBe null
                    }

                    is DeltakerEndring.Endring.FjernOppstartsdato -> {
                        deltakerFraDb.startdato shouldBe null
                        deltakerFraDb.sluttdato shouldBe null
                    }
                }
            }
        }
    }

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        private fun Deltaker.toKafkaPayload(
            kilde: Kilde,
            vurderinger: List<Vurdering> = emptyList(),
            deltakerliste: no.nav.amt.deltaker.bff.model.Deltakerliste,
        ) = DeltakerKafkaPayload(
            id = id,
            deltakerlisteId = deltakerliste.id,
            personalia = Personalia(
                navBruker.personId,
                personident = navBruker.personident,
                navn = Navn(navBruker.fornavn, navBruker.mellomnavn, navBruker.etternavn),
                kontaktinformasjon = Kontaktinformasjon(epost = navBruker.epost, telefonnummer = navBruker.telefon),
                skjermet = navBruker.erSkjermet,
                adresse = navBruker.adresse,
                adressebeskyttelse = navBruker.adressebeskyttelse,
            ),
            status = DeltakerStatusDto(
                id = status.id,
                type = status.type,
                aarsak = status.aarsak?.type,
                aarsaksbeskrivelse = status.aarsak?.beskrivelse,
                gyldigFra = status.gyldigFra,
                opprettetDato = status.opprettet,
            ),
            dagerPerUke = dagerPerUke,
            prosentStilling = deltakelsesprosent?.toDouble(),
            oppstartsdato = startdato,
            sluttdato = sluttdato,
            bestillingTekst = bakgrunnsinformasjon,
            kilde = kilde,
            innhold = deltakelsesinnhold,
            historikk = historikk,
            vurderingerFraArrangor = vurderinger,
            sistEndret = sistEndret,
            deltakerliste = Deltakerliste(
                id = deltakerliste.id,
                navn = deltakerliste.navn,
                tiltak = Tiltak(
                    navn = "trallas",
                    tiltakskode = deltakerliste.tiltak.tiltakskode,
                ),
                startdato = deltakerliste.startDato,
                sluttdato = deltakerliste.sluttDato,
                oppstartstype = deltakerliste.oppstart,
            ),
            innsoktDato = LocalDate.now(),
            forsteVedtakFattet = LocalDate.now(),
            erManueltDeltMedArrangor = false,
            sisteEndring = null,
            navKontor = null,
            navVeileder = null,
            deltarPaKurs = false,
            oppfolgingsperioder = emptyList(),
            sistEndretAv = null,
            sistEndretAvEnhet = null,
            forcedUpdate = null,
        )
    }
}
