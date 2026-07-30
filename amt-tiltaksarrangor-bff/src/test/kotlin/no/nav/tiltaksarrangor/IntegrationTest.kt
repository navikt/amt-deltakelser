package no.nav.tiltaksarrangor

import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import no.nav.amt.lib.kafka.Producer
import no.nav.tiltaksarrangor.client.amtarrangor.AmtArrangorClient
import no.nav.tiltaksarrangor.client.amtarrangor.HentArrangorClient
import no.nav.tiltaksarrangor.client.amtperson.AmtPersonClient
import no.nav.tiltaksarrangor.unleash.UnleashTestConfiguration
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(UnleashTestConfiguration::class, TestJwtConfig::class)
abstract class IntegrationTest : RepositoryTestBase() {
    @Autowired
    protected lateinit var jwtEncoder: JwtEncoder

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
        clientId: String = "amt-tiltaksarrangor-flate",
    ): String {
        val claims = JwtClaimsSet
            .builder()
            .issuer("http://localhost:9999/tokenx")
            .subject(UUID.randomUUID().toString())
            .audience(listOf(audience))
            .claim("acr", "Level4")
            .claim("idp", "idporten")
            .claim("client_id", clientId)
            .claim("pid", fnr)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }
}
