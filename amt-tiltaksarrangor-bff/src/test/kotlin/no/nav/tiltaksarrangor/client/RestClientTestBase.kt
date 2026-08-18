package no.nav.tiltaksarrangor.client

import no.nav.tiltaksarrangor.config.ClientConfig
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer

@Import(OAuth2ClientTestConfig::class, ClientConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@TestPropertySource(
    properties = [
        "spring.http.serviceclient.amt-arrangor-tokenx.base-url=http://amt-arrangor-tokenx",
        "spring.http.serviceclient.amt-arrangor-aad.base-url=http://amt-arrangor-aad",
        "spring.http.serviceclient.amt-person-service.base-url=http://amt-person-service",
        "spring.test.restclient.mockrestserviceserver.enabled=false",
    ],
)
abstract class RestClientTestBase(
    private val group: String,
) {
    @Autowired
    private lateinit var testConfig: OAuth2ClientTestConfig

    lateinit var server: MockRestServiceServer

    @BeforeEach
    fun resetServer() {
        server = testConfig.getMock(group)
        server.reset()
    }
}
