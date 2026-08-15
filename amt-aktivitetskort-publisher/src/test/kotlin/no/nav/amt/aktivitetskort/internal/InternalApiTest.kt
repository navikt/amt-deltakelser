package no.nav.amt.aktivitetskort.internal

import com.ninjasquad.springmockk.MockkBean
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import no.nav.amt.aktivitetskort.IntegrationTestBase
import no.nav.amt.aktivitetskort.kafka.producer.AktivitetskortProducer
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

@AutoConfigureMockMvc
class InternalApiTest(
    private val mockMvc: MockMvc,
    @MockkBean private val aktivitetskortProducer: AktivitetskortProducer,
) : IntegrationTestBase() {
    @Test
    fun `slettAktivitetskort - kall fra intern ip - kaller producer med riktige parametre`() {
        val aktivitetskortId = UUID.randomUUID()
        val body = """{"aktivitetskortId": "$aktivitetskortId", "personIdent": "12345678901", "navIdent": "Z123456"}"""

        every {
            aktivitetskortProducer.slettAktivitetskort(any(), any(), any())
        } just Runs

        mockMvc
            .post("/internal/slett/") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isOk() } }

        verify { aktivitetskortProducer.slettAktivitetskort(aktivitetskortId, "12345678901", "Z123456") }
    }

    @Test
    fun `slettAktivitetskort - manglende felt i body - returnerer 400`() {
        mockMvc
            .post("/internal/slett/") {
                contentType = MediaType.APPLICATION_JSON
                content = """{ "aktivitetskortId": "${UUID.randomUUID()}" }"""
            }.andExpect { status { isBadRequest() } }
    }
}
