package no.nav.amt.aktivitetskort.client

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer

@TestPropertySource(
    properties = [
        "spring.http.serviceclient.amt-arena-acl.base-url=",
        "spring.http.serviceclient.amt-arrangor.base-url=",
        "spring.http.serviceclient.aktivitet-arena-acl.base-url=",
        "spring.http.serviceclient.veilarboppfolging.base-url=",
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
