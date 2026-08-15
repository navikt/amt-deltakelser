package no.nav.tiltaksarrangor.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class JwtExtensionsTest {
    @Test
    fun `skal hente personident fra pid claim`() {
        val jwtWithPid = Jwt(
            "test-token",
            Instant.now().minusSeconds(10),
            Instant.now().plusSeconds(60),
            mapOf("alg" to "none"),
            mapOf("pid" to "12345678901"),
        )

        jwtWithPid.personIdent() shouldBe "12345678901"
    }

    @Test
    fun `skal feile uten pid claim`() {
        val jwtWithoutPid = Jwt(
            "test-token",
            Instant.now().minusSeconds(10),
            Instant.now().plusSeconds(60),
            mapOf("alg" to "none"),
            mapOf("foo" to "bar"),
        )

        shouldThrow<ResponseStatusException> {
            jwtWithoutPid.personIdent()
        }.statusCode shouldBe HttpStatus.UNAUTHORIZED
    }
}
