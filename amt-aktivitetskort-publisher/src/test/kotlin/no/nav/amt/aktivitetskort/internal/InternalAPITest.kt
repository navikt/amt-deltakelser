package no.nav.amt.aktivitetskort.internal

import io.kotest.matchers.shouldBe
import no.nav.amt.aktivitetskort.IntegrationTest
import no.nav.amt.aktivitetskort.kafka.producer.AktivitetskortProducer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.util.UUID

class InternalAPITest : IntegrationTest() {
    @MockitoSpyBean
    private lateinit var aktivitetskortProducer: AktivitetskortProducer

    // serverUrl() uses "localhost" which may resolve to ::1 on macOS — force IPv4 so remoteAddr == "127.0.0.1"
    private fun postInternal(
        path: String,
        body: String,
    ) = OkHttpClient()
        .newCall(
            Request
                .Builder()
                .url(serverUrl().replace("localhost", "127.0.0.1") + path)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build(),
        ).execute()

    @Test
    fun `slettAktivitetskort - kall fra intern ip - kaller producer med riktige parametre`() {
        val aktivitetskortId = UUID.randomUUID()
        val body = """{"aktivitetskortId": "$aktivitetskortId", "personIdent": "12345678901", "navIdent": "Z123456"}"""

        postInternal("/internal/slett/", body).use { it.code shouldBe 200 }

        verify(aktivitetskortProducer).slettAktivitetskort(aktivitetskortId, "12345678901", "Z123456")
    }

    @Test
    fun `slettAktivitetskort - manglende felt i body - returnerer 400`() {
        postInternal(
            "/internal/slett/",
            """{ "aktivitetskortId": "${UUID.randomUUID()}" }""",
        ).use { it.code shouldBe 400 }
    }
}
