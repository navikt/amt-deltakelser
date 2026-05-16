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
import no.nav.amt.deltaker.repository.TiltakskoordinatorDeltakerRow
import no.nav.amt.deltaker.repository.TiltakskoordinatorViewRepository
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TiltakskoordinatorResponseBuilderTest {
    private val viewRepository: TiltakskoordinatorViewRepository = mockk()
    private val digitalBrukerService: DigitalBrukerService = mockk()

    private val builder = TiltakskoordinatorResponseBuilder(
        viewRepository = viewRepository,
        digitalBrukerService = digitalBrukerService,
    )

    private val gjennomforingId = UUID.randomUUID()

    @Test
    fun `buildResponse - tom liste - returnerer tom respons uten oppslag`() = runTest {
        every { viewRepository.getForTiltakskoordinatorView(gjennomforingId) } returns emptyList()

        val response = builder.buildResponse(gjennomforingId)

        response.deltakere shouldBe emptyList()
        response.gjennomforing shouldBe null
        coVerify(exactly = 0) { digitalBrukerService.hentErDigitalForPersonidenter(any()) }
    }

    @Test
    fun `buildResponse - flere deltakere med fersk cache - ingen HTTP-fallback`() = runTest {
        val deltakerliste = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltakerliste()

        val row1 = lagRow(
            deltakerliste = deltakerliste,
            navVeilederId = UUID.randomUUID(),
            navVeilederNavn = "Veileder 1",
            navEnhetId = UUID.randomUUID(),
            navEnhetNavn = "NAV Enhet",
            erDigitalCached = true,
        )
        val row2 = lagRow(
            deltakerliste = deltakerliste,
            navVeilederId = UUID.randomUUID(),
            navVeilederNavn = "Veileder 2",
            navEnhetId = UUID.randomUUID(),
            navEnhetNavn = "NAV Enhet",
            erDigitalCached = true,
        )

        every { viewRepository.getForTiltakskoordinatorView(gjennomforingId) } returns listOf(row1, row2)

        val response = builder.buildResponse(gjennomforingId)

        response.deltakere.size shouldBe 2
        response.gjennomforing.shouldNotBeNull()
        coVerify(exactly = 0) { digitalBrukerService.hentErDigitalForPersonidenter(any()) }
    }

    @Test
    fun `buildResponse - mapper deltakerfelter korrekt`() = runTest {
        val deltakerliste = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltakerliste()
        val soktInn = LocalDate.now().minusMonths(1)
        val row = lagRow(
            deltakerliste = deltakerliste,
            startdato = LocalDate.now(),
            sluttdato = LocalDate.now().plusDays(1),
            erManueltDeltMedArrangor = true,
            soktInnDato = soktInn,
            harAktivtForslag = true,
            sisteVurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
            erDigitalCached = true,
            navVeilederId = UUID.randomUUID(),
            navVeilederNavn = "Veileder Navn",
            navEnhetId = UUID.randomUUID(),
            navEnhetNavn = "NAV Enhet",
        )

        every { viewRepository.getForTiltakskoordinatorView(gjennomforingId) } returns listOf(row)

        val response = builder.buildResponse(gjennomforingId)
        val deltakerResponse = response.deltakere.single()

        assertSoftly(deltakerResponse) {
            id shouldBe row.id
            startdato shouldBe row.startdato.shouldNotBeNull()
            sluttdato shouldBe row.sluttdato.shouldNotBeNull()
            status shouldBe row.status
            erManueltDeltMedArrangor shouldBe true
            sistEndret shouldBe row.sistEndret
            kilde shouldBe row.kilde
            opprettet shouldBe row.opprettet
            soktInnDato shouldBe soktInn
            harAktivtForslag shouldBe true
            sisteVurderingstype shouldBe Vurderingstype.OPPFYLLER_KRAVENE
            navBruker.erDigital shouldBe true
            navBruker.navVeileder?.navn shouldBe "Veileder Navn"
            navBruker.navEnhet shouldBe "NAV Enhet"
            erLaastForEndringer shouldBe false
        }
    }

    @Test
    fun `buildResponse - manglende digital cache gir HTTP-fallback`() = runTest {
        val deltakerliste = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltakerliste()
        val row = lagRow(
            deltakerliste = deltakerliste,
            erDigitalCached = null,
        )

        every { viewRepository.getForTiltakskoordinatorView(gjennomforingId) } returns listOf(row)
        coEvery { digitalBrukerService.hentErDigitalForPersonidenter(setOf(row.personident)) } returns mapOf(
            row.personident to true,
        )

        val response = builder.buildResponse(gjennomforingId)

        response.deltakere
            .single()
            .navBruker.erDigital shouldBe true
        coVerify(exactly = 1) { digitalBrukerService.hentErDigitalForPersonidenter(setOf(row.personident)) }
    }

    @Test
    fun `buildResponse - forslag og vurdering fra SQL uten ekstra spørringer`() = runTest {
        val deltakerliste = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltakerliste()
        val row1 = lagRow(
            deltakerliste = deltakerliste,
            harAktivtForslag = true,
            sisteVurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
            erDigitalCached = true,
        )
        val row2 = lagRow(
            deltakerliste = deltakerliste,
            harAktivtForslag = false,
            sisteVurderingstype = null,
            erDigitalCached = true,
        )

        every { viewRepository.getForTiltakskoordinatorView(gjennomforingId) } returns listOf(row1, row2)

        val response = builder.buildResponse(gjennomforingId)

        response.deltakere[0].harAktivtForslag shouldBe true
        response.deltakere[1].harAktivtForslag shouldBe false
        response.deltakere[0].sisteVurderingstype shouldBe Vurderingstype.OPPFYLLER_KRAVENE
        response.deltakere[1].sisteVurderingstype shouldBe null
        coVerify(exactly = 0) { digitalBrukerService.hentErDigitalForPersonidenter(any()) }
    }

    private fun lagRow(
        deltakerliste: no.nav.amt.deltaker.model.Deltakerliste,
        navVeilederId: UUID? = null,
        navVeilederNavn: String? = null,
        navVeilederEpost: String? = null,
        navVeilederTelefon: String? = null,
        navEnhetId: UUID? = null,
        navEnhetNavn: String? = null,
        startdato: LocalDate? = null,
        sluttdato: LocalDate? = null,
        erManueltDeltMedArrangor: Boolean = false,
        soktInnDato: LocalDate? = null,
        harAktivtForslag: Boolean = false,
        sisteVurderingstype: Vurderingstype? = null,
        erDigitalCached: Boolean? = false,
    ) = TiltakskoordinatorDeltakerRow(
        id = UUID.randomUUID(),
        personident = "1234567890${(1..9).random()}",
        startdato = startdato,
        sluttdato = sluttdato,
        sistEndret = LocalDateTime.now(),
        kilde = Kilde.KOMET,
        erManueltDeltMedArrangor = erManueltDeltMedArrangor,
        opprettet = LocalDateTime.now(),
        status = DeltakerStatus(
            id = UUID.randomUUID(),
            type = DeltakerStatus.Type.DELTAR,
            aarsak = null,
            gyldigFra = LocalDateTime.now(),
            gyldigTil = null,
            opprettet = LocalDateTime.now(),
        ),
        fornavn = "Fornavn",
        mellomnavn = null,
        etternavn = "Etternavn",
        erSkjermet = false,
        adresse = null,
        adressebeskyttelse = null,
        navVeilederId = navVeilederId,
        navVeilederNavn = navVeilederNavn,
        navVeilederEpost = navVeilederEpost,
        navVeilederTelefon = navVeilederTelefon,
        navEnhetId = navEnhetId,
        navEnhetNavn = navEnhetNavn,
        deltakerliste = deltakerliste,
        overordnetArrangorNavn = null,
        soktInnDato = soktInnDato,
        harAktivtForslag = harAktivtForslag,
        sisteVurderingstype = sisteVurderingstype,
        erDigitalCached = erDigitalCached,
        vedtakFattet = null,
        innsoektDatoArena = null,
        prisinformasjon = deltakerliste.prisinformasjon,
    )
}
