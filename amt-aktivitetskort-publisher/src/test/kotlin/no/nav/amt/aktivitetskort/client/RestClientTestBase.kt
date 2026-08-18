package no.nav.amt.aktivitetskort.client

import no.nav.amt.aktivitetskort.config.ClientConfig
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer

@TestPropertySource(
    properties = [
        "spring.http.serviceclient.aktivitet-arena-acl.base-url=http://aktivitet-arena-acl",
        "spring.http.serviceclient.amt-arena-acl.base-url=http://amt-arena-acl",
        "spring.http.serviceclient.amt-arrangor.base-url=http://amt-arrangor",
        "spring.http.serviceclient.veilarboppfolging.base-url=http://veilarboppfolging",
        "spring.test.restclient.mockrestserviceserver.enabled=false",
    ],
)
@Import(OAuth2ClientTestConfig::class, ClientConfig::class)
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
