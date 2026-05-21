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
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.internapi.deltaker.request.TiltaksKoordinatorDeltakerlisteRequest
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
    private val digitalBrukerService: DigitalBrukerService = mockk()

    private val builder = TiltakskoordinatorResponseBuilder(
        viewRepository = viewRepository,
        deltakerlisteRepository = deltakerlisteRepository,
        digitalBrukerService = digitalBrukerService,
    )

    private val gjennomforingId = UUID.randomUUID()
    private val defaultDeltakerliste = lagDeltakerliste()

    private fun mockGjennomforing(paameldingstype: GjennomforingPameldingType) {
        every {
            deltakerlisteRepository.get(gjennomforingId)
        } returns Result.success(defaultDeltakerliste.copy(pameldingstype = paameldingstype))
    }

    private fun mockDeltakere(rows: List<TiltakskoordinatorDeltakerRow>) {
        every { viewRepository.getDeltakere(any()) } returns rows
    }

    @Test
    fun `buildResponse - gjennomforing finnes ikke - returnerer tom respons`() = runTest {
        every {
            deltakerlisteRepository.get(gjennomforingId)
        } returns Result.failure(NoSuchElementException())

        val response = builder.buildResponse(TiltaksKoordinatorDeltakerlisteRequest(gjennomforingId = gjennomforingId))

        response.data shouldBe emptyList()
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

        mockDeltakere(listOf(row1, row2))

        val response = builder.buildResponse(TiltaksKoordinatorDeltakerlisteRequest(gjennomforingId = gjennomforingId))

        response.data.size shouldBe 2
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

        mockDeltakere(listOf(row))

        val response = builder.buildResponse(TiltaksKoordinatorDeltakerlisteRequest(gjennomforingId = gjennomforingId))
        val deltakerResponse = response.data.single()

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

        mockDeltakere(listOf(row))
        coEvery { digitalBrukerService.hentErDigitalForPersonidenter(setOf(row.personident)) } returns mapOf(
            row.personident to true,
        )

        val response = builder.buildResponse(TiltaksKoordinatorDeltakerlisteRequest(gjennomforingId = gjennomforingId))

        response.data
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

        mockDeltakere(listOf(row))
        coEvery {
            digitalBrukerService.hentErDigitalForPersonidenter(setOf(row.personident))
        } returns mapOf(row.personident to false)

        val response = builder.buildResponse(TiltaksKoordinatorDeltakerlisteRequest(gjennomforingId = gjennomforingId))

        response.data
            .single()
            .navBruker.ikkeDigitalOgManglerAdresse shouldBe true

        coVerify(exactly = 1) { digitalBrukerService.hentErDigitalForPersonidenter(setOf(row.personident)) }
    }

    @Test
    fun `buildResponse - forslag og vurdering fra SQL uten ekstra spørringer`() = runTest {
        mockGjennomforing(GjennomforingPameldingType.TRENGER_GODKJENNING)
        val row1 = lagRow(harAktivtForslag = true, sisteVurderingstype = Vurderingstype.OPPFYLLER_KRAVENE, erDigitalCached = true)
        val row2 = lagRow(harAktivtForslag = false, sisteVurderingstype = null, erDigitalCached = true)

        mockDeltakere(listOf(row1, row2))

        val response = builder.buildResponse(TiltaksKoordinatorDeltakerlisteRequest(gjennomforingId = gjennomforingId))

        assertSoftly(response.data.first()) {
            harAktivtForslag shouldBe true
            sisteVurderingstype shouldBe Vurderingstype.OPPFYLLER_KRAVENE
        }

        assertSoftly(response.data.last()) {
            harAktivtForslag shouldBe false
            sisteVurderingstype shouldBe null
        }

        coVerify(exactly = 0) { digitalBrukerService.hentErDigitalForPersonidenter(any()) }
    }

    @Test
    fun `buildResponse - setter totalCount og pageSize fra repository og request`() = runTest {
        val builderMedPagination = TiltakskoordinatorResponseBuilder(
            viewRepository = viewRepository,
            deltakerlisteRepository = deltakerlisteRepository,
            digitalBrukerService = digitalBrukerService,
        )
        mockGjennomforing(GjennomforingPameldingType.TRENGER_GODKJENNING)
        val request = TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = gjennomforingId,
        )
        val row = lagRow(erDigitalCached = true)

        every { viewRepository.getDeltakere(request) } returns listOf(row)

        val response = builderMedPagination.buildResponse(request)

        response.totalCount shouldBe 1
        response.pageSize shouldBe 1
        response.data.size shouldBe 1
    }

    @Test
    fun `buildResponse - bruker separat count-cache for ulike filterkombinasjoner`() = runTest {
        val builderMedPagination = TiltakskoordinatorResponseBuilder(
            viewRepository = viewRepository,
            deltakerlisteRepository = deltakerlisteRepository,
            digitalBrukerService = digitalBrukerService,
        )
        mockGjennomforing(GjennomforingPameldingType.TRENGER_GODKJENNING)
        val deltarRequest = TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = gjennomforingId,
            statuser = setOf(DeltakerStatus.Type.DELTAR),
        )
        val venterPaOppstartRequest = deltarRequest.copy(
            statuser = setOf(DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )

        every { viewRepository.getDeltakere(deltarRequest) } returns listOf(lagRow(erDigitalCached = true))
        every {
            viewRepository.getDeltakere(
                venterPaOppstartRequest,
            )
        } returns listOf(lagRow(erDigitalCached = true))

        val deltarResponse = builderMedPagination.buildResponse(deltarRequest)
        val venterPaOppstartResponse = builderMedPagination.buildResponse(venterPaOppstartRequest)

        deltarResponse.totalCount shouldBe 1
        venterPaOppstartResponse.totalCount shouldBe 1
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
