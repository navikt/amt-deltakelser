package no.nav.amt.aktivitetskort.client

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.client.MockRestServiceServer

@Import(ClientTestConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
abstract class RestClientTestBase {
    @Autowired
    lateinit var server: MockRestServiceServer

    companion object {
        const val TOKEN_IN_TEST = "test-token"
    }
}
