package no.nav.amt.lib.ktor.auth

import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.Payload
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.ktor.server.auth.jwt.JWTCredential
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class ErMaskinTilMaskinTest {
    @Test
    fun `JWTCredential skal returnere true hvis sub og oid er like`() {
        val jwtCredential = createJWTCredential(
            sub = "12345",
            oid = "12345",
        )

        jwtCredential.erMaskinTilMaskin().shouldBeTrue()
    }

    @Test
    fun `JWTCredential skal returnere false hvis sub og oid er ulike`() {
        val jwtCredential = createJWTCredential(
            sub = "12345",
            oid = "67890",
        )

        jwtCredential.erMaskinTilMaskin().shouldBeFalse()
    }

    @Test
    fun `JWTCredential skal returnere false hvis sub mangler`() {
        val jwtCredential = createJWTCredential(
            sub = null,
            oid = "67890",
        )

        jwtCredential.erMaskinTilMaskin().shouldBeFalse()
    }

    @Test
    fun `JWTCredential skal returnere false hvis oid mangler`() {
        val jwtCredential = createJWTCredential(
            sub = "12345",
            oid = null,
        )

        jwtCredential.erMaskinTilMaskin().shouldBeFalse()
    }

    companion object {
        private fun createJWTCredential(
            sub: String?,
            oid: String?,
        ): JWTCredential {
            val payload = mockk<Payload>()
            val subClaim = mockk<Claim>()
            val oidClaim = mockk<Claim>()

            every { subClaim.asString() } returns sub
            every { oidClaim.asString() } returns oid
            every { payload.getClaim(SUB_CLAIM) } returns subClaim
            every { payload.getClaim(OID_CLAIM) } returns oidClaim

            return JWTCredential(payload)
        }
    }
}
