package no.nav.amt.deltaker.bff.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.bff.navtiltakskoordinator.auth.TiltakskoordinatorTilgangskontrollService
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.NavAnsattBehandleFortroligBrukerePolicyInput
import no.nav.poao_tilgang.client.NavAnsattBehandleSkjermedePersonerPolicyInput
import no.nav.poao_tilgang.client.NavAnsattBehandleStrengtFortroligBrukerePolicyInput
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.PolicyInput
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class TilgangskontrollServiceTest {
    private val poaoTilgangCachedClient = mockk<PoaoTilgangCachedClient>()

    private val tilgangskontrollService = TilgangskontrollService(
        poaoTilgangCachedClient,
    )

    private val tiltakskoordinatorTilgangskontrollService = TiltakskoordinatorTilgangskontrollService(
        sporbarhetsloggService = mockk(relaxed = true),
        tilgangskontrollService = tilgangskontrollService,
        selfServiceTilgangService = mockk(relaxed = true),
        deltakerlisteService = mockk(relaxed = true),
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `verifiserSkrivetilgang - har tilgang - kaster ingen feil`() {
        mockPoaoTilgangPermit()

        tilgangskontrollService.verifiserSkrivetilgang(UUID.randomUUID(), "12345")
    }

    @Test
    fun `verifiserSkrivetilgang - har ikke tilgang - kaster AuthorizationException`() {
        mockPoaoTilgangDeny()

        shouldThrow<AuthorizationException> {
            tilgangskontrollService.verifiserSkrivetilgang(UUID.randomUUID(), "12345")
        }
    }

    @Test
    fun `verifiserLesetilgang - har tilgang - kaster ingen feil`() {
        mockPoaoTilgangPermit()

        tilgangskontrollService.verifiserLesetilgang(UUID.randomUUID(), "12345")
    }

    @Test
    fun `verifiserLesetilgang - har ikke tilgang - kaster AuthorizationException`() {
        mockPoaoTilgangDeny()

        shouldThrow<AuthorizationException> {
            tilgangskontrollService.verifiserLesetilgang(UUID.randomUUID(), "12345")
        }
    }

    @Nested
    inner class KoordinatorTilgangTilDeltaker {
        @Test
        fun `koordinatorTilgangTilDeltaker - mangler tilgang - deltaker er kode 7 - tilgang er false`() {
            val navAnsattAzureId = UUID.randomUUID()
            mockPoaoTilgangDeny(NavAnsattBehandleFortroligBrukerePolicyInput(navAnsattAzureId))

            val tilgangTilDeltaker = tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                navAnsattAzureId = navAnsattAzureId,
                adressebeskyttelse = Adressebeskyttelse.FORTROLIG,
                erSkjermet = false,
            )
            tilgangTilDeltaker shouldBe false
        }

        @Test
        fun `koordinatorTilgangTilDeltaker - mangler tilgang - deltaker er kode 6 - tilgang er false`() {
            val navAnsattAzureId = UUID.randomUUID()
            mockPoaoTilgangDeny(NavAnsattBehandleStrengtFortroligBrukerePolicyInput(navAnsattAzureId))

            val tilgangTilDeltaker = tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                navAnsattAzureId,
                adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG,
                erSkjermet = false,
            )
            tilgangTilDeltaker shouldBe false
        }

        @Test
        fun `koordinatorTilgangTilDeltaker - har tilgang - deltaker er kode 7 - tilgang er true`() {
            val navAnsattAzureId = UUID.randomUUID()
            mockPoaoTilgangPermit(NavAnsattBehandleFortroligBrukerePolicyInput(navAnsattAzureId))
            val tilgangTilDeltaker = tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                navAnsattAzureId,
                adressebeskyttelse = Adressebeskyttelse.FORTROLIG,
                erSkjermet = false,
            )
            tilgangTilDeltaker shouldBe true
        }

        @Test
        fun `koordinatorTilgangTilDeltaker - har tilgang - deltaker er kode 6 - tilgang er true`() {
            val navAnsattAzureId = UUID.randomUUID()
            mockPoaoTilgangPermit(NavAnsattBehandleStrengtFortroligBrukerePolicyInput(navAnsattAzureId))

            val tilgangTilDeltaker = tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                navAnsattAzureId,
                adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG,
                erSkjermet = false,
            )
            tilgangTilDeltaker shouldBe true
        }

        @Test
        fun `koordinatorTilgangTilDeltaker - deltaker er skjermet, ansatt har tilgang - tilgang er true`() {
            val navAnsattAzureId = UUID.randomUUID()

            mockPoaoTilgangPermit(NavAnsattBehandleSkjermedePersonerPolicyInput(navAnsattAzureId))
            val tilgangTilDeltaker = tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                navAnsattAzureId,
                adressebeskyttelse = null,
                erSkjermet = true,
            )
            tilgangTilDeltaker shouldBe true
        }

        @Test
        fun `koordinatorTilgangTilDeltaker - deltaker er skjermet, har ikke tilgang - tilgang er false`() {
            val navAnsattAzureId = UUID.randomUUID()
            mockPoaoTilgangDeny(NavAnsattBehandleSkjermedePersonerPolicyInput(navAnsattAzureId))
            val tilgangTilDeltaker = tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                navAnsattAzureId,
                adressebeskyttelse = null,
                erSkjermet = true,
            )
            tilgangTilDeltaker shouldBe false
        }

        @Test
        fun `koordinatorTilgangTilDeltaker - deltaker er ikke adressebeskyttet eller skjermet - tilgang er true`() {
            val navAnsattAzureId = UUID.randomUUID()

            val tilgangTilDeltaker = tiltakskoordinatorTilgangskontrollService.harTilgangTilPersonMedRestriksjoner(
                navAnsattAzureId,
                adressebeskyttelse = null,
                erSkjermet = false,
            )
            tilgangTilDeltaker shouldBe true
        }
    }

    private fun mockPoaoTilgangDeny(policyInput: PolicyInput? = null) {
        every { poaoTilgangCachedClient.evaluatePolicy(policyInput ?: any()) } returns ApiResult(null, Decision.Deny("Ikke tilgang", ""))
    }

    private fun mockPoaoTilgangPermit(policyInput: PolicyInput? = null) {
        every { poaoTilgangCachedClient.evaluatePolicy(policyInput ?: any()) } returns ApiResult(null, Decision.Permit)
    }
}
