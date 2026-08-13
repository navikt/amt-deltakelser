package no.nav.amt.aktivitetskort.internal

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.web.access.intercept.RequestAuthorizationContext

class InternalAuthorizationManagerTest {
    private val authorizationManager = InternalAuthorizationManager()

    @Test
    fun `authorize - loopback adresse - returnerer true`() {
        val context = RequestAuthorizationContext(
            MockHttpServletRequest().apply {
                remoteAddr = "127.0.0.1"
            },
        )

        authorizationManager
            .authorize({
                TestingAuthenticationToken("any", null)
            }, context)
            .isGranted shouldBe true
    }

    @Test
    fun `authorize - ekstern adresse - returnerer false`() {
        val context = RequestAuthorizationContext(
            MockHttpServletRequest().apply {
                remoteAddr = "10.0.0.1"
            },
        )

        authorizationManager
            .authorize({
                TestingAuthenticationToken("any", null)
            }, context)
            .isGranted shouldBe false
    }
}
