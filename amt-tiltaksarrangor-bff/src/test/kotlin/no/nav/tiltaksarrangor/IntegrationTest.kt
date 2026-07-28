package no.nav.tiltaksarrangor

import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import no.nav.amt.lib.kafka.Producer
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import no.nav.tiltaksarrangor.client.amtarrangor.AmtArrangorClient
import no.nav.tiltaksarrangor.client.amtarrangor.HentArrangorClient
import no.nav.tiltaksarrangor.client.amtperson.AmtPersonClient
import no.nav.tiltaksarrangor.unleash.UnleashTestConfiguration
import no.nav.tiltaksarrangor.utils.Issuer
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@EnableMockOAuth2Server
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(UnleashTestConfiguration::class)
abstract class IntegrationTest : RepositoryTestBase() {
    @Autowired
    protected lateinit var mockOAuth2Server: MockOAuth2Server

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @MockkBean
    protected lateinit var amtArrangorClient: AmtArrangorClient

    @MockkBean
    protected lateinit var hentArrangorClient: HentArrangorClient

    @MockkBean
    protected lateinit var amtPersonClient: AmtPersonClient

    @MockkBean(relaxed = true)
    protected lateinit var producer: Producer<String, String>

    @LocalServerPort
    private var localServerPort: Int = 0

    @AfterEach
    fun cleanup() {
        clearMocks(amtArrangorClient, hentArrangorClient, amtPersonClient, producer)
    }

    fun getTokenxToken(
        fnr: String,
        audience: String = "amt-tiltaksarrangor-bff-client-id",
        issuerId: String = Issuer.TOKEN_X,
        clientId: String = "amt-tiltaksarrangor-flate",
        claims: Map<String, Any> = mapOf(
            "acr" to "Level4",
            "idp" to "idporten",
            "client_id" to clientId,
            "pid" to fnr,
        ),
    ): String = mockOAuth2Server
        .issueToken(
            issuerId,
            clientId,
            DefaultOAuth2TokenCallback(
                issuerId = issuerId,
                subject = UUID.randomUUID().toString(),
                audience = listOf(audience),
                claims = claims,
                expiry = 3600,
            ),
        ).serialize()
}
