package no.nav.amt.deltaker.deltaker

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.apiclients.oppfolgingstilfelle.IsOppfolgingstilfelleClient
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagNavBruker
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.deltaker.Innsatsgruppe
import no.nav.amt.lib.models.deltaker.internalapis.paamelding.request.OpprettKladdRequest
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Year
import java.util.UUID

class OpprettKladdRequestValidatorTest {
    private val deltakerListeRepository: DeltakerlisteRepository = mockk(relaxed = true)
    private val brukerService: NavBrukerService = mockk(relaxed = true)
    private val personServiceClient: AmtPersonServiceClient = mockk(relaxed = true)
    private val isOppfolgingsTilfelleClient: IsOppfolgingstilfelleClient = mockk(relaxed = true)

    private val sut = OpprettKladdRequestValidator(
        deltakerlisteRepository = deltakerListeRepository,
        brukerService = brukerService,
        personServiceClient = personServiceClient,
        isOppfolgingsTilfelleClient = isOppfolgingsTilfelleClient,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()

        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerliste(tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)),
        )

        coEvery { brukerService.get(any()) } returns Result.success(lagNavBruker())

        coEvery {
            personServiceClient.hentNavBrukerFodselsar(any())
        } returns Year.now().minusYears(20).value
    }

    @Test
    fun `validateRequest - ingen feil - skal returnere Valid`() = runTest {
        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Valid>()
    }

    @ParameterizedTest
    @EnumSource(value = GjennomforingStatusType::class, names = ["AVBRUTT", "AVLYST", "AVSLUTTET"])
    fun `validateRequest - deltakerListe er avsluttet - skal returnere Invalid`(status: GjennomforingStatusType) = runTest {
        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerliste(status = status),
        )

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("Deltakerliste er avsluttet")
    }

    @Test
    fun `validateRequest - deltakerliste ikke apen for pamelding - skal returnere Invalid`() = runTest {
        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerliste(apentForPamelding = false),
        )

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("Deltakerliste er ikke åpen for påmelding")
    }

    @Test
    fun `validateRequest - bruker sin innsatsgruppe i deltakerliste - skal returnere Valid`() = runTest {
        val tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)

        coEvery { brukerService.get(any()) } returns
            Result.success(lagNavBruker(innsatsgruppe = tiltakstype.innsatsgrupper.first()))

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Valid>()
    }

    @Test
    fun `validateRequest - ikke ARBEIDSRETTET_REHABILITERING - skal returnere Invalid`() = runTest {
        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerliste(),
        )

        coEvery { brukerService.get(any()) } returns
            Result.success(lagNavBruker(innsatsgruppe = null))

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("Bruker har ikke riktig innsatsgruppe")
    }

    @Test
    fun `validateRequest - ARBEIDSRETTET_REHABILITERING og ikke SITUASJONSBESTEMT_INNSATS - skal returnere Invalid`() = runTest {
        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerliste(tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSRETTET_REHABILITERING)),
        )

        coEvery { brukerService.get(any()) } returns
            Result.success(lagNavBruker(innsatsgruppe = null))

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("Bruker har ikke riktig innsatsgruppe")
    }

    @Test
    fun `validateRequest - ARBEIDSRETTET_REHABILITERING og SITUASJONSBESTEMT_INNSATS ikke sykmeldt - skal returnere Invalid`() = runTest {
        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerliste(tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSRETTET_REHABILITERING)),
        )

        coEvery { brukerService.get(any()) } returns
            Result.success(lagNavBruker(innsatsgruppe = Innsatsgruppe.SITUASJONSBESTEMT_INNSATS))

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("Bruker har ikke riktig innsatsgruppe")
    }

    @Test
    fun `validateRequest - ARBEIDSRETTET_REHABILITERING og SITUASJONSBESTEMT_INNSATS sykmeldt - skal returnere Valid`() = runTest {
        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerliste(tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ARBEIDSRETTET_REHABILITERING)),
        )

        coEvery { brukerService.get(any()) } returns
            Result.success(lagNavBruker(innsatsgruppe = Innsatsgruppe.SITUASJONSBESTEMT_INNSATS))

        coEvery { isOppfolgingsTilfelleClient.erSykmeldtMedArbeidsgiver(any()) } returns true

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Valid>()
    }

    @Test
    fun `validateRequest - deltakerliste GRUPPE_ARBEIDSMARKEDSOPPLAERING mangler startdato - skal returnere invalid`() = runTest {
        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerliste(
                tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING),
            ).copy(startDato = null),
        )

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe
            listOf("Deltakerliste med tiltakskode ${Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING} mangler startdato")
    }

    @Test
    fun `validateRequest - deltaker 18 ar - skal returnere Invalid`() = runTest {
        coEvery {
            personServiceClient.hentNavBrukerFodselsar(any())
        } returns Year.now().minusYears(18).value

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Invalid>()
        validationResult.reasons shouldBe listOf("DELTAKER_FOR_UNG GRUPPE_ARBEIDSMARKEDSOPPLAERING")
    }

    @Test
    fun `validateRequest - deltaker 18 ar - skal returnere Valid`() = runTest {
        every { deltakerListeRepository.get(any()) } returns Result.success(
            lagDeltakerliste(tiltakstype = lagTiltakstype()),
        )
        coEvery {
            personServiceClient.hentNavBrukerFodselsar(any())
        } returns Year.now().minusYears(17).value

        val validationResult = sut.validateRequest(requestInTest)

        validationResult.shouldBeTypeOf<ValidationResult.Valid>()
    }

    companion object {
        private val requestInTest = OpprettKladdRequest(
            deltakerlisteId = UUID.randomUUID(),
            personident = "~personident~",
        )
    }
}
