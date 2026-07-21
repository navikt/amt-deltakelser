package no.nav.amt.aktivitetskort.client

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpStatus

abstract class ClientTestBase {
    protected lateinit var server: MockWebServer
    protected lateinit var serverUrl: String

    protected val tokenProvider: () -> String = { "TOKEN" }

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        serverUrl = server.url("/").toString().removeSuffix("/")
    }

    @AfterEach
    fun shutdown() = server.shutdown()

    protected fun enqueueHttpStatus(httpStatus: HttpStatus) = server.enqueue(MockResponse().setResponseCode(httpStatus.value()))

    fun enqueueJson(json: String) = server.enqueue(
        MockResponse()
            .setResponseCode(HttpStatus.OK.value())
            .setBody(json),
    )
}
