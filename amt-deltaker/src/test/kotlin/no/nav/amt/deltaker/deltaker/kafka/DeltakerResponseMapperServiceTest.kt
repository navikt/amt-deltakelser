package no.nav.amt.deltaker.deltaker.kafka

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerTestUtils.sammenlignHistorikk
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.innsok.InnsokPaaFellesOppstartRepository
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerKafkaPayloadBuilder
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagEndringFraArrangor
import no.nav.amt.deltaker.utils.data.TestData.lagForslag
import no.nav.amt.deltaker.utils.data.TestData.lagNavAnsatt
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagNavEnhet
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerStatusDto
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.Personalia
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerResponseMapperServiceTest {
    private val navEnhetRepository = NavEnhetRepository()
    private val navAnsattRepository = NavAnsattRepository()
    private val deltakerEndringRepository = DeltakerEndringRepository()
    private val vurderingRepository = VurderingRepository()
    private val vedtakRepository = VedtakRepository()
    private val forslagRepository = ForslagRepository()
    private val importertFraArenaRepository = ImportertFraArenaRepository()
    private val endringFraArrangorRepository = EndringFraArrangorRepository()

    private val deltakerHistorikkService = DeltakerHistorikkService(
        deltakerEndringRepository,
        vedtakRepository,
        forslagRepository,
        endringFraArrangorRepository,
        importertFraArenaRepository,
        InnsokPaaFellesOppstartRepository(),
        EndringFraTiltakskoordinatorRepository(),
        vurderingRepository,
    )
    private val deltakerKafkaPayloadBuilder =
        DeltakerKafkaPayloadBuilder(navAnsattRepository, navEnhetRepository, deltakerHistorikkService, vurderingRepository)

    private val sistEndretAvNavEnhet = lagNavEnhet()
    private val sistEndretAvNavAnsatt = lagNavAnsatt(navEnhetId = sistEndretAvNavEnhet.id)

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(sistEndretAvNavEnhet)
        navAnsattRepository.upsert(sistEndretAvNavAnsatt)
    }

    @Test
    fun `tilDeltakerV2Dto - utkast til pamelding - returnerer riktig DeltakerV2Dto`() = runTest {
        val navBruker = lagNavBruker()
        TestRepository.insert(navBruker)
        val deltaker = lagDeltaker(
            navBruker = navBruker,
            status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
        )
        TestRepository.insert(deltaker)
        val vedtak = lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
            fattet = null,
        )
        TestRepository.insert(vedtak)

        val deltakerV2Dto = deltakerKafkaPayloadBuilder.buildDeltakerV2Record(deltaker)

        // Her kan vi legge inn flere assertions på deltakerliste
        sammenlignPersonalia(deltakerV2Dto.personalia, navBruker)
        sammenlignStatus(deltakerV2Dto.status, deltaker.status)
        sammenlignHistorikk(deltakerV2Dto.historikk?.first()!!, DeltakerHistorikk.Vedtak(vedtak))

        val brukersNavEnhet = navEnhetRepository.get(navBruker.navEnhetId.shouldNotBeNull()).shouldNotBeNull()
        val brukersVeileder = navAnsattRepository.get(navBruker.navVeilederId.shouldNotBeNull()).shouldNotBeNull()

        assertSoftly(deltakerV2Dto) {
            id shouldBe deltaker.id
            deltakerliste.id shouldBe deltaker.deltakerliste.id
            deltakerliste.tiltak.tiltakskode shouldBe deltaker.deltakerliste.tiltakstype.tiltakskode
            dagerPerUke shouldBe deltaker.dagerPerUke
            prosentStilling shouldBe deltaker.deltakelsesprosent?.toDouble()
            oppstartsdato shouldBe deltaker.startdato
            sluttdato shouldBe deltaker.sluttdato
            innsoktDato shouldBe vedtak.opprettet.toLocalDate()
            forsteVedtakFattet shouldBe null
            bestillingTekst shouldBe deltaker.bakgrunnsinformasjon
            navKontor shouldBe brukersNavEnhet.navn
            navVeileder shouldBe brukersVeileder
            deltarPaKurs shouldBe deltaker.deltarPaKurs()
            kilde shouldBe Kilde.KOMET
            innhold shouldBe Deltakelsesinnhold(deltaker.deltakelsesinnhold!!.ledetekst, deltaker.deltakelsesinnhold.innhold)
            historikk?.size shouldBe 1
            sistEndret shouldBeCloseTo deltaker.sistEndret
            sistEndretAv shouldBe sistEndretAvNavAnsatt.id
            sistEndretAvEnhet shouldBe sistEndretAvNavEnhet.id
        }
    }

    @Test
    fun `tilDeltakerV2Dto - har sluttet - returnerer riktig DeltakerV2Dto`() = runTest {
        val navBruker = lagNavBruker()
        TestRepository.insert(navBruker)
        val deltaker = lagDeltaker(
            navBruker = navBruker,
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.ANNET,
                beskrivelse = "Flyttet",
            ),
        )
        TestRepository.insert(deltaker)
        val vedtak = lagVedtak(
            deltakerId = deltaker.id,
            deltakerVedVedtak = deltaker,
            opprettetAv = sistEndretAvNavAnsatt,
            opprettetAvEnhet = sistEndretAvNavEnhet,
            opprettet = LocalDateTime.now().minusWeeks(3),
            fattet = LocalDateTime.now().minusWeeks(1),
        )
        TestRepository.insert(vedtak)

        val endring = lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navBruker.navVeilederId.shouldNotBeNull(),
            endretAvEnhet = navBruker.navEnhetId.shouldNotBeNull(),
            endret = LocalDateTime.now().minusDays(2),
        )
        deltakerEndringRepository.upsert(endring)

        val forslag = lagForslag(
            deltakerId = deltaker.id,
            status = Forslag.Status.Tilbakekalt(
                tilbakekaltAvArrangorAnsattId = UUID.randomUUID(),
                tilbakekalt = LocalDateTime.now().minusDays(1),
            ),
        )
        forslagRepository.upsert(forslag)

        val endringFraArrangor = lagEndringFraArrangor(
            deltakerId = deltaker.id,
            opprettet = LocalDateTime.now(),
        )
        endringFraArrangorRepository.insert(endringFraArrangor)

        val deltakerV2Dto = deltakerKafkaPayloadBuilder.buildDeltakerV2Record(deltaker)

        deltakerV2Dto.id shouldBe deltaker.id

        deltakerV2Dto.deltakerliste.id shouldBe deltaker.deltakerliste.id
        deltakerV2Dto.deltakerliste.gjennomforingstype shouldBe deltaker.deltakerliste.gjennomforingstype

        sammenlignPersonalia(deltakerV2Dto.personalia, navBruker)
        sammenlignStatus(deltakerV2Dto.status, deltaker.status)
        sammenlignHistorikk(deltakerV2Dto.historikk?.get(0)!!, DeltakerHistorikk.EndringFraArrangor(endringFraArrangor))
        sammenlignHistorikk(deltakerV2Dto.historikk!![1], DeltakerHistorikk.Forslag(forslag))
        sammenlignHistorikk(deltakerV2Dto.historikk!![2], DeltakerHistorikk.Endring(endring))
        sammenlignHistorikk(deltakerV2Dto.historikk!![3], DeltakerHistorikk.Vedtak(vedtak))

        val brukersNavEnhet = navEnhetRepository.get(navBruker.navEnhetId.shouldNotBeNull()).shouldNotBeNull()
        val brukersVeileder = navAnsattRepository.get(navBruker.navVeilederId.shouldNotBeNull()).shouldNotBeNull()

        assertSoftly(deltakerV2Dto) {
            dagerPerUke shouldBe deltaker.dagerPerUke
            prosentStilling shouldBe deltaker.deltakelsesprosent?.toDouble()
            oppstartsdato shouldBe deltaker.startdato
            sluttdato shouldBe deltaker.sluttdato
            innsoktDato shouldBe vedtak.opprettet.toLocalDate()
            forsteVedtakFattet shouldBe LocalDateTime.now().minusWeeks(1).toLocalDate()
            bestillingTekst shouldBe deltaker.bakgrunnsinformasjon
            navKontor shouldBe brukersNavEnhet.navn
            navVeileder shouldBe brukersVeileder
            deltarPaKurs shouldBe deltaker.deltarPaKurs()
            kilde shouldBe Kilde.KOMET
            innhold shouldBe Deltakelsesinnhold(deltaker.deltakelsesinnhold!!.ledetekst, deltaker.deltakelsesinnhold.innhold)
            historikk?.size shouldBe 4
            sistEndret shouldBeCloseTo deltaker.sistEndret
            sistEndretAv shouldBe brukersVeileder.id
            sistEndretAvEnhet shouldBe brukersNavEnhet.id
        }
    }

    @Test
    fun `tilDeltakerV2Dto - importert fra arena - returnerer riktig DeltakerV2Dto`() = runTest {
        val navBruker = lagNavBruker()
        TestRepository.insert(navBruker)
        val deltaker = lagDeltaker(
            navBruker = navBruker,
            bakgrunnsinformasjon = null,
            innhold = null,
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.ANNET,
                beskrivelse = "Flyttet",
            ),
            kilde = Kilde.ARENA,
        )
        TestRepository.insert(deltaker)
        val innsoktDato = LocalDate.now().minusMonths(5)
        val importertFraArena = ImportertFraArena(
            deltakerId = deltaker.id,
            importertDato = LocalDateTime.now().minusWeeks(2),
            deltakerVedImport = deltaker.toDeltakerVedImport(innsoktDato),
        )
        importertFraArenaRepository.upsert(importertFraArena)

        val deltakerV2Dto = deltakerKafkaPayloadBuilder.buildDeltakerV2Record(deltaker)
        sammenlignHistorikk(deltakerV2Dto.historikk?.get(0)!!, DeltakerHistorikk.ImportertFraArena(importertFraArena))
        sammenlignPersonalia(deltakerV2Dto.personalia, navBruker)
        sammenlignStatus(deltakerV2Dto.status, deltaker.status)

        val brukersNavEnhet = navEnhetRepository.get(navBruker.navEnhetId.shouldNotBeNull()).shouldNotBeNull()
        val brukersVeileder = navAnsattRepository.get(navBruker.navVeilederId.shouldNotBeNull()).shouldNotBeNull()

        assertSoftly(deltakerV2Dto) {
            id shouldBe deltaker.id
            deltakerliste.id shouldBe deltaker.deltakerliste.id
            dagerPerUke shouldBe deltaker.dagerPerUke
            prosentStilling shouldBe deltaker.deltakelsesprosent?.toDouble()
            oppstartsdato shouldBe deltaker.startdato
            sluttdato shouldBe deltaker.sluttdato
            it.innsoktDato shouldBe innsoktDato
            forsteVedtakFattet shouldBe innsoktDato
            bestillingTekst shouldBe deltaker.bakgrunnsinformasjon
            navKontor shouldBe brukersNavEnhet.navn
            navVeileder shouldBe brukersVeileder
            deltarPaKurs shouldBe deltaker.deltarPaKurs()
            kilde shouldBe Kilde.ARENA
            innhold shouldBe deltaker.deltakelsesinnhold
            historikk?.size shouldBe 1
            sistEndret shouldBeCloseTo deltaker.sistEndret
            sistEndretAv shouldBe null
            sistEndretAvEnhet shouldBe null
        }
    }

    @Test
    fun `tilDeltakerV2Dto - importert fra arena, endret - returnerer riktig DeltakerV2Dto`() = runTest {
        val navBruker = lagNavBruker()
        TestRepository.insert(navBruker)
        val deltaker = lagDeltaker(
            navBruker = navBruker,
            bakgrunnsinformasjon = null,
            innhold = null,
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.ANNET,
                beskrivelse = "Flyttet",
            ),
            kilde = Kilde.ARENA,
        )
        TestRepository.insert(deltaker)
        val innsoktDato = LocalDate.now().minusMonths(5)
        val importertFraArena = ImportertFraArena(
            deltakerId = deltaker.id,
            importertDato = LocalDateTime.now().minusWeeks(2),
            deltakerVedImport = deltaker.toDeltakerVedImport(innsoktDato),
        )
        importertFraArenaRepository.upsert(importertFraArena)

        val endring = lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navBruker.navVeilederId.shouldNotBeNull(),
            endretAvEnhet = navBruker.navEnhetId.shouldNotBeNull(),
            endret = LocalDateTime.now().minusDays(2),
        )
        deltakerEndringRepository.upsert(endring)

        val deltakerV2Dto = deltakerKafkaPayloadBuilder.buildDeltakerV2Record(deltaker)

        sammenlignPersonalia(deltakerV2Dto.personalia, navBruker)
        sammenlignStatus(deltakerV2Dto.status, deltaker.status)
        sammenlignHistorikk(deltakerV2Dto.historikk?.get(0)!!, DeltakerHistorikk.Endring(endring))

        val brukersNavEnhet = navEnhetRepository.get(navBruker.navEnhetId.shouldNotBeNull()).shouldNotBeNull()
        val brukersVeileder = navAnsattRepository.get(navBruker.navVeilederId.shouldNotBeNull()).shouldNotBeNull()

        assertSoftly(deltakerV2Dto) {
            id shouldBe deltaker.id
            deltakerliste.id shouldBe deltaker.deltakerliste.id
            dagerPerUke shouldBe deltaker.dagerPerUke
            prosentStilling shouldBe deltaker.deltakelsesprosent?.toDouble()
            oppstartsdato shouldBe deltaker.startdato
            sluttdato shouldBe deltaker.sluttdato
            it.innsoktDato shouldBe innsoktDato
            forsteVedtakFattet shouldBe innsoktDato
            bestillingTekst shouldBe deltaker.bakgrunnsinformasjon
            navKontor shouldBe brukersNavEnhet.navn
            navVeileder shouldBe brukersVeileder
            deltarPaKurs shouldBe deltaker.deltarPaKurs()
            kilde shouldBe Kilde.ARENA
            innhold shouldBe deltaker.deltakelsesinnhold
            historikk?.size shouldBe 2
            sistEndret shouldBeCloseTo deltaker.sistEndret
            sistEndretAv shouldBe brukersVeileder.id
            sistEndretAvEnhet shouldBe brukersNavEnhet.id
        }
    }

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        private fun sammenlignPersonalia(personaliaDto: Personalia, navBruker: NavBruker) {
            personaliaDto.personId shouldBe navBruker.personId
            personaliaDto.personident shouldBe navBruker.personident
            personaliaDto.navn.fornavn shouldBe navBruker.fornavn
            personaliaDto.navn.mellomnavn shouldBe navBruker.mellomnavn
            personaliaDto.navn.etternavn shouldBe navBruker.etternavn
            personaliaDto.kontaktinformasjon.telefonnummer shouldBe navBruker.telefon
            personaliaDto.kontaktinformasjon.epost shouldBe navBruker.epost
            personaliaDto.skjermet shouldBe navBruker.erSkjermet
            personaliaDto.adresse shouldBe navBruker.adresse
            personaliaDto.adressebeskyttelse shouldBe navBruker.adressebeskyttelse
        }

        private fun sammenlignStatus(deltakerStatusDto: DeltakerStatusDto, deltakerStatus: DeltakerStatus) {
            deltakerStatusDto.id shouldBe deltakerStatus.id
            deltakerStatusDto.type shouldBe deltakerStatus.type
            deltakerStatusDto.aarsak shouldBe deltakerStatus.aarsak?.type
            deltakerStatusDto.aarsaksbeskrivelse shouldBe deltakerStatus.aarsak?.beskrivelse
            deltakerStatusDto.gyldigFra shouldBeCloseTo deltakerStatus.gyldigFra
            deltakerStatusDto.opprettetDato shouldBeCloseTo deltakerStatus.opprettet
        }
    }
}
