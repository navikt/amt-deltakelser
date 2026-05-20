package no.nav.amt.deltaker.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.api.response.TiltakskoordinatorResponseBuilder
import no.nav.amt.deltaker.digitalbruker.DigitalBrukerService
import no.nav.amt.deltaker.repository.DeltakerlisteRepository
import no.nav.amt.deltaker.repository.TiltakskoordinatorDeltakerRow
import no.nav.amt.deltaker.repository.TiltakskoordinatorViewRepository
import no.nav.amt.deltaker.tiltaksarrangor.ArrangorService
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TiltakskoordinatorResponseBuilderTest {
    private val viewRepository: TiltakskoordinatorViewRepository = mockk()
    private val deltakerlisteRepository: DeltakerlisteRepository = mockk()
    private val arrangorService: ArrangorService = mockk()
    private val digitalBrukerService: DigitalBrukerService = mockk()

    private val builder = TiltakskoordinatorResponseBuilder(
        viewRepository = viewRepository,
        deltakerlisteRepository = deltakerlisteRepository,
        arrangorService = arrangorService,
        digitalBrukerService = digitalBrukerService,
    )

    private val gjennomforingId = UUID.randomUUID()
    private val defaultDeltakerliste = lagDeltakerliste()

    private fun mockGjennomforing(paameldingstype: GjennomforingPameldingType) {
        every {
            deltakerlisteRepository.get(gjennomforingId)
        } returns Result.success(defaultDeltakerliste.copy(pameldingstype = paameldingstype))
        every { arrangorService.getArrangorNavn(any(), any()) } returns "Arrangør Navn"
    }

    @Test
    fun `buildResponse - gjennomforing finnes ikke - returnerer tom respons`() = runTest {
        every { deltakerlisteRepository.get(gjennomforingId) } returns Result.failure(NoSuchElementException())

        val response = builder.buildResponse(gjennomforingId)

        response.deltakere shouldBe emptyList()
        response.gjennomforing shouldBe null
        coVerify(exactly = 0) { digitalBrukerService.hentErDigitalForPersonidenter(any()) }
    }

    @Test
    fun `buildResponse - flere deltakere med fersk cache - ingen HTTP-fallback`() = runTest {
        mockGjennomforing(GjennomforingPameldingType.TRENGER_GODKJENNING)
        val row1 = lagRow(
            erDigitalCached = true,
            navEnhetNavn = "NAV Enhet",
        )
        val row2 = lagRow(
            erDigitalCached = true,
            navEnhetNavn = "NAV Enhet",
        )

        every { viewRepository.getDeltakere(gjennomforingId) } returns listOf(row1, row2)

        val response = builder.buildResponse(gjennomforingId)

        response.deltakere.size shouldBe 2
        response.gjennomforing.shouldNotBeNull()
        coVerify(exactly = 0) { digitalBrukerService.hentErDigitalForPersonidenter(any()) }
    }

    @Test
    fun `buildResponse - mapper deltakerfelter korrekt`() = runTest {
        mockGjennomforing(GjennomforingPameldingType.TRENGER_GODKJENNING)
        val soktInn = LocalDate.now().minusMonths(1)
        val row = lagRow(
            startdato = LocalDate.now(),
            sluttdato = LocalDate.now().plusDays(1),
            erManueltDeltMedArrangor = true,
            soktInnDato = soktInn,
            harAktivtForslag = true,
            sisteVurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
            erDigitalCached = true,
            navEnhetNavn = "NAV Enhet",
        )

        every { viewRepository.getDeltakere(gjennomforingId) } returns listOf(row)

        val response = builder.buildResponse(gjennomforingId)
        val deltakerResponse = response.deltakere.single()

        assertSoftly(deltakerResponse) {
            id shouldBe row.id
            startdato shouldBe row.startdato.shouldNotBeNull()
            sluttdato shouldBe row.sluttdato.shouldNotBeNull()
            erManueltDeltMedArrangor shouldBe true
            soktInnDato shouldBe soktInn
            harAktivtForslag shouldBe true
            sisteVurderingstype shouldBe Vurderingstype.OPPFYLLER_KRAVENE
            navBruker.ikkeDigitalOgManglerAdresse shouldBe false
            navBruker.navEnhet shouldBe "NAV Enhet"
        }
    }

    @Test
    fun `buildResponse - manglende digital cache trenger ikke HTTP-fallback`() = runTest {
        mockGjennomforing(GjennomforingPameldingType.DIREKTE_VEDTAK)
        val row = lagRow(erDigitalCached = null)

        every { viewRepository.getDeltakere(gjennomforingId) } returns listOf(row)
        coEvery { digitalBrukerService.hentErDigitalForPersonidenter(setOf(row.personident)) } returns mapOf(
            row.personident to true,
        )

        val response = builder.buildResponse(gjennomforingId)

        response.deltakere
            .single()
            .navBruker.ikkeDigitalOgManglerAdresse shouldBe false

        coVerify(exactly = 0) {
            digitalBrukerService.hentErDigitalForPersonidenter(setOf(row.personident))
        }
    }

    @Test
    fun `buildResponse - manglende digital cache gir HTTP-fallback`() = runTest {
        mockGjennomforing(GjennomforingPameldingType.TRENGER_GODKJENNING)
        val row = lagRow(erDigitalCached = null, harAdresse = false)

        every { viewRepository.getDeltakere(gjennomforingId) } returns listOf(row)
        coEvery {
            digitalBrukerService.hentErDigitalForPersonidenter(setOf(row.personident))
        } returns mapOf(row.personident to false)

        val response = builder.buildResponse(gjennomforingId)

        response.deltakere
            .single()
            .navBruker.ikkeDigitalOgManglerAdresse shouldBe true

        coVerify(exactly = 1) { digitalBrukerService.hentErDigitalForPersonidenter(setOf(row.personident)) }
    }

    @Test
    fun `buildResponse - forslag og vurdering fra SQL uten ekstra spørringer`() = runTest {
        mockGjennomforing(GjennomforingPameldingType.TRENGER_GODKJENNING)
        val row1 = lagRow(harAktivtForslag = true, sisteVurderingstype = Vurderingstype.OPPFYLLER_KRAVENE, erDigitalCached = true)
        val row2 = lagRow(harAktivtForslag = false, sisteVurderingstype = null, erDigitalCached = true)

        every { viewRepository.getDeltakere(gjennomforingId) } returns listOf(row1, row2)

        val response = builder.buildResponse(gjennomforingId)

        response.deltakere[0].harAktivtForslag shouldBe true
        response.deltakere[1].harAktivtForslag shouldBe false
        response.deltakere[0].sisteVurderingstype shouldBe Vurderingstype.OPPFYLLER_KRAVENE
        response.deltakere[1].sisteVurderingstype shouldBe null
        coVerify(exactly = 0) { digitalBrukerService.hentErDigitalForPersonidenter(any()) }
    }

    @Test
    fun `buildResponse - flere deltakelser for samme person - returnerer kun nyeste`() = runTest {
        mockGjennomforing(GjennomforingPameldingType.TRENGER_GODKJENNING)
        val personident = "12345678901"

        val gammelAvsluttet = lagRow(
            personident = personident,
            statusType = DeltakerStatus.Type.HAR_SLUTTET,
            statusGyldigFra = LocalDateTime.now().minusMonths(6),
            vedtakFattet = LocalDateTime.now().minusMonths(8),
            erDigitalCached = true,
        )
        val nyAktiv = lagRow(
            personident = personident,
            statusType = DeltakerStatus.Type.DELTAR,
            statusGyldigFra = LocalDateTime.now().minusDays(10),
            vedtakFattet = LocalDateTime.now().minusDays(14),
            erDigitalCached = true,
        )

        every { viewRepository.getDeltakere(gjennomforingId) } returns listOf(gammelAvsluttet, nyAktiv)

        val response = builder.buildResponse(gjennomforingId)

        response.deltakere.size shouldBe 1
        response.deltakere.single().id shouldBe nyAktiv.id
    }

    @Test
    fun `buildResponse - flere deltakelser for samme person uten aktiv - velger nyeste`() = runTest {
        mockGjennomforing(GjennomforingPameldingType.TRENGER_GODKJENNING)
        val personident = "12345678901"

        val eldreAvsluttet = lagRow(
            personident = personident,
            statusType = DeltakerStatus.Type.HAR_SLUTTET,
            statusGyldigFra = LocalDateTime.now().minusMonths(6),
            vedtakFattet = LocalDateTime.now().minusMonths(8),
            erDigitalCached = true,
        )
        val nyereAvsluttet = lagRow(
            personident = personident,
            statusType = DeltakerStatus.Type.IKKE_AKTUELL,
            statusGyldigFra = LocalDateTime.now().minusDays(10),
            vedtakFattet = LocalDateTime.now().minusDays(14),
            erDigitalCached = true,
        )

        every { viewRepository.getDeltakere(gjennomforingId) } returns listOf(eldreAvsluttet, nyereAvsluttet)

        val response = builder.buildResponse(gjennomforingId)

        response.deltakere.size shouldBe 1
        response.deltakere.single().id shouldBe nyereAvsluttet.id
    }

    @Test
    fun `buildResponse - aktiv status velges over nyere avsluttet deltakelse`() = runTest {
        mockGjennomforing(GjennomforingPameldingType.TRENGER_GODKJENNING)
        val personident = "12345678901"

        val aktiv = lagRow(
            personident = personident,
            statusType = DeltakerStatus.Type.VENTER_PA_OPPSTART,
            statusGyldigFra = LocalDateTime.now().minusDays(30),
            vedtakFattet = LocalDateTime.now().minusDays(30),
            erDigitalCached = true,
        )
        val nyereAvsluttet = lagRow(
            personident = personident,
            statusType = DeltakerStatus.Type.HAR_SLUTTET,
            statusGyldigFra = LocalDateTime.now().minusDays(5),
            vedtakFattet = LocalDateTime.now().minusDays(3),
            erDigitalCached = true,
        )

        every { viewRepository.getDeltakere(gjennomforingId) } returns listOf(nyereAvsluttet, aktiv)

        val response = builder.buildResponse(gjennomforingId)

        response.deltakere.size shouldBe 1
        response.deltakere.single().id shouldBe aktiv.id
    }

    @Test
    fun `buildResponse - ulike personer gir en rad per person`() = runTest {
        mockGjennomforing(GjennomforingPameldingType.TRENGER_GODKJENNING)

        val person1Aktiv = lagRow(
            personident = "11111111111",
            statusType = DeltakerStatus.Type.DELTAR,
            erDigitalCached = true,
        )
        val person1Avsluttet = lagRow(
            personident = "11111111111",
            statusType = DeltakerStatus.Type.HAR_SLUTTET,
            statusGyldigFra = LocalDateTime.now().minusMonths(3),
            vedtakFattet = LocalDateTime.now().minusMonths(4),
            erDigitalCached = true,
        )
        val person2Aktiv = lagRow(
            personident = "22222222222",
            statusType = DeltakerStatus.Type.DELTAR,
            erDigitalCached = true,
        )

        every { viewRepository.getDeltakere(gjennomforingId) } returns listOf(person1Aktiv, person1Avsluttet, person2Aktiv)

        val response = builder.buildResponse(gjennomforingId)

        response.deltakere.size shouldBe 2
        response.deltakere.map { it.id }.toSet() shouldBe setOf(person1Aktiv.id, person2Aktiv.id)
    }

    private var personidentCounter = 0

    private fun lagRow(
        personident: String = "1234567${"%04d".format(++personidentCounter)}",
        statusType: DeltakerStatus.Type = DeltakerStatus.Type.DELTAR,
        statusGyldigFra: LocalDateTime = LocalDateTime.now(),
        vedtakFattet: LocalDateTime? = null,
        innsoektDatoArena: LocalDate? = null,
        navEnhetNavn: String? = null,
        startdato: LocalDate? = null,
        sluttdato: LocalDate? = null,
        erManueltDeltMedArrangor: Boolean = false,
        soktInnDato: LocalDate? = null,
        harAktivtForslag: Boolean = false,
        sisteVurderingstype: Vurderingstype? = null,
        erDigitalCached: Boolean? = false,
        harAdresse: Boolean = true,
    ) = TiltakskoordinatorDeltakerRow(
        id = UUID.randomUUID(),
        personident = personident,
        startdato = startdato,
        sluttdato = sluttdato,
        erManueltDeltMedArrangor = erManueltDeltMedArrangor,
        status = DeltakerStatus(
            id = UUID.randomUUID(),
            type = statusType,
            aarsak = null,
            gyldigFra = statusGyldigFra,
            gyldigTil = null,
            opprettet = LocalDateTime.now(),
        ),
        fornavn = "Fornavn",
        mellomnavn = null,
        etternavn = "Etternavn",
        erSkjermet = false,
        harAdresse = harAdresse,
        adressebeskyttelse = null,
        navEnhetNavn = navEnhetNavn,
        soktInnDato = soktInnDato,
        harAktivtForslag = harAktivtForslag,
        sisteVurderingstype = sisteVurderingstype,
        erDigitalCached = erDigitalCached,
        vedtakFattet = vedtakFattet,
        innsoektDatoArena = innsoektDatoArena,
    )
}
