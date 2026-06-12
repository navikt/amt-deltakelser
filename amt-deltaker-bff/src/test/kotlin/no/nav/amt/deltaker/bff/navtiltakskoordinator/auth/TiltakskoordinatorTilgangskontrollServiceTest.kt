package no.nav.amt.deltaker.bff.navtiltakskoordinator.auth
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.auth.SporbarhetsloggService
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.gjennomforing.DeltakerlisteService
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.poao_tilgang.client.Decision
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class TiltakskoordinatorTilgangskontrollServiceTest {
    val mockSporbarhetsloggService = mockk<SporbarhetsloggService>(relaxed = true)
    val mockDeltakerListeService = mockk<DeltakerlisteService>(relaxed = true)
    val mockTilgangskontrollService = mockk<TilgangskontrollService>()
    val mockSelfServiceTilgangService = mockk<SelfServiceTilgangService>()

    val sut = TiltakskoordinatorTilgangskontrollService(
        sporbarhetsloggService = mockSporbarhetsloggService,
        tilgangskontrollService = mockTilgangskontrollService,
        deltakerlisteService = mockDeltakerListeService,
        selfServiceTilgangService = mockSelfServiceTilgangService,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()

        coEvery { mockSelfServiceTilgangService.verifiserTiltakskoordinatorTilgang(any(), any()) } just runs
        coEvery { mockTilgangskontrollService.vurderAdressebeskyttelseTilgang(any(), any()) } returns Decision.Permit
        coEvery { mockTilgangskontrollService.vurderSkjermingTilgang(any(), any()) } returns Decision.Permit
    }

    @Test
    fun `kontrollerTilgangTilBruker - happy path - skal kalle riktige tjenester og returnere true`() = runTest {
        val harTilgang = sut.kontrollerTilgangTilBruker(
            NAV_IDENT,
            navAnsattAzureId,
            personident = navBruker.personident,
            erSkjermet = navBruker.erSkjermet,
            adressebeskyttelse = navBruker.adressebeskyttelse,
            deltakerlisteId = deltakerlisteId,
        )

        harTilgang shouldBe true

        coVerifySequence {
            mockSporbarhetsloggService.sendAuditLog(NAV_IDENT, navBruker.personident)
            mockDeltakerListeService.verifiserTilgjengeligDeltakerliste(deltakerlisteId)
            mockSelfServiceTilgangService.verifiserTiltakskoordinatorTilgang(NAV_IDENT, deltakerlisteId)
            mockTilgangskontrollService.vurderAdressebeskyttelseTilgang(navBruker.adressebeskyttelse, navAnsattAzureId)
            mockTilgangskontrollService.vurderSkjermingTilgang(navBruker.erSkjermet, navAnsattAzureId)
        }
    }

    @Test
    fun `kontrollerTilgangTilBruker - deltakerliste ikke tilgjengelig - propagerer exception`() = runTest {
        coEvery {
            mockDeltakerListeService.verifiserTilgjengeligDeltakerliste(any())
        } throws AuthorizationException("deltakerliste ikke tilgjengelig")

        shouldThrow<AuthorizationException> {
            sut.kontrollerTilgangTilBruker(
                NAV_IDENT,
                navAnsattAzureId,
                personident = navBruker.personident,
                erSkjermet = navBruker.erSkjermet,
                adressebeskyttelse = navBruker.adressebeskyttelse,
                deltakerlisteId = deltakerlisteId,
            )
        }
    }

    @Nested
    inner class KontrollerTilgangTilBruker {
        @Test
        fun `kontrollerTilgangTilBruker - skjerming denies - returnerer false`() = runTest {
            every { mockTilgangskontrollService.vurderSkjermingTilgang(any(), any()) } returns Decision.Deny("skjermet", "")

            sut.kontrollerTilgangTilBruker(
                NAV_IDENT,
                navAnsattAzureId,
                personident = navBruker.personident,
                erSkjermet = true,
                adressebeskyttelse = null,
                deltakerlisteId = deltakerlisteId,
            ) shouldBe false
        }

        @Test
        fun `kontrollerTilgangTilBruker - adressebeskyttelse denies - returnerer false`() = runTest {
            every { mockTilgangskontrollService.vurderAdressebeskyttelseTilgang(any(), any()) } returns Decision.Deny("kode 6", "")

            sut.kontrollerTilgangTilBruker(
                NAV_IDENT,
                navAnsattAzureId,
                personident = navBruker.personident,
                erSkjermet = false,
                adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG,
                deltakerlisteId = deltakerlisteId,
            ) shouldBe false
        }

        @Test
        fun `kontrollerTilgangTilBruker - mangler tiltakskoordinatortilgang - propagerer AuthorizationException`() = runTest {
            coEvery {
                mockSelfServiceTilgangService.verifiserTiltakskoordinatorTilgang(any(), any())
            } throws AuthorizationException("ingen tilgang")

            shouldThrow<AuthorizationException> {
                sut.kontrollerTilgangTilBruker(
                    NAV_IDENT,
                    navAnsattAzureId,
                    personident = navBruker.personident,
                    erSkjermet = navBruker.erSkjermet,
                    adressebeskyttelse = navBruker.adressebeskyttelse,
                    deltakerlisteId = deltakerlisteId,
                )
            }
        }
    }

    @Nested
    inner class HarKoordinatorTilgangTilPerson {
        @Test
        fun `harKoordinatorTilgangTilPerson - begge permit - returnerer true`() {
            sut.harTilgangTilPersonMedRestriksjoner(navAnsattAzureId, erSkjermet = false, adressebeskyttelse = null) shouldBe true
        }

        @Test
        fun `harKoordinatorTilgangTilPerson - skjerming denies - returnerer false`() {
            every { mockTilgangskontrollService.vurderSkjermingTilgang(any(), any()) } returns Decision.Deny("", "")
            sut.harTilgangTilPersonMedRestriksjoner(navAnsattAzureId, erSkjermet = true, adressebeskyttelse = null) shouldBe false
        }

        @Test
        fun `harKoordinatorTilgangTilPerson - adressebeskyttelse denies - returnerer false`() {
            every { mockTilgangskontrollService.vurderAdressebeskyttelseTilgang(any(), any()) } returns Decision.Deny("", "")
            sut.harTilgangTilPersonMedRestriksjoner(
                navAnsattAzureId,
                erSkjermet = false,
                adressebeskyttelse = Adressebeskyttelse.FORTROLIG,
            ) shouldBe false
        }
    }

    @Nested
    inner class TilgangTilDeltakereGuard {
        @Test
        fun `tilgangTilDeltakereGuard - happy path - kaster ingen exception`() = runTest {
            val deltakerliste = TestData.lagDeltakerliste()

            sut.tilgangTilGjennomforingGuard(deltakerliste.id, NAV_IDENT)

            coVerifySequence {
                mockSelfServiceTilgangService.verifiserTiltakskoordinatorTilgang(NAV_IDENT, deltakerliste.id)
                mockDeltakerListeService.verifiserTilgjengeligDeltakerliste(deltakerliste.id)
            }
        }

        @Test
        fun `tilgangTilDeltakereGuard - mangler tiltakskoordinatortilgang - propagerer AuthorizationException`() = runTest {
            val deltakerliste = TestData.lagDeltakerliste()

            coEvery {
                mockSelfServiceTilgangService.verifiserTiltakskoordinatorTilgang(any(), any())
            } throws AuthorizationException("ingen tilgang")

            shouldThrow<AuthorizationException> {
                sut.tilgangTilGjennomforingGuard(deltakerliste.id, NAV_IDENT)
            }
        }

        @Test
        fun `tilgangTilDeltakereGuard - deltakerliste ikke tilgjengelig - propagerer AuthorizationException`() = runTest {
            val deltakerliste = TestData.lagDeltakerliste()

            coEvery {
                mockDeltakerListeService.verifiserTilgjengeligDeltakerliste(any())
            } throws AuthorizationException("deltakerliste ikke tilgjengelig")

            shouldThrow<AuthorizationException> {
                sut.tilgangTilGjennomforingGuard(deltakerliste.id, NAV_IDENT)
            }
        }

        @Test
        fun `tilgangTilDeltakereGuard - tom deltakerliste - kaster ingen exception`() = runTest {
            val deltakerliste = TestData.lagDeltakerliste()

            sut.tilgangTilGjennomforingGuard(deltakerliste.id, NAV_IDENT)
        }
    }

    companion object {
        private const val NAV_IDENT = "~navIdent~"
        private val navAnsattAzureId = UUID.randomUUID()
        private val navBruker = lagNavBruker()
        private val deltakerlisteId = UUID.randomUUID()
    }
}
