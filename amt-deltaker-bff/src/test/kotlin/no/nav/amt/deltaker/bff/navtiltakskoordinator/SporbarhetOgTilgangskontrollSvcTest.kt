package no.nav.amt.deltaker.bff.navtiltakskoordinator

import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.auth.TilgangskontrollService
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteService
import no.nav.amt.deltaker.bff.sporbarhet.SporbarhetsloggService
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SporbarhetOgTilgangskontrollSvcTest {
    val mockSporbarhetsloggService = mockk<SporbarhetsloggService>(relaxed = true)
    val mockDeltakerListeService = mockk<DeltakerlisteService>(relaxed = true)
    val mockTilgangskontrollService = mockk<TilgangskontrollService>()

    val sut = SporbarhetOgTilgangskontrollSvc(
        sporbarhetsloggService = mockSporbarhetsloggService,
        tilgangskontrollService = mockTilgangskontrollService,
        deltakerlisteService = mockDeltakerListeService,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()

        coEvery { mockTilgangskontrollService.verifiserTiltakskoordinatorTilgang(any(), any()) } just runs
        every { mockTilgangskontrollService.harKoordinatorTilgangTilPerson(any(), any<Boolean>(), anyNullable(), any()) } returns true
    }

    @Test
    fun `skal kalle riktige tjenester og returnere resultat`() = runTest {
        val harTilgang = sut.kontrollerTilgangTilBruker(
            NAV_IDENT,
            navAnsattAzureId,
            personident = navBruker.personident,
            erInnbyggerSkjermet = navBruker.erSkjermet,
            adressebeskyttelse = navBruker.adressebeskyttelse,
            deltakerlisteId = deltakerlisteId,
        )

        harTilgang shouldBe true

        coVerifySequence {
            mockSporbarhetsloggService.sendAuditLog(NAV_IDENT, navBruker.personident)
            mockDeltakerListeService.verifiserTilgjengeligDeltakerliste(deltakerlisteId)
            mockTilgangskontrollService.verifiserTiltakskoordinatorTilgang(NAV_IDENT, deltakerlisteId)
            mockTilgangskontrollService.harKoordinatorTilgangTilPerson(navAnsattAzureId, navBruker.erSkjermet, navBruker.adressebeskyttelse)
        }
    }

    companion object {
        private const val NAV_IDENT = "~navIdent~"
        private val navAnsattAzureId = UUID.randomUUID()
        private val navBruker = lagNavBruker()
        private val deltakerlisteId = UUID.randomUUID()
    }
}
