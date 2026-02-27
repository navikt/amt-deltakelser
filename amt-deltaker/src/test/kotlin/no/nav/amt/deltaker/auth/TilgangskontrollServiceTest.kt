package no.nav.amt.deltaker.auth

import io.mockk.every
import io.mockk.mockk
import no.nav.amt.lib.ktor.auth.exceptions.AuthorizationException
import no.nav.poao_tilgang.client.Decision
import no.nav.poao_tilgang.client.PoaoTilgangCachedClient
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

class TilgangskontrollServiceTest {
    private val poaoTilgangCachedClient = mockk<PoaoTilgangCachedClient>()
    private val tilgangskontrollService = TilgangskontrollService(poaoTilgangCachedClient)

    @Test
    fun `verifiserLesetilgang - har tilgang - kaster ingen feil`() {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Permit)

        tilgangskontrollService.verifiserLesetilgang(UUID.randomUUID(), "12345")
    }

    @Test
    fun `verifiserLesetilgang - har ikke tilgang - kaster AuthorizationException`() {
        every { poaoTilgangCachedClient.evaluatePolicy(any()) } returns ApiResult(null, Decision.Deny("Ikke tilgang", ""))

        assertFailsWith<AuthorizationException> {
            tilgangskontrollService.verifiserLesetilgang(UUID.randomUUID(), "12345")
        }
    }
}
