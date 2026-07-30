package no.nav.tiltaksarrangor.client

import no.nav.tiltaksarrangor.config.ClientConfig
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer

@Import(ClientTestConfig::class, ClientConfig::class)
@TestPropertySource(
    properties = [
        "spring.http.serviceclient.amt-arrangor-tokenx.base-url=",
        "spring.http.serviceclient.amt-arrangor-aad.base-url=",
        "spring.http.serviceclient.amt-person-aad.base-url=",
        "spring.test.restclient.mockrestserviceserver.enabled=false",
    ],
)
abstract class RestClientTestBase(
    private val group: String,
) {
    @Autowired
    private lateinit var testConfig: ClientTestConfig

    lateinit var server: MockRestServiceServer

    @BeforeEach
    fun resetServer() {
        server = testConfig.getMock(group)
        server.reset()
    }
}
